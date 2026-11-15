#!/usr/bin/env python3
"""Orchestrate all four explicit process-death restore fixture stages on a disposable emulator."""
from __future__ import annotations

import argparse
import datetime as dt
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
TEST_CLASS = "io.github.aj4abinjacob.chikku.backup.ProcessDeathRestoreDeviceTest"
TARGET_PACKAGE = "io.github.aj4abinjacob.chikku.debug"
RUNNER_COMPONENT = "io.github.aj4abinjacob.chikku.debug.test/androidx.test.runner.AndroidJUnitRunner"
MAX_PAIR_SECONDS = 90

# Reuse the repository runner's AndroidJUnitRunner status parser, including its verified code map.
_spec = importlib.util.spec_from_file_location("reliability_runner", ROOT / "scripts/run-reliability.py")
if _spec is None or _spec.loader is None:
    raise RuntimeError("Unable to load scripts/run-reliability.py")
_reliability = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_reliability)


def adb_call(adb: str, serial: str, *args: str, timeout: int = 10) -> subprocess.CompletedProcess:
    return subprocess.run([adb, "-s", serial, *args], cwd=ROOT, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=timeout, check=False)


def adb_checked(adb: str, serial: str, *args: str, timeout: int = 10) -> str:
    result = adb_call(adb, serial, *args, timeout=timeout)
    if result.returncode != 0:
        raise RuntimeError(result.stdout.strip() or f"adb exited {result.returncode}")
    return result.stdout.strip()


def verify_emulator(adb: str, serial: str) -> dict:
    if not re.fullmatch(r"emulator-\d+", serial):
        raise ValueError("--serial must be emulator-N; physical devices are rejected")
    if adb_checked(adb, serial, "get-state") != "device":
        raise RuntimeError("emulator is not in device state")
    qemu = adb_checked(adb, serial, "shell", "getprop", "ro.kernel.qemu")
    if qemu != "1":
        raise RuntimeError(f"emulator verification failed: ro.kernel.qemu={qemu!r}")
    prop_names = {
        "manufacturer": "ro.product.manufacturer", "model": "ro.product.model",
        "device": "ro.product.device", "release": "ro.build.version.release",
        "sdk": "ro.build.version.sdk", "build_fingerprint": "ro.build.fingerprint",
    }
    result = {key: adb_checked(adb, serial, "shell", "getprop", prop) for key, prop in prop_names.items()}
    result.update({
        "serial": serial, "kernel_qemu": qemu,
        "display_size": adb_checked(adb, serial, "shell", "wm", "size"),
        "display_density": adb_checked(adb, serial, "shell", "wm", "density"),
        "memory": adb_checked(adb, serial, "shell", "cat", "/proc/meminfo").splitlines()[0],
    })
    return result


def git_metadata() -> dict:
    def git(*args: str) -> str:
        result = subprocess.run(["git", *args], cwd=ROOT, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.DEVNULL, check=False)
        return result.stdout.strip()
    return {"base_sha": git("rev-parse", "HEAD"), "dirty": bool(git("status", "--porcelain"))}


def hash_file(path: str | None) -> dict | None:
    if path is None:
        return None
    file = Path(path).expanduser().resolve(strict=True)
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return {"path": str(file), "sha256": digest.hexdigest(), "size_bytes": file.stat().st_size}


def run_instrumentation(command: list[str], log_path: Path, timeout: float) -> dict:
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    started_monotonic = time.monotonic()
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=ROOT, text=True, stdout=log, stderr=subprocess.STDOUT)
        try:
            return_code = process.wait(timeout=max(0.1, timeout))
            timed_out = False
        except subprocess.TimeoutExpired:
            # Stop the local adb client. We never begin another invocation after a timeout because
            # remote instrumentation may still be active; fixture data is kept for diagnosis.
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            return_code = process.returncode
            timed_out = True
    output = log_path.read_text(encoding="utf-8", errors="replace")
    return {
        "command": command, "command_display": shlex.join(command),
        "started_at": started, "ended_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "duration_seconds": round(time.monotonic() - started_monotonic, 3),
        "exit_code": return_code, "timeout": timed_out,
        "parsed": _reliability.parse_instrumentation_output(output),
        "log": str(log_path),
    }


def wait_for_marker(adb: str, serial: str, token: str, deadline: float,
                    preparation: subprocess.Popen) -> tuple[str | None, int, list[str]]:
    relative = f"no_backup/restore-process-{token}/cut.marker"
    command = ["run-as", TARGET_PACKAGE, "cat", relative]
    checks = 0
    last_output = ""
    while time.monotonic() < deadline:
        checks += 1
        result = adb_call(adb, serial, "shell", *command, timeout=min(8, max(1, int(deadline - time.monotonic()))))
        last_output = result.stdout.strip()
        if result.returncode == 0:
            return last_output, checks, command
        if any(marker in last_output.lower() for marker in ("device offline", "device not found", "no devices/emulators found")):
            raise RuntimeError(f"adb transport lost while waiting for synthetic marker: {last_output}")
        if preparation.poll() is not None:
            raise RuntimeError(f"preparation invocation exited with status {preparation.returncode} before publishing its marker")
        time.sleep(min(0.3, max(0, deadline - time.monotonic())))
    return None, checks, command


def valid_marker(contents: str | None, stage: int) -> tuple[int | None, bool]:
    if contents is None:
        return None, False
    match = re.fullmatch(r"([1-9][0-9]*):([0-3])", contents.strip())
    if not match:
        return None, False
    pid, recorded_stage = int(match.group(1)), int(match.group(2))
    return pid, recorded_stage == stage


def prepare_args(args: argparse.Namespace, token: str, stage: int) -> list[str]:
    return [args.adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
            "-e", "class", f"{TEST_CLASS}#pauseRestoreForExternalProcessKill",
            "-e", "fixtureId", token, "-e", "cutAfterStore", str(stage), args.runner]


def recovery_args(args: argparse.Namespace, token: str) -> list[str]:
    return [args.adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
            "-e", "class", f"{TEST_CLASS}#recoverAfterExternalProcessKill",
            "-e", "fixtureId", token, args.runner]


def recovery_passed(parsed: dict) -> bool:
    return (
        parsed.get("status") == "PASS"
        and parsed.get("announced_tests") == 1
        and parsed.get("accounted_tests") == 1
        and parsed.get("counts") == {"passed": 1, "failed": 0, "ignored": 0, "assumption_skipped": 0}
        and len(parsed.get("tests", [])) == 1
        and parsed["tests"][0].get("class") == TEST_CLASS
        and parsed["tests"][0].get("test") == "recoverAfterExternalProcessKill"
        and parsed["tests"][0].get("outcome") == "PASSED"
    )


def preparation_was_paused(log_path: Path, parsed: dict) -> bool:
    output = log_path.read_text(encoding="utf-8", errors="replace")
    started = re.search(
        r"INSTRUMENTATION_STATUS: test=pauseRestoreForExternalProcessKill\s+INSTRUMENTATION_STATUS_CODE: 1",
        output,
    ) is not None
    no_terminal_pass = not any(
        test.get("test") == "pauseRestoreForExternalProcessKill" and test.get("outcome") == "PASSED"
        for test in parsed.get("tests", [])
    )
    return started and no_terminal_pass and parsed.get("status") != "PASS"


def force_stop(adb: str, serial: str, output_dir: Path, token: str, stage: int, timeout: float = 10) -> dict:
    command = [adb, "-s", serial, "shell", "am", "force-stop", TARGET_PACKAGE]
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    try:
        result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=max(0.1, timeout), check=False)
        output, return_code, timed_out = result.stdout, result.returncode, False
    except subprocess.TimeoutExpired as exc:
        raw = exc.stdout or b""
        output = raw.decode(errors="replace") if isinstance(raw, bytes) else raw
        return_code, timed_out = None, True
    log_path = output_dir / f"stage-{stage}-{token}-force-stop.log"
    log_path.write_text(output, encoding="utf-8")
    return {"command": command, "command_display": shlex.join(command), "exit_code": return_code,
            "timeout": timed_out, "output": output.strip(), "started_at": started,
            "ended_at": dt.datetime.now(dt.timezone.utc).isoformat(), "log": str(log_path)}


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def run(args: argparse.Namespace) -> int:
    if not 1 <= args.pair_timeout_seconds <= MAX_PAIR_SECONDS:
        raise ValueError(f"--pair-timeout-seconds must be from 1 to {MAX_PAIR_SECONDS}")
    device = verify_emulator(args.adb, args.serial)
    stamp = dt.datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")
    output_dir = Path(args.output_root) / f"{stamp}-{uuid.uuid4().hex[:8]}"
    output_dir.mkdir(parents=True, exist_ok=False)
    report = {
        "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "device": device, "git": _reliability.git_metadata(), "apk": hash_file(args.apk),
        "runner": args.runner, "target_package": TARGET_PACKAGE,
        "pair_timeout_seconds": args.pair_timeout_seconds,
        "stages": [], "output_directory": str(output_dir), "status": "RUNNING",
    }
    write_json(output_dir / "metadata.json", {key: report[key] for key in
               ("started_at", "device", "git", "apk", "runner", "target_package", "pair_timeout_seconds")})

    for stage in range(4):
        token = str(uuid.uuid4())
        stage_record = {"stage": stage, "fixture_id": token, "status": "RUNNING", "commands": []}
        report["stages"].append(stage_record)
        pair_deadline = time.monotonic() + args.pair_timeout_seconds

        prep_log = output_dir / f"stage-{stage}-{token}-prepare.log"
        prep_command = prepare_args(args, token, stage)
        stage_record["commands"].append({"kind": "prepare", "command": prep_command,
                                         "command_display": shlex.join(prep_command), "log": str(prep_log)})
        prep_started = dt.datetime.now(dt.timezone.utc).isoformat()
        with prep_log.open("w", encoding="utf-8") as log:
            prep_process = subprocess.Popen(prep_command, cwd=ROOT, text=True, stdout=log, stderr=subprocess.STDOUT)
            try:
                marker, poll_count, marker_command = wait_for_marker(args.adb, args.serial, token, pair_deadline, prep_process)
            except (RuntimeError, subprocess.TimeoutExpired) as exc:
                marker, poll_count, marker_command = None, 0, ["run-as", TARGET_PACKAGE, "cat", f"no_backup/restore-process-{token}/cut.marker"]
                stage_record["error"] = str(exc)
            stage_record["marker_command"] = [args.adb, "-s", args.serial, "shell", *marker_command]
            stage_record["marker_polls"] = poll_count
            stage_record["marker_value"] = marker
            (output_dir / f"stage-{stage}-{token}-marker.log").write_text(marker or "MARKER NOT OBSERVED\n", encoding="utf-8")
            prep_process_state = prep_process.poll()

        if marker is None:
            stage_record["status"] = "FAIL"
            stage_record["error"] = stage_record.get("error", "synthetic cut marker did not appear before timeout")
            if prep_process_state is None:
                # No marker means the fixture is not known to be safely paused. Stop remote work and
                # preserve its synthetic files for diagnosis.
                stage_record["failure_force_stop"] = force_stop(args.adb, args.serial, output_dir, token, stage,
                                                                 timeout=pair_deadline - time.monotonic())
                prep_process.terminate()
                try:
                    prep_process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    prep_process.kill(); prep_process.wait()
            stage_record["prepare_started_at"] = prep_started
            prep_output = prep_log.read_text(encoding="utf-8", errors="replace")
            stage_record["prepare"] = {
                "started_at": prep_started, "ended_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                "exit_code": prep_process.returncode, "parsed": _reliability.parse_instrumentation_output(prep_output),
                "expected_interruption": False, "log": str(prep_log),
            }
            report["status"] = "FAIL"
            break

        old_pid, stage_matches = valid_marker(marker, stage)
        stage_record["old_pid"] = old_pid
        if not stage_matches:
            stage_record["status"] = "FAIL"
            stage_record["error"] = f"marker did not contain a valid PID and expected stage {stage}"
            stage_record["failure_force_stop"] = force_stop(args.adb, args.serial, output_dir, token, stage,
                                                             timeout=pair_deadline - time.monotonic())
            report["status"] = "FAIL"
            break

        stopped = force_stop(args.adb, args.serial, output_dir, token, stage,
                             timeout=pair_deadline - time.monotonic())
        stage_record["commands"].append({"kind": "force-stop", **stopped})
        if stopped["exit_code"] != 0 or stopped["timeout"]:
            stage_record["status"] = "FAIL"
            stage_record["error"] = "am force-stop failed"
            report["status"] = "FAIL"
            break
        try:
            remaining = max(0.1, pair_deadline - time.monotonic())
            prep_return = prep_process.wait(timeout=min(5, remaining))
        except subprocess.TimeoutExpired:
            # The app process was force-stopped. Bound the local adb wait; no later test may overlap.
            prep_process.terminate()
            try:
                prep_return = prep_process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                prep_process.kill(); prep_return = prep_process.wait()
        prep_output = prep_log.read_text(encoding="utf-8", errors="replace")
        prep_parsed = _reliability.parse_instrumentation_output(prep_output)
        stage_record["prepare"] = {
            "started_at": prep_started, "ended_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "exit_code": prep_return, "parsed": prep_parsed,
            "expected_interruption": preparation_was_paused(prep_log, prep_parsed), "log": str(prep_log),
        }
        if not stage_record["prepare"]["expected_interruption"]:
            stage_record["status"] = "FAIL"
            stage_record["error"] = "preparation invocation did not stop after the paused test without a pass"
            report["status"] = "FAIL"
            break

        if time.monotonic() >= pair_deadline:
            stage_record["status"] = "FAIL"
            stage_record["error"] = "pair deadline expired before recovery invocation"
            report["status"] = "FAIL"
            break
        recovery_command = recovery_args(args, token)
        recovery_log = output_dir / f"stage-{stage}-{token}-recover.log"
        remaining = pair_deadline - time.monotonic()
        recovery = run_instrumentation(recovery_command, recovery_log, remaining)
        stage_record["commands"].append({"kind": "recover", **recovery})
        stage_record["recovery"] = recovery
        if recovery["timeout"] or not recovery_passed(recovery["parsed"]):
            stage_record["status"] = "FAIL"
            stage_record["error"] = "recovery invocation did not report exactly one completed passing assertion test"
            report["status"] = "FAIL"
            break
        stage_record["status"] = "PASS"
        print(f"PASS: stage {stage}; fresh fixture {token}; old PID {old_pid}")
        write_json(output_dir / "report.json", report)

    if report["status"] == "RUNNING":
        report["status"] = "PASS" if len(report["stages"]) == 4 and all(s["status"] == "PASS" for s in report["stages"]) else "FAIL"
    statuses = [stage["status"] for stage in report["stages"]]
    report["counts"] = {
        "pass": statuses.count("PASS"), "fail": statuses.count("FAIL"),
        "blocked": statuses.count("BLOCKED"), "not_run": 4 - len(statuses),
        "expected_stages": 4,
    }
    report["ended_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
    write_json(output_dir / "report.json", report)
    print(f"Overall: {report['status']} — {output_dir}")
    return 0 if report["status"] == "PASS" else 1


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--serial", required=True, help="verified disposable emulator serial, e.g. emulator-5554")
    result.add_argument("--adb", default="adb", help="adb executable path")
    result.add_argument("--runner", default=RUNNER_COMPONENT, help="installed test instrumentation component")
    result.add_argument("--apk", help="optional APK path to record SHA-256")
    result.add_argument("--pair-timeout-seconds", type=int, default=MAX_PAIR_SECONDS,
                        help="deadline for each prepare/kill/recover pair, maximum 90 seconds")
    result.add_argument("--output-root", default=str(ROOT / "build/qa/process-death"))
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return run(args)
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
