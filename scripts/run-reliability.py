#!/usr/bin/env python3
"""Run installed Android instrumentation classes sequentially on a named emulator.

The runner is deliberately read-only outside the test APK's own fixtures: it never installs,
wipes, changes settings/roles, grants permissions, or reads logcat/package inventories.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
ANDROID_TEST_ROOT = ROOT / "app/src/androidTest/java"
DEFAULT_RUNNER = "io.github.aj4abinjacob.chikku.debug.test/androidx.test.runner.AndroidJUnitRunner"
PROCESS_DEATH = "io.github.aj4abinjacob.chikku.backup.ProcessDeathRestoreDeviceTest"
MAX_BATCH = 3
MAX_TIMEOUT = 12 * 60 * 60
PROFILE_QUARANTINE_PATH = "/data/local/tmp/chikku-acceptance-profile-unsafe"
PROFILE_QUARANTINE_CHECK = f"if [ -e {PROFILE_QUARANTINE_PATH} ]; then echo UNSAFE; else echo CLEAN; fi"
TRANSPORT_MARKERS = (
    "device offline", "no devices/emulators found", "device not found",
    "cannot connect to daemon", "error: closed", "transport error",
)


def parse_instrumentation_output(output: str) -> dict:
    """Parse AndroidJUnitRunner test lifecycle statuses and the final instrumentation result."""
    tests: list[dict] = []
    fields: dict[str, str] = {}
    starts: dict[tuple[str | None, str | None], int] = {}
    terminals: dict[tuple[str | None, str | None], int] = {}
    result_code: int | None = None
    announced_tests: int | None = None
    status_outcomes = {0: "PASSED", -1: "FAILED", -2: "FAILED", -3: "IGNORED", -4: "ASSUMPTION_SKIPPED"}
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            payload = line[len("INSTRUMENTATION_STATUS: "):]
            key, sep, value = payload.partition("=")
            if sep:
                fields[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            try:
                code = int(line.rsplit(" ", 1)[1])
            except ValueError:
                code = None
            if code is not None:
                try:
                    announced_tests = max(announced_tests or 0, int(fields["numtests"]))
                except (KeyError, ValueError):
                    pass
                identity = (fields.get("class"), fields.get("test"))
                if code == 1:  # AndroidJUnitRunner: test started.
                    starts[identity] = starts.get(identity, 0) + 1
                else:
                    outcome = status_outcomes.get(code, "FAILED")
                    tests.append({
                        "class": identity[0], "test": identity[1], "code": code,
                        "stream": fields.get("stack", fields.get("stream")),
                        "outcome": outcome,
                    })
                    terminals[identity] = terminals.get(identity, 0) + 1
                fields = {}
        elif line.startswith("INSTRUMENTATION_CODE: "):
            try:
                result_code = int(line.rsplit(" ", 1)[1])
            except ValueError:
                result_code = None

    counts = {
        "passed": sum(t["outcome"] == "PASSED" for t in tests),
        "failed": sum(t["outcome"] == "FAILED" for t in tests),
        "ignored": sum(t["outcome"] == "IGNORED" for t in tests),
        "assumption_skipped": sum(t["outcome"] == "ASSUMPTION_SKIPPED" for t in tests),
    }
    unfinished = {f"{class_name}#{test_name}": count - terminals.get((class_name, test_name), 0)
                  for (class_name, test_name), count in starts.items()
                  if count > terminals.get((class_name, test_name), 0)}
    accounted = sum(terminals.values())
    complete = (
        result_code == -1 and announced_tests is not None and announced_tests > 0
        and accounted == announced_tests and not unfinished and counts["failed"] == 0
    )
    if not complete:
        status = "FAIL"
    elif counts["passed"] == 0:
        status = "BLOCKED"
    else:
        status = "PASS"
    return {
        "instrumentation_code": result_code,
        "announced_tests": announced_tests,
        "accounted_tests": accounted,
        "unfinished_tests": unfinished,
        "tests": tests,
        "counts": counts,
        "status": status,
    }


def discover_classes() -> list[str]:
    classes: set[str] = set()
    package_pattern = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
    # Top-level declarations only: nested Fixture/helper classes are not runnable test classes.
    class_pattern = re.compile(r"^(?:(?:public|internal|open|abstract|final|private|protected)\s+)*(?:class|object)\s+(\w+)", re.MULTILINE)
    for path in ANDROID_TEST_ROOT.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        if "@Test" not in source:
            continue
        package_match = package_pattern.search(source)
        if not package_match:
            continue
        package = package_match.group(1)
        classes.update(f"{package}.{match}" for match in class_pattern.findall(source))
    return sorted(classes)


def adb_read(adb: str, serial: str, *args: str, timeout: int = 20) -> str:
    completed = subprocess.run([adb, "-s", serial, *args], cwd=ROOT, text=True,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               timeout=timeout, check=False)
    if completed.returncode:
        raise RuntimeError(completed.stdout.strip() or f"adb returned {completed.returncode}")
    return completed.stdout.strip()


def device_metadata(adb: str, serial: str) -> dict:
    state = adb_read(adb, serial, "get-state")
    if state != "device":
        raise RuntimeError(f"emulator state is {state!r}")
    qemu = adb_read(adb, serial, "shell", "getprop", "ro.kernel.qemu")
    if qemu != "1":
        raise RuntimeError(f"serial {serial} is not verified as an emulator (ro.kernel.qemu={qemu!r})")
    if adb_read(adb, serial, "shell", PROFILE_QUARANTINE_CHECK) != "CLEAN":
        raise RuntimeError("Unsafe profile acceptance AVD: destroy and recreate before reuse")
    props = {
        "manufacturer": "ro.product.manufacturer", "model": "ro.product.model",
        "device": "ro.product.device", "release": "ro.build.version.release",
        "sdk": "ro.build.version.sdk", "build_fingerprint": "ro.build.fingerprint",
    }
    metadata = {key: adb_read(adb, serial, "shell", "getprop", prop) for key, prop in props.items()}
    metadata["serial"] = serial
    metadata["kernel_qemu"] = qemu
    metadata["display_size"] = adb_read(adb, serial, "shell", "wm", "size")
    metadata["display_density"] = adb_read(adb, serial, "shell", "wm", "density")
    metadata["memory"] = adb_read(adb, serial, "shell", "cat", "/proc/meminfo").splitlines()[0]
    return metadata


def git_metadata() -> dict:
    def git(*args: str) -> str:
        return subprocess.run(["git", *args], cwd=ROOT, text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL, check=False).stdout.strip()
    diff = subprocess.run(["git", "diff", "HEAD", "--binary"], cwd=ROOT,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False).stdout
    untracked = subprocess.run(["git", "ls-files", "--others", "--exclude-standard", "-z"], cwd=ROOT,
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False).stdout
    hasher = hashlib.sha256(diff)
    source_roots = ("app/src/", "core/model/src/", "scripts/", "docs/")
    for raw_path in untracked.split(b"\0"):
        if not raw_path:
            continue
        relative = raw_path.decode(errors="replace")
        if relative.startswith(source_roots):
            path = ROOT / relative
            if path.is_file():
                hasher.update(relative.encode())
                hasher.update(path.read_bytes())
    status = git("status", "--porcelain")
    return {"base_sha": git("rev-parse", "HEAD"), "dirty": bool(status),
            "source_diff_sha256": hasher.hexdigest()}


def sha256_file(path: str | None) -> dict | None:
    if path is None:
        return None
    file = Path(path).expanduser().resolve(strict=True)
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {"path": str(file), "sha256": digest.hexdigest(), "size_bytes": file.stat().st_size}


def chunks(values: list[str], size: int = MAX_BATCH):
    for start in range(0, len(values), size):
        yield values[start:start + size]


def run(args: argparse.Namespace) -> int:
    if not re.fullmatch(r"emulator-\d+", args.serial):
        raise ValueError("--serial must name an emulator (for example emulator-5554); physical devices are rejected")
    if not 1 <= args.timeout_seconds <= MAX_TIMEOUT:
        raise ValueError(f"--timeout-seconds must be between 1 and {MAX_TIMEOUT} (12 hours)")
    if bool(args.classes) == bool(args.all_classes):
        raise ValueError("choose exactly one of --classes or --all")

    selected = args.classes or discover_classes()
    if args.all_classes:
        selected = [name for name in selected if name != PROCESS_DEATH]
    if not selected:
        raise ValueError("no instrumentation classes selected or discovered")
    if PROCESS_DEATH in selected:
        raise ValueError(f"{PROCESS_DEATH} is externally orchestrated and cannot run in this standard runner")
    if len(set(selected)) != len(selected):
        raise ValueError("duplicate class names are not allowed")
    valid = set(discover_classes())
    unknown = sorted(set(selected) - valid)
    if unknown:
        raise ValueError("unknown instrumentation class(es): " + ", ".join(unknown))

    stamp = dt.datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")
    output_dir = Path(args.output_root) / f"{stamp}-{uuid.uuid4().hex[:8]}"
    output_dir.mkdir(parents=True, exist_ok=False)
    metadata = {"started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                "device": device_metadata(args.adb, args.serial), "git": git_metadata(),
                "apk": sha256_file(args.apk), "test_apk": sha256_file(args.test_apk), "adb": args.adb,
                "runner": args.runner, "timeout_seconds": args.timeout_seconds,
                "selection": selected, "execution": "NOT RUN" if args.dry_run else "RUN"}
    (output_dir / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    report = {**metadata, "output_directory": str(output_dir), "batches": [], "status": "NOT RUN" if args.dry_run else "RUNNING"}

    stop_status: str | None = None
    selected_batches = list(chunks(selected))
    for batch_number, batch in enumerate(selected_batches, start=1):
        batch_record = {"batch": batch_number, "classes": batch, "status": "NOT RUN", "commands": []}
        report["batches"].append(batch_record)
        for class_index, class_name in enumerate(batch, start=1):
            command = [args.adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
                       "-e", "class", class_name, args.runner]
            item = {"class": class_name, "command": shlex.join(command),
                    "started_at": dt.datetime.now(dt.timezone.utc).isoformat()}
            batch_record["commands"].append(item)
            if args.dry_run:
                item.update({"status": "NOT RUN", "ended_at": dt.datetime.now(dt.timezone.utc).isoformat()})
                continue
            log_path = output_dir / f"batch-{batch_number:02d}-{class_index:02d}-{class_name.rsplit('.', 1)[-1]}.log"
            started_monotonic = time.monotonic()
            try:
                process = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT, timeout=args.timeout_seconds, check=False)
                output = process.stdout
                item["exit_code"] = process.returncode
                item["parsed"] = parse_instrumentation_output(output)
                transport_lost = any(marker in output.lower() for marker in TRANSPORT_MARKERS)
                item["status"] = item["parsed"]["status"]
                if process.returncode != 0 and not transport_lost:
                    item["status"] = "FAIL"
                item["transport_lost"] = transport_lost
                if transport_lost:
                    item["status"] = "BLOCKED"
                    item["reason"] = "adb transport lost; stopped"
                    stop_status = "BLOCKED"
            except subprocess.TimeoutExpired as exc:
                raw = exc.stdout or b""
                output = raw.decode(errors="replace") if isinstance(raw, bytes) else raw
                # A timed-out `am instrument -w` may still be executing on-device after the host
                # shell exits. Stop here rather than starting another class concurrently.
                item.update({"status": "FAIL", "reason": "TIMEOUT; remote instrumentation state is unknown; stopped",
                             "timeout_seconds": args.timeout_seconds,
                             "parsed": parse_instrumentation_output(output), "transport_lost": False})
                stop_status = "FAIL"
            except FileNotFoundError as exc:
                output = str(exc)
                item.update({"status": "FAIL", "error": output, "transport_lost": False})
            item["ended_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
            item["duration_seconds"] = round(time.monotonic() - started_monotonic, 3)
            log_path.write_text(output, encoding="utf-8", errors="replace")
            item["log"] = str(log_path)
            print(f"{item['status']}: {class_name} ({log_path})")
            if item.get("transport_lost"):
                break
            if stop_status is not None:
                break
        else:
            outcomes = {command["status"] for command in batch_record["commands"]}
            batch_record["status"] = summarize_statuses(outcomes)
            write_report(output_dir, report)
            continue
        # No command is issued after a timeout/transport loss; mark the remainder explicitly.
        for class_index, class_name in enumerate(batch[class_index:], start=class_index + 1):
            remaining_command = [args.adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
                                 "-e", "class", class_name, args.runner]
            batch_record["commands"].append({"class": class_name, "command": shlex.join(remaining_command),
                                             "status": "NOT RUN", "reason": "previous command stopped the run"})
        batch_record["status"] = stop_status
        for later_number, later_batch in enumerate(selected_batches[batch_number:], start=batch_number + 1):
            later_record = {"batch": later_number, "classes": later_batch, "status": "NOT RUN", "commands": []}
            for class_name in later_batch:
                later_command = [args.adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
                                 "-e", "class", class_name, args.runner]
                later_record["commands"].append({"class": class_name, "command": shlex.join(later_command),
                                                 "status": "NOT RUN", "reason": "previous command stopped the run"})
            report["batches"].append(later_record)
        write_report(output_dir, report)
        break

    if args.dry_run:
        report["status"] = "NOT RUN"
    elif report["status"] == "RUNNING":
        statuses = [command.get("status") for batch in report["batches"] for command in batch["commands"]]
        report["status"] = summarize_statuses(statuses)
    elif stop_status is not None:
        report["status"] = stop_status
    report["ended_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
    write_report(output_dir, report)
    print(f"Overall: {report['status']} — {output_dir}")
    return 0 if report["status"] in ("PASS", "NOT RUN") else 1


def summarize_statuses(statuses) -> str:
    statuses = set(statuses)
    if "FAIL" in statuses:
        return "FAIL"
    if "BLOCKED" in statuses:
        return "BLOCKED"
    if statuses and statuses <= {"NOT RUN"}:
        return "NOT RUN"
    if "PASS" in statuses:
        return "PASS"
    return "BLOCKED"


def write_report(output_dir: Path, report: dict) -> None:
    (output_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--serial", required=True, help="emulator serial, e.g. emulator-5554; physical devices are rejected")
    select = result.add_mutually_exclusive_group(required=True)
    select.add_argument("--classes", nargs="+", help="fully qualified test classes (maximum 3 run per batch)")
    select.add_argument("--all", dest="all_classes", action="store_true", help="discover classes; excludes external ProcessDeath fixture")
    result.add_argument("--runner", default=DEFAULT_RUNNER, help="installed AndroidJUnitRunner component")
    result.add_argument("--adb", default="adb", help="adb executable path (default: adb from PATH)")
    result.add_argument("--apk", help="optional installed APK file whose SHA-256 should be recorded")
    result.add_argument("--test-apk", help="optional installed instrumentation APK whose SHA-256 should be recorded")
    result.add_argument("--timeout-seconds", type=int, default=1800, help="per-class timeout, maximum 43200 seconds")
    result.add_argument("--output-root", default=str(ROOT / "build/qa/reliability"))
    result.add_argument("--dry-run", action="store_true", help="validate emulator and record planned commands as NOT RUN")
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return run(args)
    except (ValueError, RuntimeError, subprocess.TimeoutExpired, OSError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

