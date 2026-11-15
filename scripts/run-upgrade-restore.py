#!/usr/bin/env python3
"""Guarded historical development-APK upgrade and production-store restore on one disposable AVD.

Consumes already-built, same-key APKs. It never starts Gradle, creates an AVD, touches physical
devices, or downgrades an installation. Service restore, SAF and external widget evidence stay
separate. Failed installations/data are retained for diagnosis, never automatically cleared.
"""
from __future__ import annotations

import argparse
import datetime as dt
import fcntl
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
ID_PATTERN = re.compile(r"io\.github\.aj4abinjacob\.chikku\.acceptance\.[a-z][a-z0-9_]{0,47}")
SEED_CLASS = "io.github.aj4abinjacob.chikku.acceptance.HistoricalUpgradeSeedDeviceTest"
TEST_CLASS = "io.github.aj4abinjacob.chikku.acceptance.UpgradeRestoreAcceptanceDeviceTest"
RUNNER = "androidx.test.runner.AndroidJUnitRunner"
HISTORICAL_REVISION = "f69121e08e04c64d0c3541304484893877c9788f"
MAX_TEST_TIMEOUT = 300
MAX_BOOT_TIMEOUT = 600
_spec = importlib.util.spec_from_file_location("upgrade_reliability", ROOT / "scripts/run-reliability.py")
assert _spec is not None and _spec.loader is not None
_reliability = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_reliability)


class AcceptanceBlocked(RuntimeError):
    """A declared fixture assumption was unavailable; do not convert it into a pass or failure."""


def validate_id(value: str) -> str:
    if ID_PATTERN.fullmatch(value) is None:
        raise ValueError("An isolated io.github.aj4abinjacob.chikku.acceptance.<scenario> ID is required")
    return value


def command(args: list[str], timeout: float = 30) -> str:
    result = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout, check=False)
    if result.returncode != 0:
        # Do not echo arbitrary provider/package-manager output into public diagnostics.
        raise RuntimeError(f"Command failed ({result.returncode}): {shlex.join(args)}")
    return result.stdout.strip()


def parse_apk_badging(output: str, *, allow_unversioned_test: bool = False) -> dict:
    package = re.search(r"^package: name='([^']+)' versionCode='(\d*)' versionName='([^']*)'", output, re.M)
    if package is None:
        raise ValueError("Unable to parse APK package/version metadata")
    code = int(package[2]) if package[2] else None
    is_test_header = allow_unversioned_test and package[1].endswith(".test")
    if code is None and not is_test_header:
        raise ValueError("Application APK must declare an actual positive versionCode")
    if not is_test_header and (code is None or not 1 <= code <= 2_100_000_000):
        raise ValueError("Application APK versionCode is outside Android's supported positive range")
    return {"application_id": package[1], "version_code": code,
            "version_name": package[3] or (None if is_test_header else ""),
            "debuggable": bool(re.search(r"^application-debuggable(?:\s|$)", output, re.M))}


def parse_certificate(output: str) -> str:
    values = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", output, re.M)
    if len(values) != 1:
        raise ValueError("Exactly one verified signing certificate is required")
    return values[0].lower()


def apk_metadata(path: str, args: argparse.Namespace, *, test: bool = False) -> dict:
    file = Path(path).expanduser().resolve(strict=True)
    if not file.is_file():
        raise ValueError("APK must be a regular file")
    result = parse_apk_badging(command([args.aapt2, "dump", "badging", str(file)]), allow_unversioned_test=test)
    result.update({"path": str(file), "sha256": hashlib.sha256(file.read_bytes()).hexdigest(),
                   "size_bytes": file.stat().st_size,
                   "certificate_sha256": parse_certificate(command([args.apksigner, "verify", "--print-certs", str(file)]))})
    if test:
        manifest = command([args.aapt2, "dump", "xmltree", str(file), "--file", "AndroidManifest.xml"])
        targets = re.findall(r'android:targetPackage[^\n]*?="([^"]+)"', manifest)
        if targets != [args.application_id]:
            raise ValueError("Test APK must target exactly the isolated acceptance application")
        names = re.findall(r'android:name[^\n]*?="([^"]+)"', manifest)
        if RUNNER not in names:
            raise ValueError("Test APK does not declare the expected AndroidJUnitRunner")
    return result


def validate_apk_set(apks: dict, application_id: str) -> None:
    validate_id(application_id)
    for name, info in apks.items():
        expected = application_id + (".test" if name.endswith("test") else "")
        if info["application_id"] != expected:
            raise ValueError(f"{name} APK has an unexpected application ID")
        if not name.endswith("test") and info["debuggable"]:
            raise ValueError(f"{name} acceptance application must be nondebuggable")
        if not name.endswith("test"):
            code = info["version_code"]
            if not isinstance(code, int) or isinstance(code, bool) or not 1 <= code <= 2_100_000_000:
                raise ValueError(f"{name} application must declare an actual positive versionCode")
    if apks["new"]["version_code"] <= apks["old"]["version_code"]:
        raise ValueError("New APK versionCode must be strictly greater than old APK versionCode")
    if len({info["certificate_sha256"] for info in apks.values()}) != 1:
        raise ValueError("All application and instrumentation APKs must share the disposable signing key")


def adb(args: argparse.Namespace, *values: str, timeout: float = 30) -> str:
    return command([args.adb, "-s", args.serial, *values], timeout)


def require_clean_profile_device(args: argparse.Namespace) -> None:
    state = adb(args, "shell", _reliability.PROFILE_QUARANTINE_CHECK)
    if state != "CLEAN":
        raise RuntimeError("Unsafe profile acceptance AVD: destroy and recreate before reuse")


def verify_emulator(args: argparse.Namespace) -> dict:
    validate_id(args.application_id)
    if re.fullmatch(r"emulator-\d+", args.serial) is None:
        raise ValueError("Only emulator-N serials are allowed; physical devices are rejected")
    scenario = args.application_id.rsplit(".", 1)[1]
    if args.avd_name != f"chikku_acceptance_{scenario}":
        raise ValueError("AVD name must be chikku_acceptance_<the same application-ID scenario>")
    if adb(args, "get-state") != "device" or adb(args, "shell", "getprop", "ro.kernel.qemu") != "1":
        raise ValueError("Target is not a connected Android emulator")
    actual_avd = adb(args, "emu", "avd", "name").splitlines()
    if not actual_avd or actual_avd[0].strip() != args.avd_name:
        raise ValueError("Connected emulator AVD does not match the explicitly disposable scenario")
    if adb(args, "shell", "am", "get-current-user") != "0":
        raise ValueError("Acceptance requires primary user 0")
    require_fresh_system_user(adb(args, "shell", "pm", "list", "users"))
    if adb(args, "shell", "getprop", "ro.build.version.sdk") != str(args.api):
        raise ValueError("Connected emulator API differs from --api")
    for package in (args.application_id, args.application_id + ".test"):
        # Only the two synthetic scenario IDs are queried; never collect an app inventory.
        if scenario_package_exists(args, package):
            raise ValueError("Scenario package already exists; use a fresh disposable AVD/scenario")
    return {"serial": args.serial, "avd_name": args.avd_name, "api": args.api,
            "fingerprint": adb(args, "shell", "getprop", "ro.build.fingerprint"),
            "model": adb(args, "shell", "getprop", "ro.product.model"),
            "display_size": adb(args, "shell", "wm", "size"),
            "display_density": adb(args, "shell", "wm", "density"),
            "memory": adb(args, "shell", "cat", "/proc/meminfo").splitlines()[0]}


def require_fresh_system_user(output: str) -> None:
    # Parse only IDs in memory; profile/user names and types are never reported or retained.
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines or lines[0] != "Users:" or len(lines) != 2:
        raise ValueError("Upgrade scenario requires a fresh AVD with only system user 0")
    if re.fullmatch(r"UserInfo\{0:[^\r\n]*\}(?: running)?", lines[1]) is None:
        raise ValueError("Unable to verify sole system user 0")


def scenario_package_exists(args: argparse.Namespace, package: str) -> bool:
    if package not in (args.application_id, args.application_id + ".test"):
        raise ValueError("Package presence queries are limited to the isolated scenario")
    # -u also detects package registration/data retained by a previous uninstall -k. The
    # package filter limits this query to the synthetic acceptance prefix, never a real inventory.
    result = subprocess.run([args.adb, "-s", args.serial, "shell", "pm", "list", "packages", "-u", "--user", "0", package],
                            cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            timeout=60, check=False)
    output = result.stdout.strip()
    if result.returncode == 0 and output:
        lines = output.splitlines()
        if any(not re.fullmatch(r"package:io\.github\.aj4abinjacob\.chikku\.acceptance\.[a-z][a-z0-9_]{0,47}(?:\.test)?", line) for line in lines):
            raise RuntimeError("Unexpected output while checking isolated scenario registration")
        return f"package:{package}" in lines
    if result.returncode in (0, 1) and not output:
        return False
    raise RuntimeError("Unable to establish that isolated scenario package is absent")


def exact_test_passed(parsed: dict, test_class: str, method: str) -> bool:
    return (parsed.get("status") == "PASS" and parsed.get("announced_tests") == 1
            and parsed.get("accounted_tests") == 1
            and parsed.get("counts") == {"passed": 1, "failed": 0, "ignored": 0, "assumption_skipped": 0}
            and len(parsed.get("tests", [])) == 1
            and parsed["tests"][0].get("class") == test_class
            and parsed["tests"][0].get("test") == method
            and parsed["tests"][0].get("outcome") == "PASSED")


def exact_test_skipped(parsed: dict, test_class: str, method: str) -> bool:
    tests = parsed.get("tests", [])
    return (parsed.get("status") == "BLOCKED" and parsed.get("announced_tests") == 1
            and parsed.get("accounted_tests") == 1 and parsed.get("counts", {}).get("failed") == 0
            and len(tests) == 1 and tests[0].get("class") == test_class and tests[0].get("test") == method
            and tests[0].get("outcome") in ("ASSUMPTION_SKIPPED", "IGNORED"))


def parse_cut_marker(output: str, stage: int) -> int | None:
    values = re.findall(r"^INSTRUMENTATION_STATUS: chikkuCutMarker=([1-9][0-9]*):([0-3])\s*$", output, re.M)
    if len(values) == 1 and int(values[0][1]) == stage:
        return int(values[0][0])
    return None


def instrument_args(args: argparse.Namespace, metadata: dict, method: str, *, historical: bool = False,
                    stage: int | None = None, widget_id: int = 0) -> list[str]:
    test_class = SEED_CLASS if historical else TEST_CLASS
    result = [args.adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
              "-e", "class", f"{test_class}#{method}",
              "-e", "acceptanceApplicationId", args.application_id,
              "-e", "acceptanceDisposableEmulator", "true",
              "-e", "expectedVersionCode", str(metadata["version_code"]),
              "-e", "expectedSchema", "3" if historical else "4",
              "-e", "expectedWidgetId", str(widget_id)]
    if stage is not None:
        result.extend(["-e", "cutAfterStore", str(stage)])
    result.append(f"{args.application_id}.test/{RUNNER}")
    return result


def installed_version(args: argparse.Namespace) -> int:
    output = adb(args, "shell", "dumpsys", "package", args.application_id)
    found = re.search(r"\bversionCode=(\d+)\b", output)
    if found is None:
        raise RuntimeError("Installed scenario versionCode is unavailable")
    return int(found[1])


def terminate_client(process: subprocess.Popen) -> None:
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=3)


def run_instrumentation(values: list[str], path: Path, timeout: float) -> dict:
    started = time.monotonic()
    with path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(values, cwd=ROOT, text=True, stdout=log, stderr=subprocess.STDOUT)
        try:
            process.wait(timeout=timeout)
            timed_out = False
        except subprocess.TimeoutExpired:
            timed_out = True
            terminate_client(process)
    return {"command": shlex.join(values), "log": str(path), "exit_code": process.returncode,
            "timeout": timed_out, "duration_seconds": round(time.monotonic() - started, 3),
            "parsed": _reliability.parse_instrumentation_output(path.read_text(errors="replace"))}


class EvidenceRun:
    def __init__(self, args: argparse.Namespace, report: dict, directory: Path):
        self.args, self.report, self.directory = args, report, directory

    def record(self, name: str, status: str, **details) -> None:
        self.report["checks"].append({"name": name, "status": status, **details})
        (self.directory / "report.json").write_text(json.dumps(self.report, indent=2) + "\n")

    def mutate(self, name: str, *values: str, timeout: float = 30) -> str:
        started = time.monotonic()
        result = adb(self.args, *values, timeout=timeout)
        if values[0] in ("install", "uninstall") and "Success" not in result.splitlines():
            raise RuntimeError(f"Package operation did not report Success: {name}")
        if values[:4] == ("shell", "am", "start", "-W") and "Status: ok" not in result.splitlines():
            raise RuntimeError(f"HOME activity did not report Status: ok: {name}")
        self.record(name, "PASS", command=shlex.join([self.args.adb, "-s", self.args.serial, *values]),
                    duration_seconds=round(time.monotonic() - started, 3))
        return result

    def best_effort_force_stop(self, name: str) -> bool:
        values = [self.args.adb, "-s", self.args.serial, "shell", "am", "force-stop", self.args.application_id]
        try:
            adb(self.args, "shell", "am", "force-stop", self.args.application_id)
        except (OSError, RuntimeError, subprocess.TimeoutExpired) as exc:
            self.record(name, "FAIL", command=shlex.join(values), error=str(exc), remote_quiescence_confirmed=False)
            return False
        self.record(name, "PASS", command=shlex.join(values), remote_quiescence_confirmed=True)
        return True

    def test(self, metadata: dict, method: str, *, historical: bool = False, widget_id: int = 0,
             stage: int | None = None) -> None:
        args = instrument_args(self.args, metadata, method, historical=historical, widget_id=widget_id, stage=stage)
        path = self.directory / f"{len(self.report['checks']):02d}-{method}.log"
        result = run_instrumentation(args, path, self.args.test_timeout)
        test_class = SEED_CLASS if historical else TEST_CLASS
        if not result["timeout"] and result["exit_code"] == 0 and exact_test_skipped(result["parsed"], test_class, method):
            self.record(method, "SKIPPED", **result)
            raise AcceptanceBlocked(f"Required native fixture assumption was skipped: {method}")
        if result["timeout"] or result["exit_code"] != 0 or not exact_test_passed(result["parsed"], test_class, method):
            # Preserve the primary parsed evidence before cleanup: a slow/offline force-stop
            # must not erase the failed invocation or replace its cause with a cleanup error.
            self.record(method, "FAIL", **result)
            if result["timeout"]:
                self.best_effort_force_stop(f"{method}-cleanup-force-stop")
            raise RuntimeError(f"Native assertion did not complete exactly one passing test: {method}")
        self.record(method, "PASS", **result)

    def interrupted_restore(self, metadata: dict, stage: int) -> None:
        values = instrument_args(self.args, metadata, "pauseProductionRestoreForExternalKill", stage=stage)
        path = self.directory / f"cut-{stage}-prepare.log"
        deadline = time.monotonic() + self.args.test_timeout
        old_pid = None
        remote_cleanup_confirmed = True
        with path.open("w") as log:
            process = subprocess.Popen(values, cwd=ROOT, text=True, stdout=log, stderr=subprocess.STDOUT)
            try:
                while time.monotonic() < deadline and process.poll() is None:
                    old_pid = parse_cut_marker(path.read_text(errors="replace"), stage)
                    if old_pid is not None:
                        break
                    time.sleep(0.2)
                if old_pid is None:
                    raise RuntimeError(f"Stage {stage} did not publish its durable pause marker")
                self.mutate(f"cut-{stage}-force-stop", "shell", "am", "force-stop", self.args.application_id)
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    terminate_client(process)
                parsed = _reliability.parse_instrumentation_output(path.read_text(errors="replace"))
                if any(test.get("outcome") == "PASSED" for test in parsed.get("tests", [])):
                    raise RuntimeError("Interrupted preparation must not be counted as a passing test")
                self.record(f"cut-{stage}-preparation", "EXPECTED_INTERRUPTION", old_pid=old_pid,
                            command=shlex.join(values), log=str(path))
            except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as exc:
                self.record(f"cut-{stage}-preparation", "FAIL", old_pid=old_pid, command=shlex.join(values),
                            log=str(path), error=str(exc),
                            parsed=_reliability.parse_instrumentation_output(path.read_text(errors="replace")))
                raise
            finally:
                # No second invocation can overlap a still-running local adb client.
                try:
                    if process.poll() is None:
                        remote_cleanup_confirmed = self.best_effort_force_stop(f"cut-{stage}-cleanup-force-stop")
                finally:
                    # Even an offline/failed remote force-stop must release the local client.
                    # A failed remote cleanup remains explicit and requires orchestrator review.
                    terminate_client(process)
        if not remote_cleanup_confirmed:
            raise RuntimeError("Interrupted restore cleanup did not confirm remote quiescence; recovery was not started")
        self.test(metadata, "recoverProductionRestoreInNewProcess", stage=stage)


def validate_boot_id(value: str) -> str:
    try:
        normalized = str(uuid.UUID(value))
    except (ValueError, AttributeError):
        raise ValueError("Kernel boot ID must be a canonical UUID") from None
    if normalized != value:
        raise ValueError("Kernel boot ID must be a canonical UUID")
    return normalized


def wait_after_reboot(args: argparse.Namespace, previous_boot_id: str) -> str:
    validate_boot_id(previous_boot_id)
    deadline = time.monotonic() + args.boot_timeout
    # Each adb call and sleep is bounded; no indefinite wait-for-device command.
    def poll(*values: str) -> str:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError("Boot deadline exhausted")
        return adb(args, *values, timeout=min(5.0, remaining))

    while time.monotonic() < deadline:
        try:
            current_boot_id = validate_boot_id(poll("shell", "cat", "/proc/sys/kernel/random/boot_id"))
            # A delayed software reboot can briefly leave the previous session fully booted.
            # Its boot_completed/unlocked flags are not evidence of a completed reboot.
            if current_boot_id != previous_boot_id and poll("shell", "getprop", "sys.boot_completed") == "1":
                poll("shell", "input", "keyevent", "KEYCODE_WAKEUP")
                poll("shell", "wm", "dismiss-keyguard")
                unlocked = poll("shell", "am", "get-started-user-state", "0")
                if unlocked.strip() == "RUNNING_UNLOCKED":
                    return current_boot_id
        except (ValueError, RuntimeError, subprocess.TimeoutExpired):
            pass
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        time.sleep(min(0.5, remaining))
    raise RuntimeError("Emulator did not establish a changed boot ID, completed boot and first unlock before timeout")


def validate_timeouts(args: argparse.Namespace) -> None:
    if not 1 <= args.test_timeout <= MAX_TEST_TIMEOUT or not 1 <= args.boot_timeout <= MAX_BOOT_TIMEOUT:
        raise ValueError(f"Test timeout must be 1–{MAX_TEST_TIMEOUT} seconds and boot timeout 1–{MAX_BOOT_TIMEOUT} seconds")


def run(args: argparse.Namespace) -> int:
    validate_id(args.application_id)
    if args.old_revision != HISTORICAL_REVISION or re.fullmatch(r"[0-9a-f]{40}", args.new_revision) is None:
        raise ValueError("Record exact historical f69121e and full current source revisions")
    validate_timeouts(args)
    if re.fullmatch(r"emulator-\d+", args.serial) is None:
        raise ValueError("Physical devices are rejected")
    with Path(f"/tmp/chikku-acceptance-{args.serial}.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        # Shared device-side quarantine survives host failure and serial reassignment.
        # A profile fixture may still be running or have changed HOME/preferences.
        require_clean_profile_device(args)
        apks = {name: apk_metadata(getattr(args, name + "_apk"), args, test=name.endswith("test"))
                for name in ("old", "new", "old_test", "new_test")}
        validate_apk_set(apks, args.application_id)
        environment = verify_emulator(args)
        directory = Path(args.output_root).resolve() / f"{dt.datetime.now(dt.timezone.utc):%Y%m%d-%H%M%S}-{uuid.uuid4().hex[:8]}"
        directory.mkdir(parents=True, exist_ok=False)
        report = {"status": "RUNNING", "label": "historical development-APK upgrade (no published production baseline)",
                  "started_at": dt.datetime.now(dt.timezone.utc).isoformat(), "environment": environment,
                  "old_revision": args.old_revision, "new_revision": args.new_revision, "apks": apks,
                  "source": _reliability.git_metadata(),
                  "checks": [], "scope": "production repositories in an isolated application ID on a disposable emulator"}
        evidence = EvidenceRun(args, report, directory)
        try:
            evidence.mutate("install-historical-app", "install", "--no-streaming", "--user", "0", apks["old"]["path"], timeout=90)
            evidence.mutate("install-historical-tests", "install", "--no-streaming", "-t", "--user", "0", apks["old_test"]["path"], timeout=90)
            if installed_version(args) != apks["old"]["version_code"]:
                raise RuntimeError("Installed historical APK version mismatch")
            evidence.test(apks["old"], "seedHistoricalProductionState", historical=True)
            evidence.mutate("replace-with-new-version", "install", "--no-streaming", "--user", "0", "-r", apks["new"]["path"], timeout=90)
            evidence.mutate("replace-new-tests", "install", "--no-streaming", "-t", "--user", "0", "-r", apks["new_test"]["path"], timeout=90)
            if installed_version(args) != apks["new"]["version_code"]:
                raise RuntimeError("Installed upgraded APK version mismatch")
            evidence.test(apks["new"], "verifyRetainedProductionState", widget_id=7301)
            # Launch with the HOME category; launcher role/system selection stays separate evidence.
            evidence.mutate("launch-HOME-after-upgrade", "shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
                            "-c", "android.intent.category.HOME", "-n", f"{args.application_id}/io.github.aj4abinjacob.chikku.home.HomeActivity")
            evidence.test(apks["new"], "verifyRetainedProductionState", widget_id=7301)
            before_boot_id = validate_boot_id(adb(args, "shell", "cat", "/proc/sys/kernel/random/boot_id"))
            evidence.record("boot-id-before-reboot", "RECORDED", boot_id=before_boot_id)
            evidence.mutate("reboot-disposable-emulator", "reboot")
            after_boot_id = wait_after_reboot(args, before_boot_id)
            evidence.record("boot-and-first-unlock", "PASS", before_boot_id=before_boot_id,
                            after_boot_id=after_boot_id, boot_id_changed=True)
            evidence.mutate("launch-HOME-after-reboot", "shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
                            "-c", "android.intent.category.HOME", "-n", f"{args.application_id}/io.github.aj4abinjacob.chikku.home.HomeActivity")
            evidence.test(apks["new"], "verifyRetainedProductionState", widget_id=7301)
            evidence.test(apks["new"], "exportProductionBackup")
            backup = directory / "synthetic-backup.json"
            evidence.mutate("pull-synthetic-backup", "pull", f"/sdcard/Android/data/{args.application_id}/files/cloud-acceptance-backup.json", str(backup))
            evidence.record("synthetic-backup-sha256", "RECORDED", sha256=hashlib.sha256(backup.read_bytes()).hexdigest(),
                            scope="Host-file fingerprint; fresh restore checks logical content separately")
            # The only uninstall is this scenario's freshly-created synthetic installation.
            evidence.mutate("fresh-install-remove-synthetic-app", "uninstall", args.application_id)
            evidence.mutate("fresh-install-new-app", "install", "--no-streaming", "--user", "0", apks["new"]["path"], timeout=90)
            evidence.mutate("restore-input", "push", str(backup), f"/data/local/tmp/{args.application_id}-restore.json")
            evidence.test(apks["new"], "restoreFreshProductionBackup")
            evidence.mutate("fresh-restore-force-stop", "shell", "am", "force-stop", args.application_id)
            evidence.test(apks["new"], "verifyRetainedProductionState", widget_id=0)
            evidence.test(apks["new"], "applyReplacementForDurableUndo")
            evidence.mutate("durable-undo-force-stop", "shell", "am", "force-stop", args.application_id)
            evidence.test(apks["new"], "verifyDurableUndoAfterRestart")
            evidence.test(apks["new"], "rejectMalformedAndFutureArchivesWithoutWrites")
            for stage in range(4):
                evidence.interrupted_restore(apks["new"], stage)
            evidence.test(apks["new"], "safDocumentPickerRoundTrip")
            report["status"] = "PASS"
        except AcceptanceBlocked as exc:
            report["status"] = "BLOCKED"
            evidence.record("acceptance-run", "BLOCKED", error=str(exc))
        except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as exc:
            report["status"] = "FAIL"
            evidence.record("acceptance-run", "FAIL", error=str(exc))
        finally:
            # Retain the isolated scenario and its journal/data even after success; no broad cleanup.
            if not any(check["name"] == "safDocumentPickerRoundTrip" for check in report["checks"]):
                evidence.record("SAF-document-picker", "NOT_RUN", reason="Separate UI fixture did not execute; service file transport is not SAF evidence")
            evidence.record("external-widget-consent-configuration-rebind", "NOT_RUN", reason="Synthetic widget reference only; genuine external-provider compatibility is separate acceptance")
            evidence.record("physical-OEM-TalkBack-battery-foldable", "UNAVAILABLE", reason="Cloud emulator cannot establish physical acceptance")
            report["ended_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
            report["counts"] = {status: sum(c["status"] == status for c in report["checks"])
                                for status in ("PASS", "FAIL", "SKIPPED", "BLOCKED", "RECORDED", "EXPECTED_INTERRUPTION", "NOT_RUN", "UNAVAILABLE")}
            (directory / "report.json").write_text(json.dumps(report, indent=2) + "\n")
        print(f"{report['status']}: {directory}")
        return 0 if report["status"] == "PASS" else 1


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--serial", required=True)
    result.add_argument("--avd-name", required=True)
    result.add_argument("--application-id", required=True)
    result.add_argument("--api", type=int, choices=(29, 36), default=36)
    for name in ("old", "new", "old-test", "new-test"):
        result.add_argument(f"--{name}-apk", required=True)
    result.add_argument("--old-revision", default=HISTORICAL_REVISION)
    result.add_argument("--new-revision", required=True)
    result.add_argument("--adb", default="adb")
    result.add_argument("--aapt2", default="aapt2")
    result.add_argument("--apksigner", default="apksigner")
    result.add_argument("--test-timeout", type=int, default=90,
                        help="Native invocation deadline, 1–300 seconds; default 90, allow slower software-emulator startup explicitly")
    result.add_argument("--boot-timeout", type=int, default=180,
                        help="Reboot/unlock deadline, 1–600 seconds; default 180, allow slower software boot explicitly")
    result.add_argument("--output-root", default=str(ROOT / "build/qa/cloud-upgrade"))
    return result


def main() -> int:
    try:
        return run(parser().parse_args())
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

