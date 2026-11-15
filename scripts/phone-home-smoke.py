#!/usr/bin/env python3
"""Opt-in physical-phone HOME smoke. Retains private local evidence; never prints user contents.

Temporarily selects the debug HOME, runs one native workflow, replaces the same-version APK,
records system-HOME starts and their launch states, and restores the original role/startup guard in finally.
It never uninstalls, reboots, grants notification access or changes user workspace rows.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import sqlite3
import subprocess
import tarfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--samples", type=int, default=5)
    args = parser.parse_args()
    if not 1 <= args.samples <= 10:
        parser.error("samples must be between 1 and 10")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    package = "io.github.aj4abinjacob.chikku.debug"
    role = "android.app.role.HOME"

    def adb(*parts, data=None, timeout=60):
        return subprocess.run([args.adb, "-s", args.serial, *parts], input=data,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              check=True, timeout=timeout).stdout

    def shell(*parts, **kwargs):
        return adb("shell", *parts, **kwargs).decode().strip()

    def snapshot(name):
        adb("shell", "am", "force-stop", package)
        archive = adb("exec-out", "run-as", package, "tar", "cf", "-", "shared_prefs", "databases", "no_backup")
        (output / f"private-{name}.tar").write_bytes(archive)
        (output / f"private-{name}.tar").chmod(0o600)
        files = {}
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            for member in tar.getmembers():
                if member.isfile():
                    path = Path(member.name)
                    if path.is_absolute() or ".." in path.parts:
                        raise RuntimeError("Unsafe private snapshot path")
                    files[str(path)] = tar.extractfile(member).read()
        return files

    def database_fingerprint(files, name):
        directory = output / f"private-db-{name}"
        directory.mkdir(exist_ok=True)
        # Repeated runs must not fingerprint stale database/WAL copies from an older snapshot.
        for previous in directory.iterdir():
            if previous.is_file():
                previous.unlink()
        for path, data in files.items():
            if path.startswith("databases/"):
                target = directory / Path(path).name
                target.write_bytes(data)
                target.chmod(0o600)
        rows = {}
        for file in directory.iterdir():
            if file.name.endswith(("-wal", "-shm", "-journal")):
                continue
            with sqlite3.connect(file) as db:
                tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")]
                rows[file.name] = {}
                for table in tables:
                    escaped = table.replace('"', '""')
                    values = db.execute(f'SELECT * FROM "{escaped}"').fetchall()
                    rows[file.name][table] = sorted(repr(row) for row in values)
        return hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()

    user = shell("am", "get-current-user")
    original_roles = shell("cmd", "role", "get-role-holders", "--user", user, role).splitlines()
    if len(original_roles) != 1:
        raise RuntimeError("A single original HOME is required for safe reversion")
    original = original_roles[0]
    (output / "original-home-role.txt").write_text(original + "\n")
    before = snapshot("before-home")
    result = {"physicalPhone": True, "sameVersionReplacement": True, "rebootTested": False,
              "apkSha256": hashlib.sha256(args.apk.read_bytes()).hexdigest()}
    try:
        shell("cmd", "role", "add-role-holder", "--user", user, role, package)
        if shell("cmd", "role", "get-role-holders", "--user", user, role) != package:
            raise RuntimeError("Temporary HOME selection failed")
        log = shell("am", "instrument", "-w", "-r", "-e", "class",
                    "io.github.aj4abinjacob.chikku.home.DefaultHomeDeviceTest",
                    package + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=120)
        (output / "default-home-instrumentation.log").write_text(log)
        if "OK (1 test)" not in log or "INSTRUMENTATION_STATUS_CODE: -4" in log:
            raise RuntimeError("Native default-HOME workflow failed or skipped; inspect the saved log")
        result["defaultHomeWorkflowPassed"] = True
        print("Native default-HOME workflow passed", flush=True)
        adb("shell", "am", "force-stop", package)
        adb("install", "--no-streaming", "-r", str(args.apk), timeout=90)
        if shell("cmd", "role", "get-role-holders", "--user", user, role) != package:
            raise RuntimeError("HOME selection lost during APK replacement")
        samples = []
        for sample in range(args.samples):
            adb("shell", "am", "force-stop", package)
            log = shell("am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
            (output / f"cold-home-{sample + 1}.txt").write_text(log + "\n")
            fields = dict(line.split(":", 1) for line in log.splitlines() if ":" in line)
            if fields.get("Status", "").strip() != "ok" or package not in log:
                raise RuntimeError("System HOME did not resolve to the debug launcher")
            samples.append({"launchState": fields.get("LaunchState", "").strip(),
                            "totalTimeMs": int(fields["TotalTime"].strip())})
            # Let the real startup guard mark the start stable before the next process stop.
            time.sleep(6)
            print(f"System-HOME sample {sample + 1}/{args.samples} complete", flush=True)
        result["homeLaunchSamples"] = samples
        result["confirmedColdHomeTimeMs"] = [s["totalTimeMs"] for s in samples if s["launchState"] == "COLD"]
        result["timingLimit"] = "Android may restart default HOME before am start; only COLD samples measure startup. No P95/frame/battery acceptance."
        after = snapshot("after-home")
        result["settingsExact"] = before.get("shared_prefs/chikku_settings.xml") == after.get("shared_prefs/chikku_settings.xml")
        result["databaseLogicalRowsUnchanged"] = database_fingerprint(before, "before") == database_fingerprint(after, "after")
        result["noBackupFilesUnchanged"] = {k: v for k, v in before.items() if k.startswith("no_backup/")} == {k: v for k, v in after.items() if k.startswith("no_backup/")}
        if not all(result[key] for key in ["settingsExact", "databaseLogicalRowsUnchanged", "noBackupFilesUnchanged"]):
            raise RuntimeError("Launcher state comparison failed; private snapshots retained")
    finally:
        # Restore role even when a test or sample fails; never reset another launcher's data.
        shell("cmd", "role", "add-role-holder", "--user", user, role, original)
        result["originalHomeRestored"] = shell("cmd", "role", "get-role-holders", "--user", user, role) == original
        adb("shell", "am", "force-stop", package)
        guard = before.get("shared_prefs/chikku_startup_guard.xml")
        if guard is None:
            adb("shell", "run-as", package, "rm", "-f", "shared_prefs/chikku_startup_guard.xml")
        else:
            adb("exec-in", "run-as", package, "tee", "shared_prefs/chikku_startup_guard.xml", data=guard)
        (output / "phone-home-results.json").write_text(json.dumps(result, indent=2) + "\n")
        if not result["originalHomeRestored"]:
            raise RuntimeError("Original HOME reversion needs attention")
    print("Original HOME restored; saved settings and database rows preserved", flush=True)


if __name__ == "__main__":
    main()
