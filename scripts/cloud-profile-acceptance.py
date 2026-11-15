#!/usr/bin/env python3
"""Disposable-emulator managed-profile acceptance; no personal device or app data changes.

Requires an already installed, isolated acceptance app/test pair. Creates actual managed/private
profiles containing only the synthetic acceptance app. Production enumeration runs under the
target UID with no shell permissions; native setup alone temporarily requests quiet mode.
API 29–34 opts into temporarily selecting/foregrounding the acceptance HOME, with native
finally restoration of the previously resolved HOME. Interrupted/unconfirmed native runs
quarantine the disposable AVD until it is destroyed and recreated. Later APIs use scoped setup identity.
This is not DPC provisioning, a real Private Space UI test, or OEM compatibility evidence.
Default transport requires the dedicated acceptance prefix. An explicitly owner-authorized named
disposable Chikku AVD may instead be pinned with --expected-avd; every other safety gate remains.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import datetime as dt
import fcntl
import importlib.util
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
ID_PATTERN = re.compile(r"io\.github\.aj4abinjacob\.chikku\.acceptance\.[a-z][a-z0-9_]{0,47}\Z")
SERIAL_PATTERN = re.compile(r"emulator-[0-9]+\Z")
TEST_CLASS = "io.github.aj4abinjacob.chikku.platform.ManagedProfileAcceptanceDeviceTest"
TEST_METHOD = "freshUnprivilegedEnumerationReflectsSyntheticProfileStage"
STAGES = {"work-active", "work-paused", "work-resumed", "work-removed", "private-running", "private-stopped"}
REMOVAL_TIMEOUT_SECONDS = 60.0
REMOVAL_POLL_SECONDS = 0.5
CREATION_RECONCILE_TIMEOUT_SECONDS = 60.0
# Device-side, so a killed host, another host, or a different serial cannot lose the guard.
# Never remove this marker after an uncertain native outcome: force-stop cannot run finally.
spec = importlib.util.spec_from_file_location("profile_native_parser", ROOT / "scripts/run-reliability.py")
assert spec and spec.loader
native_parser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native_parser)
QUARANTINE_PATH = native_parser.PROFILE_QUARANTINE_PATH
QUARANTINE_CHECK = native_parser.PROFILE_QUARANTINE_CHECK


class AcceptanceError(RuntimeError):
    """Fixed diagnostic code only: never include platform inventories or profile details."""


def validate_arguments(serial: str, package: str) -> None:
    if not SERIAL_PATTERN.fullmatch(serial):
        raise AcceptanceError("EXPLICIT_EMULATOR_SERIAL_REQUIRED")
    if not ID_PATTERN.fullmatch(package):
        raise AcceptanceError("ISOLATED_ACCEPTANCE_ID_REQUIRED")


def parse_user_rows(output: str) -> dict[int, str]:
    """Names stay in memory only, solely for exact synthetic creation-nonce reconciliation."""
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines or lines[0] != "Users:":
        raise AcceptanceError("UNRECOGNIZED_USER_LIST")
    users: dict[int, str] = {}
    for line in lines[1:]:
        match = re.fullmatch(r"UserInfo\{([0-9]+):([^\r\n]*):[0-9a-fA-F]+\}(?: running)?", line)
        if not match or int(match[1]) in users:
            raise AcceptanceError("UNRECOGNIZED_USER_LIST")
        users[int(match[1])] = match[2]
    if not users:
        raise AcceptanceError("EMPTY_USER_LIST")
    return users


def parse_user_ids(output: str) -> set[int]:
    """Return only IDs; never retain or display raw shell profile rows."""
    return set(parse_user_rows(output))


def parse_created_user(output: str) -> int:
    match = re.fullmatch(r"Success: created user id ([0-9]+)\s*", output)
    if not match or not 1 <= int(match[1]) <= 9999:
        raise AcceptanceError("PROFILE_CREATION_UNCONFIRMED")
    return int(match[1])


def confirmed_native_pass(output: str) -> dict:
    parsed = native_parser.parse_instrumentation_output(output)
    counts = parsed["counts"]
    result = {"status": parsed["status"], "counts": counts,
              "instrumentation_code": parsed["instrumentation_code"]}
    expected = {"passed": 1, "failed": 0, "ignored": 0, "assumption_skipped": 0}
    tests = parsed["tests"]
    if (parsed["status"] != "PASS" or counts != expected or parsed["announced_tests"] != 1
            or len(tests) != 1 or tests[0]["class"] != TEST_CLASS or tests[0]["test"] != TEST_METHOD):
        result["status"] = "FAIL"
    # Deliberately omit raw output, stacks, test streams and private profile metadata.
    return result


@contextmanager
def device_lock(serial: str):
    path = Path("/tmp") / f"chikku-acceptance-{serial}.lock"
    with path.open("a", encoding="utf-8") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise AcceptanceError("OTHER_ACCEPTANCE_WORKFLOW_RUNNING") from exc
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


class Client:
    def __init__(self, serial: str, package: str, adb: str = "adb", expected_avd: str | None = None):
        validate_arguments(serial, package)
        if expected_avd is not None and not re.fullmatch(r"chikku_[a-z0-9_]{1,80}", expected_avd):
            raise AcceptanceError("EXPLICIT_CHIKKU_AVD_NAME_REQUIRED")
        self.serial, self.package, self.adb = serial, package, adb
        self.expected_avd = expected_avd
        self.owned: set[int] = set()
        self.removed: set[int] = set()
        self.pending_creation_names: set[str] = set()
        self.preflight_ids: frozenset[int] | None = None
        self.api: int | None = None
        self.guard_owned = False
        self.guard_uncertain = False
        self.native_uncertain = False

    def command(self, *args: str, timeout: int = 60) -> str:
        try:
            process = subprocess.run([self.adb, "-s", self.serial, *args], text=True,
                                     capture_output=True, timeout=timeout, check=False)
        except FileNotFoundError as exc:
            raise AcceptanceError("ADB_UNAVAILABLE") from exc
        except subprocess.TimeoutExpired as exc:
            raise AcceptanceError("ADB_COMMAND_TIMEOUT") from exc
        if process.returncode:
            raise AcceptanceError("ADB_COMMAND_FAILED")
        return process.stdout.replace("\r", "").strip()

    def preflight(self) -> int:
        self.api = None  # A failed repeat preflight cannot retain mutation authorization.
        if self.command("get-state") != "device":
            raise AcceptanceError("EMULATOR_OFFLINE")
        if self.command("shell", "getprop", "ro.kernel.qemu") != "1":
            raise AcceptanceError("EMULATOR_REQUIRED")
        if self.command("shell", QUARANTINE_CHECK) != "CLEAN":
            raise AcceptanceError("UNSAFE_AVD_RECREATE_REQUIRED")
        avd = self.command("emu", "avd", "name").splitlines()
        if (len(avd) != 2 or avd[1] != "OK" or
                (avd[0] != self.expected_avd if self.expected_avd is not None
                 else not avd[0].startswith("chikku_acceptance_"))):
            raise AcceptanceError("DEDICATED_ACCEPTANCE_AVD_REQUIRED")
        if self.command("shell", "am", "get-current-user") != "0":
            raise AcceptanceError("PERSONAL_USER_ZERO_REQUIRED")
        if parse_user_ids(self.command("shell", "pm", "list", "users")) != {0}:
            raise AcceptanceError("FRESH_AVD_WITHOUT_EXISTING_PROFILES_REQUIRED")
        api = self.command("shell", "getprop", "ro.build.version.sdk")
        if not api.isdigit() or int(api) < 29:
            raise AcceptanceError("SUPPORTED_API_REQUIRED")
        for package in (self.package, f"{self.package}.test"):
            path = self.command("shell", "pm", "path", "--user", "0", package)
            if not path.startswith("package:"):
                raise AcceptanceError("INSTALLED_ACCEPTANCE_APP_TEST_PAIR_REQUIRED")
        self.api = int(api)
        self.preflight_ids = frozenset({0})
        return self.api

    def arm_guard(self) -> None:
        if self.native_uncertain or self.guard_uncertain:
            raise AcceptanceError("UNSAFE_AVD_RECREATE_REQUIRED")
        if self.guard_owned:
            return
        # Atomic mkdir rejects another owner. Register before any profile or HOME mutation;
        # losing the response leaves the marker in place and a later preflight fails closed.
        self.guard_uncertain = True
        if self.command("shell", f"mkdir {QUARANTINE_PATH} && echo ARMED") != "ARMED":
            raise AcceptanceError("AVD_QUARANTINE_GUARD_UNCONFIRMED")
        self.guard_owned = True
        self.guard_uncertain = False

    def create(self, kind: str) -> int:
        if self.api is None or self.preflight_ids is None:
            raise AcceptanceError("SUCCESSFUL_PREFLIGHT_REQUIRED")
        if kind not in {"managed", "private"}:
            raise AcceptanceError("UNKNOWN_SYNTHETIC_PROFILE_KIND")
        self.arm_guard()
        # --managed works at minSdk 29; --user-type was introduced later.
        flags = ["--managed"] if kind == "managed" else ["--user-type", "android.os.usertype.profile.PRIVATE"]
        name = f"chikku_acceptance_{uuid.uuid4().hex}"
        self.pending_creation_names.add(name)  # Register uncertainty before a remote commit can occur.
        try:
            output = self.command("shell", "pm", "create-user", "--profileOf", "0", *flags, name)
            profile = parse_created_user(output)
        except AcceptanceError:
            # A timed-out response does not imply Android rolled back creation. Only this
            # invocation's exact nonce can positively identify a newly created profile.
            try:
                self.reconcile_creation(name)
            except AcceptanceError:
                pass  # Unresolved uncertainty remains registered and makes final cleanup fail.
            raise
        self.owned.add(profile)  # Register ownership before any later setup can fail.
        self.pending_creation_names.remove(name)
        self.command("shell", "pm", "install-existing", "--user", str(profile), self.package)
        self.command("shell", "settings", "--user", str(profile), "put", "secure", "user_setup_complete", "1")
        started = self.command("shell", "am", "start-user", "-w", str(profile))
        if "Success: user started" not in started.splitlines():
            raise AcceptanceError("PROFILE_START_UNCONFIRMED")
        if self.command("shell", "am", "get-started-user-state", str(profile)) != "RUNNING_UNLOCKED":
            raise AcceptanceError("PROFILE_UNLOCK_UNCONFIRMED")
        return profile

    def reconcile_creation(self, name: str) -> None:
        if name not in self.pending_creation_names or self.preflight_ids is None:
            raise AcceptanceError("REGISTERED_CREATION_NONCE_REQUIRED")
        deadline = time.monotonic() + CREATION_RECONCILE_TIMEOUT_SECONDS
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise AcceptanceError("PROFILE_CREATION_OWNERSHIP_UNCONFIRMED")
            users = parse_user_rows(self.command("shell", "pm", "list", "users",
                                                timeout=max(1, math.ceil(remaining))))
            if 0 not in users:
                raise AcceptanceError("PRIMARY_USER_LIST_CONFIRMATION_REQUIRED")
            matches = [profile for profile, actual_name in users.items() if actual_name == name]
            if len(matches) > 1:
                raise AcceptanceError("PROFILE_CREATION_OWNERSHIP_AMBIGUOUS")
            if matches:
                profile = matches[0]
                if profile in self.preflight_ids or profile in self.owned or not 1 <= profile <= 9999:
                    raise AcceptanceError("PROFILE_CREATION_OWNERSHIP_UNCONFIRMED")
                self.owned.add(profile)
                self.pending_creation_names.remove(name)
                return
            time.sleep(min(REMOVAL_POLL_SECONDS, max(0.0, deadline - time.monotonic())))

    def stop(self, profile: int) -> None:
        if profile not in self.owned or profile == 0:
            raise AcceptanceError("UNOWNED_PROFILE_STOP_REFUSED")
        self.command("shell", "am", "stop-user", "-f", "-w", str(profile))
        if self.command("shell", "am", "is-user-stopped", str(profile)) != "true":
            raise AcceptanceError("PROFILE_STOP_UNCONFIRMED")

    def stage(self, stage: str, profile: int) -> dict:
        if self.api is None:
            raise AcceptanceError("SUCCESSFUL_PREFLIGHT_REQUIRED")
        if stage not in STAGES or not 1 <= profile <= 9999:
            raise AcceptanceError("VALID_SYNTHETIC_PROFILE_STAGE_REQUIRED")
        if stage != "work-removed" and profile not in self.owned:
            raise AcceptanceError("OWNED_PROFILE_STAGE_REQUIRED")
        if stage == "work-removed" and profile not in self.removed:
            raise AcceptanceError("OWN_REMOVAL_CONFIRMATION_REQUIRED")
        arguments = ["shell", "am", "instrument", "--user", "0", "-w", "-r",
                     "-e", "class", f"{TEST_CLASS}#{TEST_METHOD}",
                     "-e", "cloudProfileFixture", "true",
                     "-e", "cloudProfileStage", stage,
                     "-e", "cloudProfileUser", str(profile)]
        if 29 <= self.api < 35:
            arguments.extend(["-e", "cloudProfileLauncherSetup", "true"])
        arguments.append(f"{self.package}.test/androidx.test.runner.AndroidJUnitRunner")
        self.arm_guard()
        # Mark before launching: killing adb does not establish remote quiescence. Even a
        # terminal failure can mean restoration failed. Only the complete successful native
        # assertion (including its finally restoration checks) permits cleanup/reuse.
        self.native_uncertain = True
        output = self.command(*arguments, timeout=120)
        result = {"stage": stage, **confirmed_native_pass(output)}
        if result["status"] == "PASS":
            self.native_uncertain = False
        return result

    def remove(self, profile: int) -> None:
        if profile not in self.owned or profile == 0:
            raise AcceptanceError("UNOWNED_PROFILE_REMOVAL_REFUSED")
        output = self.command("shell", "pm", "remove-user", str(profile))
        if not output.startswith("Success:"):
            raise AcceptanceError("PROFILE_REMOVAL_UNCONFIRMED")
        # Android may acknowledge removal before its stop/cleanup callback completes. Retain
        # ownership until a fresh public shell user list confirms the owned ID is absent.
        deadline = time.monotonic() + REMOVAL_TIMEOUT_SECONDS
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise AcceptanceError("PROFILE_REMOVAL_ABSENCE_UNCONFIRMED")
            ids = parse_user_ids(self.command("shell", "pm", "list", "users",
                                              timeout=max(1, math.ceil(remaining))))
            if 0 not in ids:
                raise AcceptanceError("PRIMARY_USER_LIST_CONFIRMATION_REQUIRED")
            if profile not in ids:
                self.owned.remove(profile)
                self.removed.add(profile)
                return
            time.sleep(min(REMOVAL_POLL_SECONDS, max(0.0, deadline - time.monotonic())))

    def cleanup(self) -> bool:
        if self.native_uncertain or self.guard_uncertain:
            # Do not race a possibly live instrumentation or pretend removal restores HOME /
            # onboarding. Leave both the guard and owned profiles for discard/diagnosis.
            return False
        success = True
        for name in tuple(self.pending_creation_names):
            try:
                self.reconcile_creation(name)
            except AcceptanceError:
                success = False
        for profile in tuple(self.owned):
            try:
                self.remove(profile)
            except AcceptanceError:
                success = False
        try:
            ids = parse_user_ids(self.command("shell", "pm", "list", "users"))
            if self.preflight_ids is None or ids != self.preflight_ids:
                success = False
        except AcceptanceError:
            success = False
        success = success and not self.pending_creation_names and not self.owned
        if success and self.guard_owned:
            try:
                if self.command("shell", f"rmdir {QUARANTINE_PATH} && echo RELEASED") != "RELEASED":
                    return False
                self.guard_owned = False
                if self.command("shell", QUARANTINE_CHECK) != "CLEAN":
                    return False
            except AcceptanceError:
                return False
        return success


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--expected-avd", help="Exact owner-authorized disposable Chikku AVD; otherwise the dedicated acceptance prefix is required")
    parser.add_argument("--output", type=Path, default=ROOT / "build/qa/cloud-profiles")
    args = parser.parse_args()
    result = {"status": "UNAVAILABLE", "stages": [], "scope": "synthetic-profile-native-enumeration",
              "private_space_support": "UNSUPPORTED", "cleanup": "NOT_REQUIRED"}
    try:
        client = Client(args.serial, args.package, args.adb, expected_avd=args.expected_avd)
        with device_lock(args.serial):
            result["api"] = client.preflight()
            result["application_id"] = args.package
            try:
                work = client.create("managed")
                for stage in ("work-active", "work-paused", "work-resumed"):
                    entry = client.stage(stage, work)
                    result["stages"].append(entry)
                    if entry["status"] != "PASS":
                        raise AcceptanceError("NATIVE_PROFILE_ASSERTION_FAILED")
                client.remove(work)
                entry = client.stage("work-removed", work)
                result["stages"].append(entry)
                if entry["status"] != "PASS":
                    raise AcceptanceError("NATIVE_PROFILE_ASSERTION_FAILED")
                if result["api"] >= 35:
                    private = client.create("private")
                    entry = client.stage("private-running", private)
                    result["stages"].append(entry)
                    if entry["status"] != "PASS":
                        raise AcceptanceError("NATIVE_PROFILE_ASSERTION_FAILED")
                    client.stop(private)
                    entry = client.stage("private-stopped", private)
                    result["stages"].append(entry)
                    if entry["status"] != "PASS":
                        raise AcceptanceError("NATIVE_PROFILE_ASSERTION_FAILED")
                else:
                    result["stages"].append({"stage": "private-profile", "status": "UNAVAILABLE", "reason": "API_BELOW_35"})
                result["status"] = "PASS"
            finally:
                result["cleanup"] = "PASS" if client.cleanup() else "FAIL"
                if result["cleanup"] == "FAIL":
                    result["status"] = "FAIL"
                    result["device_reuse"] = "UNSAFE_RECREATE_AVD_REQUIRED"
    except AcceptanceError as exc:
        result["reason"] = str(exc)
        if result["stages"] or result["cleanup"] != "NOT_REQUIRED":
            result["status"] = "FAIL"
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    directory = args.output / stamp
    directory.mkdir(parents=True, exist_ok=False)
    (directory / "summary.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())

