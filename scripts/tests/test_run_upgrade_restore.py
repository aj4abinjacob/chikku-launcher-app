import argparse
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("upgrade_restore", ROOT / "scripts/run-upgrade-restore.py")
runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runner)
APP_ID = "io.github.aj4abinjacob.chikku.acceptance.upgrade"
CERTIFICATE = "a" * 64
OLD_BOOT_ID = "11111111-1111-4111-8111-111111111111"
NEW_BOOT_ID = "22222222-2222-4222-8222-222222222222"


def arguments(**overrides):
    values = dict(application_id=APP_ID, serial="emulator-5554", avd_name="chikku_acceptance_upgrade",
                  api=36, adb="adb", old_revision=runner.HISTORICAL_REVISION,
                  new_revision="1" * 40, test_timeout=90, boot_timeout=180)
    values.update(overrides)
    return argparse.Namespace(**values)


def apk_set():
    return {name: dict(application_id=APP_ID + (".test" if name.endswith("test") else ""),
                       version_code=100 if name.startswith("old") else 101,
                       version_name=name, certificate_sha256=CERTIFICATE, debuggable=name.endswith("test"))
            for name in ("old", "new", "old_test", "new_test")}


def passing_result(method="verifyRetainedProductionState"):
    output = (f"INSTRUMENTATION_STATUS: class={runner.TEST_CLASS}\n"
              f"INSTRUMENTATION_STATUS: test={method}\n"
              "INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS_CODE: 1\n"
              f"INSTRUMENTATION_STATUS: class={runner.TEST_CLASS}\n"
              f"INSTRUMENTATION_STATUS: test={method}\n"
              "INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS_CODE: 0\n"
              "INSTRUMENTATION_CODE: -1\n")
    return runner._reliability.parse_instrumentation_output(output)


class AcceptanceSafetyTest(unittest.TestCase):
    def test_profile_quarantine_blocks_upgrade_before_apk_or_device_mutation(self):
        for state in ("UNSAFE", "", "unexpected"):
            with self.subTest(state=state), patch.object(runner, "adb", return_value=state) as adb, \
                    patch.object(runner, "apk_metadata") as metadata:
                with self.assertRaisesRegex(RuntimeError, "Unsafe profile acceptance"):
                    runner.run(arguments())
                metadata.assert_not_called()
                adb.assert_called_once_with(arguments(), "shell", runner._reliability.PROFILE_QUARANTINE_CHECK)
        with patch.object(runner, "adb", return_value="CLEAN"):
            runner.require_clean_profile_device(arguments())

    def test_valid_isolated_id(self):
        self.assertEqual(runner.validate_id(APP_ID), APP_ID)

    def test_real_debug_or_production_ids_rejected_before_external_commands(self):
        for value in ("io.github.aj4abinjacob.chikku", "io.github.aj4abinjacob.chikku.debug",
                      APP_ID + ".debug", APP_ID + ";reboot", APP_ID + "x" * 48):
            with self.subTest(value=value), patch.object(runner, "command") as external:
                with self.assertRaises(ValueError):
                    runner.run(arguments(application_id=value))
                external.assert_not_called()

    def test_physical_serial_rejected_before_any_external_command(self):
        with patch.object(runner, "command") as external:
            with self.assertRaises(ValueError):
                runner.run(arguments(serial="R58M1234"))
            external.assert_not_called()

    def test_avd_scenario_must_match_before_any_device_query(self):
        with patch.object(runner, "adb") as external:
            with self.assertRaises(ValueError):
                runner.verify_emulator(arguments(avd_name="personal_avd"))
            external.assert_not_called()

    def test_fresh_avd_requires_only_system_user_and_rejects_partial_output(self):
        runner.require_fresh_system_user("Users:\n\tUserInfo{0:Owner:c13} running\n")
        for output in ("", "Users:\nUserInfo{10:Work:30}\n", "Users:\nUserInfo{0:Owner:c13}\nUserInfo{10:Work:30}\n",
                       "Users:\nUserInfo{0:Owner:c13}\npartial"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.require_fresh_system_user(output)

    def test_historical_revision_and_current_revision_required(self):
        for values in (dict(old_revision="1" * 40), dict(new_revision="main")):
            with self.subTest(values=values), patch.object(runner, "command") as external:
                with self.assertRaises(ValueError):
                    runner.run(arguments(**values))
                external.assert_not_called()

    def test_invalid_timeout_rejected_before_any_external_command(self):
        for values in (dict(test_timeout=0), dict(test_timeout=301), dict(boot_timeout=0), dict(boot_timeout=601)):
            with self.subTest(values=values), patch.object(runner, "command") as external:
                with self.assertRaises(ValueError):
                    runner.run(arguments(**values))
                external.assert_not_called()

    def test_explicit_software_timeout_caps_and_original_defaults(self):
        runner.validate_timeouts(arguments(test_timeout=300, boot_timeout=600))
        self.assertEqual(runner.parser().get_default("test_timeout"), 90)
        self.assertEqual(runner.parser().get_default("boot_timeout"), 180)

    def test_fresh_package_absent_exit_one_is_allowed(self):
        with patch.object(runner.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "")):
            self.assertFalse(runner.scenario_package_exists(arguments(), APP_ID))

    def test_transport_error_is_not_mistaken_for_absent_package(self):
        with patch.object(runner.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "error: device offline")):
            with self.assertRaises(RuntimeError):
                runner.scenario_package_exists(arguments(), APP_ID)

    def test_only_scenario_packages_can_be_queried(self):
        with patch.object(runner.subprocess, "run") as external:
            with self.assertRaises(ValueError):
                runner.scenario_package_exists(arguments(), "io.github.aj4abinjacob.chikku")
            external.assert_not_called()

    def test_existing_scenario_package_is_detected(self):
        with patch.object(runner.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, f"package:{APP_ID}\n")) as call:
            self.assertTrue(runner.scenario_package_exists(arguments(), APP_ID))
            self.assertIn("-u", call.call_args.args[0])

    def test_prefix_filter_does_not_mistake_test_package_for_application(self):
        with patch.object(runner.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, f"package:{APP_ID}.test\n")):
            self.assertFalse(runner.scenario_package_exists(arguments(), APP_ID))
            self.assertTrue(runner.scenario_package_exists(arguments(), APP_ID + ".test"))

    def test_valid_native_apk_set(self):
        runner.validate_apk_set(apk_set(), APP_ID)

    def test_unversioned_instrumentation_keeps_app_version_guards(self):
        apks = apk_set()
        for name in ("old_test", "new_test"):
            apks[name]["version_code"] = None
            apks[name]["version_name"] = None
        runner.validate_apk_set(apks, APP_ID)
        apks["old"]["version_code"] = None
        with self.assertRaises(ValueError):
            runner.validate_apk_set(apks, APP_ID)

    def test_app_key_or_target_mismatch_rejected(self):
        for name in ("old", "new", "old_test", "new_test"):
            apks = apk_set()
            apks[name]["application_id"] = "io.github.aj4abinjacob.chikku.debug"
            with self.subTest(name=name), self.assertRaises(ValueError):
                runner.validate_apk_set(apks, APP_ID)

    def test_different_certificate_rejected_even_for_instrumentation(self):
        for name in ("old", "new", "old_test", "new_test"):
            apks = apk_set()
            apks[name]["certificate_sha256"] = "b" * 64
            with self.subTest(name=name), self.assertRaises(ValueError):
                runner.validate_apk_set(apks, APP_ID)

    def test_same_or_lower_version_code_rejected(self):
        for version in (99, 100):
            apks = apk_set()
            apks["new"]["version_code"] = version
            with self.subTest(version=version), self.assertRaises(ValueError):
                runner.validate_apk_set(apks, APP_ID)

    def test_debuggable_application_is_rejected(self):
        apks = apk_set()
        apks["new"]["debuggable"] = True
        with self.assertRaises(ValueError):
            runner.validate_apk_set(apks, APP_ID)

    def test_instrumentation_commands_have_no_downgrade_or_clear(self):
        values = runner.instrument_args(arguments(), apk_set()["new"], "verifyRetainedProductionState", widget_id=7301)
        self.assertIn(APP_ID, values)
        self.assertIn(APP_ID + ".test/" + runner.RUNNER, values)
        self.assertNotIn("clear", values)
        self.assertNotIn("uninstall", values)
        self.assertNotIn("-d", values)


class ApkMetadataTest(unittest.TestCase):
    def test_apk_badging_including_debuggable_flag(self):
        output = f"package: name='{APP_ID}' versionCode='101' versionName='acceptance-new' platformBuildVersionName='16'\n"
        self.assertEqual(runner.parse_apk_badging(output)["version_code"], 101)
        self.assertFalse(runner.parse_apk_badging(output)["debuggable"])
        self.assertTrue(runner.parse_apk_badging(output + "application-debuggable\n")["debuggable"])

    def test_actual_agp_unversioned_instrumentation_header(self):
        output = f"package: name='{APP_ID}.test' versionCode='' versionName='' platformBuildVersionName='16'\n"
        parsed = runner.parse_apk_badging(output, allow_unversioned_test=True)
        self.assertEqual(parsed["application_id"], APP_ID + ".test")
        self.assertIsNone(parsed["version_code"])
        self.assertIsNone(parsed["version_name"])
        with self.assertRaises(ValueError):
            runner.parse_apk_badging(output)

    def test_unversioned_or_nonpositive_app_is_rejected_even_with_test_override(self):
        output = f"package: name='{APP_ID}' versionCode='' versionName=''\n"
        for allow in (False, True):
            with self.subTest(allow=allow), self.assertRaises(ValueError):
                runner.parse_apk_badging(output, allow_unversioned_test=allow)
        for code in (0, 2_100_000_001):
            for allow in (False, True):
                with self.subTest(code=code, allow=allow), self.assertRaises(ValueError):
                    runner.parse_apk_badging(f"package: name='{APP_ID}' versionCode='{code}' versionName='acceptance'\n",
                                            allow_unversioned_test=allow)

    def test_invalid_badging_rejected(self):
        with self.assertRaises(ValueError):
            runner.parse_apk_badging("unreadable manifest")

    def test_verified_single_certificate(self):
        self.assertEqual(runner.parse_certificate(f"Signer #1 certificate SHA-256 digest: {CERTIFICATE}\n"), CERTIFICATE)

    def test_missing_or_multiple_signers_rejected(self):
        for output in ("", f"Signer #1 certificate SHA-256 digest: {CERTIFICATE}\nSigner #2 certificate SHA-256 digest: {CERTIFICATE}\n"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.parse_certificate(output)


class NativeEvidenceTest(unittest.TestCase):
    def test_exact_expected_test_passes(self):
        self.assertTrue(runner.exact_test_passed(passing_result(), runner.TEST_CLASS, "verifyRetainedProductionState"))

    def test_actual_zero_test_bootstrap_crash_never_becomes_pass(self):
        parsed = runner._reliability.parse_instrumentation_output(
            "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n")
        self.assertEqual(parsed["accounted_tests"], 0)
        self.assertFalse(runner.exact_test_passed(parsed, runner.SEED_CLASS, "seedHistoricalProductionState"))

    def test_wrong_method_or_class_not_accepted(self):
        result = passing_result()
        self.assertFalse(runner.exact_test_passed(result, runner.SEED_CLASS, "verifyRetainedProductionState"))
        self.assertFalse(runner.exact_test_passed(result, runner.TEST_CLASS, "exportProductionBackup"))

    def test_assumption_skip_and_ignored_are_not_pass(self):
        for outcome in ("ASSUMPTION_SKIPPED", "IGNORED"):
            result = passing_result()
            result["tests"][0]["outcome"] = outcome
            self.assertFalse(runner.exact_test_passed(result, runner.TEST_CLASS, "verifyRetainedProductionState"))

    def test_declared_skip_is_separate_from_failure(self):
        result = passing_result()
        result["status"] = "BLOCKED"
        result["counts"] = {"passed": 0, "failed": 0, "ignored": 0, "assumption_skipped": 1}
        result["tests"][0]["outcome"] = "ASSUMPTION_SKIPPED"
        self.assertTrue(runner.exact_test_skipped(result, runner.TEST_CLASS, "verifyRetainedProductionState"))
        result["tests"][0]["outcome"] = "FAILED"
        self.assertFalse(runner.exact_test_skipped(result, runner.TEST_CLASS, "verifyRetainedProductionState"))

    def test_extra_or_unfinished_tests_are_not_pass(self):
        for field in ("announced_tests", "accounted_tests"):
            result = passing_result()
            result[field] = 2
            self.assertFalse(runner.exact_test_passed(result, runner.TEST_CLASS, "verifyRetainedProductionState"))

    def test_only_single_exact_stage_cut_marker_accepted(self):
        marker = "INSTRUMENTATION_STATUS: chikkuCutMarker=1234:2\nINSTRUMENTATION_STATUS_CODE: 2\n"
        self.assertEqual(runner.parse_cut_marker(marker, 2), 1234)
        self.assertIsNone(runner.parse_cut_marker(marker, 1))
        self.assertIsNone(runner.parse_cut_marker(marker + marker, 2))
        self.assertIsNone(runner.parse_cut_marker("INSTRUMENTATION_STATUS: chikkuCutMarker=0:2\n", 2))


class RebootDeadlineTest(unittest.TestCase):
    def test_pending_wake_command_uses_remaining_deadline_and_stops_polling(self):
        elapsed = [0.0]
        calls = []
        def fake_adb(args, *values, timeout):
            calls.append((values, timeout))
            if values == ("shell", "cat", "/proc/sys/kernel/random/boot_id"):
                return NEW_BOOT_ID
            if values == ("shell", "getprop", "sys.boot_completed"):
                elapsed[0] += 2.0
                return "1"
            elapsed[0] += timeout
            raise subprocess.TimeoutExpired(values, timeout)
        with patch.object(runner.time, "monotonic", side_effect=lambda: elapsed[0]), \
                patch.object(runner.time, "sleep") as sleep, patch.object(runner, "adb", side_effect=fake_adb):
            with self.assertRaises(RuntimeError):
                runner.wait_after_reboot(arguments(boot_timeout=3), OLD_BOOT_ID)
        self.assertEqual([timeout for _, timeout in calls], [3.0, 3.0, 1.0])
        self.assertEqual(elapsed[0], 3.0)
        sleep.assert_not_called()

    def test_boot_poll_sleep_is_clamped_to_remaining_time(self):
        elapsed = [0.0]
        sleeps = []
        def fake_adb(args, *values, timeout):
            self.assertLessEqual(timeout, 1.0 - elapsed[0])
            elapsed[0] += 0.8
            return OLD_BOOT_ID
        def fake_sleep(seconds):
            sleeps.append(seconds)
            elapsed[0] += seconds
        with patch.object(runner.time, "monotonic", side_effect=lambda: elapsed[0]), \
                patch.object(runner.time, "sleep", side_effect=fake_sleep), patch.object(runner, "adb", side_effect=fake_adb):
            with self.assertRaises(RuntimeError):
                runner.wait_after_reboot(arguments(boot_timeout=1), OLD_BOOT_ID)
        self.assertEqual(len(sleeps), 1)
        self.assertAlmostEqual(sleeps[0], 0.2)
        self.assertEqual(elapsed[0], 1.0)

    def test_unchanged_boot_id_never_accepts_prior_boot_completed_or_unlock_flags(self):
        elapsed = [0.0]
        calls = []
        def fake_adb(args, *values, timeout):
            calls.append(values)
            elapsed[0] += 0.2
            if values == ("shell", "cat", "/proc/sys/kernel/random/boot_id"):
                return OLD_BOOT_ID
            return "1" if values[-1] == "sys.boot_completed" else "RUNNING_UNLOCKED"
        with patch.object(runner.time, "monotonic", side_effect=lambda: elapsed[0]), \
                patch.object(runner.time, "sleep", side_effect=lambda seconds: elapsed.__setitem__(0, elapsed[0] + seconds)), \
                patch.object(runner, "adb", side_effect=fake_adb):
            with self.assertRaises(RuntimeError):
                runner.wait_after_reboot(arguments(boot_timeout=1), OLD_BOOT_ID)
        self.assertTrue(calls)
        self.assertTrue(all(call == ("shell", "cat", "/proc/sys/kernel/random/boot_id") for call in calls))

    def test_changed_boot_completed_and_unlocked_returns_verified_new_boot_id(self):
        elapsed = [0.0]
        calls = []
        def fake_adb(args, *values, timeout):
            calls.append(values)
            elapsed[0] += 0.2
            if values == ("shell", "cat", "/proc/sys/kernel/random/boot_id"):
                return NEW_BOOT_ID
            if values == ("shell", "getprop", "sys.boot_completed"):
                return "1"
            if values == ("shell", "am", "get-started-user-state", "0"):
                return "RUNNING_UNLOCKED"
            return ""
        with patch.object(runner.time, "monotonic", side_effect=lambda: elapsed[0]), \
                patch.object(runner.time, "sleep") as sleep, patch.object(runner, "adb", side_effect=fake_adb):
            self.assertEqual(runner.wait_after_reboot(arguments(boot_timeout=3), OLD_BOOT_ID), NEW_BOOT_ID)
        self.assertEqual(len(calls), 5)
        sleep.assert_not_called()

    def test_invalid_previous_boot_id_rejected_without_device_queries(self):
        for value in ("", "not-a-UUID", OLD_BOOT_ID.replace("-", "")):
            with self.subTest(value=value), patch.object(runner, "adb") as adb:
                with self.assertRaises(ValueError):
                    runner.wait_after_reboot(arguments(), value)
                adb.assert_not_called()


class CleanupEvidenceTest(unittest.TestCase):
    def test_native_timeout_is_recorded_before_cleanup_error_and_remains_primary(self):
        report = {"checks": []}
        result = {"timeout": True, "exit_code": -15, "parsed": {"status": "FAIL", "tests": [],
                  "accounted_tests": 0}, "log": "synthetic-native.log"}
        with tempfile.TemporaryDirectory() as directory:
            evidence = runner.EvidenceRun(arguments(), report, Path(directory))
            def failed_cleanup(*args, **kwargs):
                # The primary result must already be durable when remote cleanup begins.
                written = (Path(directory) / "report.json").read_text()
                self.assertIn("synthetic-native.log", written)
                self.assertEqual(report["checks"][0]["parsed"], result["parsed"])
                raise subprocess.TimeoutExpired("synthetic-force-stop", 30)
            with patch.object(runner, "run_instrumentation", return_value=result), \
                    patch.object(runner, "adb", side_effect=failed_cleanup):
                with self.assertRaisesRegex(RuntimeError, "Native assertion did not complete"):
                    evidence.test(apk_set()["new"], "verifyRetainedProductionState")
        self.assertEqual(report["checks"][0]["name"], "verifyRetainedProductionState")
        self.assertEqual(report["checks"][0]["status"], "FAIL")
        self.assertTrue(report["checks"][0]["timeout"])
        self.assertEqual(report["checks"][1]["status"], "FAIL")
        self.assertFalse(report["checks"][1]["remote_quiescence_confirmed"])

    def test_interrupted_preparation_preserves_primary_failure_and_terminates_client_when_remote_cleanup_fails(self):
        for cleanup_error in (RuntimeError("synthetic transport failure"), subprocess.TimeoutExpired("synthetic-force-stop", 30)):
            with self.subTest(cleanup_error=type(cleanup_error).__name__), tempfile.TemporaryDirectory() as directory:
                report = {"checks": []}
                evidence = runner.EvidenceRun(arguments(test_timeout=1), report, Path(directory))
                process = Mock()
                process.poll.return_value = None
                with patch.object(runner.subprocess, "Popen", return_value=process), \
                        patch.object(runner.time, "monotonic", side_effect=[0, 1]), \
                        patch.object(runner, "adb", side_effect=cleanup_error), \
                        patch.object(runner, "terminate_client") as terminate:
                    with self.assertRaisesRegex(RuntimeError, "did not publish its durable pause marker"):
                        evidence.interrupted_restore(apk_set()["new"], 0)
                    terminate.assert_called_once_with(process)
                self.assertEqual(report["checks"][0]["name"], "cut-0-preparation")
                self.assertEqual(report["checks"][0]["status"], "FAIL")
                self.assertEqual(report["checks"][0]["parsed"]["accounted_tests"], 0)
                self.assertEqual(report["checks"][1]["name"], "cut-0-cleanup-force-stop")
                self.assertFalse(report["checks"][1]["remote_quiescence_confirmed"])

    def test_successful_preparation_with_failed_remote_cleanup_never_starts_recovery(self):
        with tempfile.TemporaryDirectory() as directory:
            report = {"checks": []}
            evidence = runner.EvidenceRun(arguments(), report, Path(directory))
            process = Mock()
            # Exercise a completed preparation body whose local client still appears active.
            # An accepted first stop cannot turn a later uncertain cleanup into acceptance.
            process.poll.return_value = None
            process.wait.return_value = 0
            with patch.object(runner.subprocess, "Popen", return_value=process), \
                    patch.object(runner.time, "monotonic", side_effect=[0, 0]), \
                    patch.object(runner, "parse_cut_marker", return_value=1234), \
                    patch.object(evidence, "mutate", return_value=""), \
                    patch.object(runner, "adb", side_effect=RuntimeError("synthetic remote cleanup failure")), \
                    patch.object(runner, "terminate_client") as terminate, \
                    patch.object(evidence, "test") as recover:
                with self.assertRaisesRegex(RuntimeError, "remote quiescence; recovery was not started"):
                    evidence.interrupted_restore(apk_set()["new"], 0)
                terminate.assert_called_once_with(process)
                recover.assert_not_called()
            self.assertEqual(report["checks"][0]["status"], "EXPECTED_INTERRUPTION")
            self.assertEqual(report["checks"][1]["status"], "FAIL")
            self.assertFalse(report["checks"][1]["remote_quiescence_confirmed"])


if __name__ == "__main__":
    unittest.main()

