import importlib.util
from pathlib import Path
import unittest
import contextlib
import io
import json
import subprocess
import tempfile
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "cloud-profile-acceptance.py"
spec = importlib.util.spec_from_file_location("cloud_profile_acceptance", SCRIPT)
assert spec and spec.loader
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
PACKAGE = "io.github.aj4abinjacob.chikku.acceptance.profiles"


class FakeClock:
    def __init__(self):
        self.elapsed = 0.0
        self.waits = []

    def monotonic(self):
        return self.elapsed

    def sleep(self, seconds):
        self.waits.append(seconds)
        self.elapsed += seconds


class FakeClient(runner.Client):
    def __init__(self, overrides=None, expected_avd=None):
        super().__init__("emulator-5554", PACKAGE, expected_avd=expected_avd)
        self.calls = []
        self.guard = False
        self.responses = {
            ("get-state",): "device",
            ("shell", "getprop", "ro.kernel.qemu"): "1",
            ("emu", "avd", "name"): "chikku_acceptance_api36\nOK",
            ("shell", "am", "get-current-user"): "0",
            ("shell", "pm", "list", "users"): "Users:\n UserInfo{0:Owner:c13} running",
            ("shell", "getprop", "ro.build.version.sdk"): "36",
            ("shell", "pm", "path", "--user", "0", PACKAGE): "package:/data/app/synthetic/base.apk",
            ("shell", "pm", "path", "--user", "0", f"{PACKAGE}.test"): "package:/data/app/synthetic-test/base.apk",
        }
        self.responses.update(overrides or {})

    def command(self, *args, timeout=60):
        self.calls.append(args)
        if args in self.responses:
            return self.responses[args]
        if args == ("shell", runner.QUARANTINE_CHECK):
            return "UNSAFE" if self.guard else "CLEAN"
        if args == ("shell", f"mkdir {runner.QUARANTINE_PATH} && echo ARMED"):
            if self.guard:
                raise runner.AcceptanceError("ADB_COMMAND_FAILED")
            self.guard = True
            return "ARMED"
        if args == ("shell", f"rmdir {runner.QUARANTINE_PATH} && echo RELEASED"):
            self.guard = False
            return "RELEASED"
        if args[:3] == ("shell", "pm", "create-user"):
            return "Success: created user id 11"
        if args[:3] == ("shell", "pm", "remove-user"):
            return "Success: removed user"
        if args[:3] == ("shell", "pm", "install-existing"):
            raise runner.AcceptanceError("SYNTHETIC_SETUP_FAILED")
        raise AssertionError("Unexpected synthetic command")


class PassingStageClient(FakeClient):
    def command(self, *args, timeout=60):
        if args[:3] == ("shell", "am", "instrument"):
            self.calls.append(args)
            return (
                f"INSTRUMENTATION_STATUS: class={runner.TEST_CLASS}\n"
                f"INSTRUMENTATION_STATUS: test={runner.TEST_METHOD}\n"
                "INSTRUMENTATION_STATUS: numtests=1\n"
                "INSTRUMENTATION_STATUS_CODE: 1\n"
                f"INSTRUMENTATION_STATUS: class={runner.TEST_CLASS}\n"
                f"INSTRUMENTATION_STATUS: test={runner.TEST_METHOD}\n"
                "INSTRUMENTATION_STATUS: numtests=1\n"
                "INSTRUMENTATION_STATUS_CODE: 0\n"
                "INSTRUMENTATION_CODE: -1\n"
            )
        return super().command(*args, timeout=timeout)


class UncertainCreationClient(FakeClient):
    def __init__(self, mode):
        super().__init__()
        self.mode = mode
        self.creation_name = None
        self.creation_reads = 0
        self.deleted = False

    def command(self, *args, timeout=60):
        if args[:3] == ("shell", "pm", "create-user"):
            self.calls.append(args)
            self.creation_name = args[-1]
            if self.mode == "unconfirmed":
                return "Error: creation response was incomplete"
            raise runner.AcceptanceError("ADB_COMMAND_TIMEOUT")
        if args == ("shell", "pm", "list", "users") and self.creation_name is not None:
            self.calls.append(args)
            self.creation_reads += 1
            owner = "Users:\nUserInfo{0:Synthetic:c13} running"
            if self.deleted or self.mode == "empty" or (self.mode == "committed" and self.creation_reads == 1):
                return owner
            if self.mode == "ambiguous":
                return owner + f"\nUserInfo{{11:{self.creation_name}:30}}\nUserInfo{{12:{self.creation_name}:30}}"
            name = self.creation_name + "_different" if self.mode == "unowned" else self.creation_name
            return owner + f"\nUserInfo{{11:{name}:30}}"
        if args[:3] == ("shell", "pm", "remove-user"):
            self.deleted = True
        return super().command(*args, timeout=timeout)


class CloudProfileAcceptanceTests(unittest.TestCase):
    def test_actual_command_timeout_and_crash_summary_never_claim_clean_device(self):
        for mode in ("timeout", "crash"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as output:
                state = {"guard": False, "home_changed": False}
                calls = []
                defaults = FakeClient().responses
                def remote(argv, **kwargs):
                    args = tuple(argv[3:])
                    calls.append(args)
                    if args == ("shell", runner.QUARANTINE_CHECK):
                        value = "UNSAFE" if state["guard"] else "CLEAN"
                    elif args == ("shell", f"mkdir {runner.QUARANTINE_PATH} && echo ARMED"):
                        state["guard"] = True
                        value = "ARMED"
                    elif args[:3] == ("shell", "am", "instrument"):
                        self.assertTrue(state["guard"])
                        state["home_changed"] = True
                        if mode == "timeout":
                            raise subprocess.TimeoutExpired(argv, kwargs["timeout"])
                        value = "INSTRUMENTATION_RESULT: shortMsg=Process crashed."
                    elif args[:3] == ("shell", "pm", "create-user"):
                        value = "Success: created user id 11"
                    elif args[:3] == ("shell", "pm", "install-existing") or args[:2] == ("shell", "settings"):
                        value = ""
                    elif args[:3] == ("shell", "am", "start-user"):
                        value = "Success: user started"
                    elif args[:3] == ("shell", "am", "get-started-user-state"):
                        value = "RUNNING_UNLOCKED"
                    elif args == ("shell", "getprop", "ro.build.version.sdk"):
                        value = "29"
                    else:
                        value = defaults[args]
                    return subprocess.CompletedProcess(argv, 0, value, "")
                stdout = io.StringIO()
                with patch.object(runner.subprocess, "run", side_effect=remote), \
                        patch.object(runner.sys, "argv", ["runner", "--serial", "emulator-65530", "--package", PACKAGE, "--output", output]), \
                        contextlib.redirect_stdout(stdout):
                    self.assertEqual(runner.main(), 1)
                    # New process/client is rejected even if the user list looks pristine.
                    with self.assertRaisesRegex(runner.AcceptanceError, "UNSAFE_AVD"):
                        runner.Client("emulator-65530", PACKAGE).preflight()
                summary = json.loads(stdout.getvalue())
                self.assertEqual(summary["status"], "FAIL")
                self.assertEqual(summary["cleanup"], "FAIL")
                self.assertEqual(summary["device_reuse"], "UNSAFE_RECREATE_AVD_REQUIRED")
                self.assertEqual(summary["reason"], "ADB_COMMAND_TIMEOUT" if mode == "timeout" else "NATIVE_PROFILE_ASSERTION_FAILED")
                self.assertTrue(state["guard"] and state["home_changed"])
                self.assertFalse(any("remove-user" in c or "force-stop" in c for c in calls))
                self.assertNotIn("shortMsg", stdout.getvalue())

    def test_native_timeout_crash_or_restore_failure_quarantines_without_cleanup_race(self):
        for mode in ("timeout", "crash", "restoration-failure", "host-interrupt"):
            class InterruptedClient(PassingStageClient):
                def command(self, *args, timeout=60):
                    if args[:3] == ("shell", "am", "instrument"):
                        self.calls.append(args)
                        # Simulate native mutation, then no finally (or failed restoration).
                        self.home_changed = self.onboarding_changed = True
                        if mode == "timeout":
                            raise runner.AcceptanceError("ADB_COMMAND_TIMEOUT")
                        if mode == "host-interrupt":
                            raise KeyboardInterrupt()
                        if mode == "crash":
                            return "INSTRUMENTATION_RESULT: shortMsg=Process crashed."
                        return super().command(*args, timeout=timeout).replace("STATUS_CODE: 0", "STATUS_CODE: -2")
                    return super().command(*args, timeout=timeout)
            with self.subTest(mode=mode):
                client = InterruptedClient({("shell", "getprop", "ro.build.version.sdk"): "29"})
                client.preflight()
                client.owned.add(11)
                try:
                    self.assertEqual(client.stage("work-paused", 11)["status"], "FAIL")
                except (runner.AcceptanceError, KeyboardInterrupt):
                    pass
                self.assertTrue(client.native_uncertain)
                self.assertFalse(client.cleanup())
                self.assertTrue(client.guard)
                self.assertTrue(client.home_changed and client.onboarding_changed)
                self.assertFalse(any("remove-user" in call or "force-stop" in call for call in client.calls))
                with self.assertRaisesRegex(runner.AcceptanceError, "UNSAFE_AVD"):
                    client.stage("work-resumed", 11)
                # A fresh host client sees the on-device guard even with only user 0 left.
                fresh = FakeClient()
                fresh.guard = client.guard
                with self.assertRaisesRegex(runner.AcceptanceError, "UNSAFE_AVD"):
                    fresh.preflight()
                self.assertIsNone(fresh.api)

    def test_successful_native_restoration_and_profile_cleanup_release_guard(self):
        client = PassingStageClient()
        client.preflight()
        client.owned.add(11)
        self.assertEqual(client.stage("work-paused", 11)["status"], "PASS")
        self.assertTrue(client.guard)
        self.assertFalse(client.native_uncertain)
        self.assertTrue(client.cleanup())
        self.assertFalse(client.guard)
        self.assertEqual(client.owned, set())
        client.preflight()

    def test_host_crash_after_arming_remains_quarantined_for_new_invocation(self):
        client = FakeClient()
        client.preflight()
        client.arm_guard()
        fresh = FakeClient()
        fresh.guard = client.guard
        with self.assertRaisesRegex(runner.AcceptanceError, "UNSAFE_AVD"):
            fresh.preflight()
        self.assertTrue(client.guard)

    def test_guard_failure_prevents_profile_or_native_mutations(self):
        client = PassingStageClient({("shell", f"mkdir {runner.QUARANTINE_PATH} && echo ARMED"): ""})
        client.preflight()
        client.owned.add(11)
        with self.assertRaises(runner.AcceptanceError):
            client.create("managed")
        with self.assertRaises(runner.AcceptanceError):
            client.stage("work-active", 11)
        self.assertFalse(any("create-user" in c or "instrument" in c for c in client.calls))
        self.assertFalse(client.cleanup())

    def test_guard_release_failure_cannot_report_cleanup_pass(self):
        client = PassingStageClient({("shell", f"rmdir {runner.QUARANTINE_PATH} && echo RELEASED"): ""})
        client.preflight()
        client.owned.add(11)
        client.stage("work-active", 11)
        self.assertFalse(client.cleanup())
        self.assertTrue(client.guard)

    def test_only_exact_acceptance_id_and_explicit_emulator_transport_are_accepted(self):
        runner.validate_arguments("emulator-5554", PACKAGE)
        for package in ("io.github.aj4abinjacob.chikku", "io.github.aj4abinjacob.chikku.debug",
                        PACKAGE + ".debug", PACKAGE + ";pm clear", PACKAGE + "\n", PACKAGE.upper()):
            with self.subTest(package=package), self.assertRaises(runner.AcceptanceError):
                runner.validate_arguments("emulator-5554", package)
        for serial in ("R58PERSONAL", "", "localhost:5555", "emulator-5554\n"):
            with self.subTest(serial=serial), self.assertRaises(runner.AcceptanceError):
                runner.validate_arguments(serial, PACKAGE)

    def test_explicit_disposable_name_requires_an_exact_transport_match(self):
        name = "chikku_play_preparation_20261015"
        client = FakeClient({("emu", "avd", "name"): name + "\nOK"}, expected_avd=name)
        self.assertEqual(client.preflight(), 36)
        for actual in ("chikku_acceptance_api36", "chikku_play_preparation_other", name + "\nextra"):
            with self.subTest(actual=actual):
                client = FakeClient({("emu", "avd", "name"): actual + "\nOK"}, expected_avd=name)
                with self.assertRaisesRegex(runner.AcceptanceError, "DEDICATED_ACCEPTANCE_AVD"):
                    client.preflight()
                self.assertFalse(any("create-user" in c or "instrument" in c for c in client.calls))

    def test_preparation_name_is_still_rejected_without_an_explicit_pin(self):
        client = FakeClient({("emu", "avd", "name"): "chikku_play_preparation_20261015\nOK"})
        with self.assertRaisesRegex(runner.AcceptanceError, "DEDICATED_ACCEPTANCE_AVD"):
            client.preflight()

    def test_explicit_name_retains_qemu_primary_profile_and_quarantine_gates(self):
        name = "chikku_play_preparation_20261015"
        cases = [
            ({("shell", "getprop", "ro.kernel.qemu"): "0"}, "EMULATOR_REQUIRED"),
            ({("shell", "am", "get-current-user"): "11"}, "PERSONAL_USER_ZERO"),
            ({("shell", "pm", "list", "users"): "Users:\n UserInfo{0:Owner:c13} running\n UserInfo{11:Synthetic:30}"}, "FRESH_AVD"),
            ({("shell", runner.QUARANTINE_CHECK): "UNSAFE"}, "UNSAFE_AVD"),
        ]
        for overrides, error in cases:
            with self.subTest(error=error):
                client = FakeClient({("emu", "avd", "name"): name + "\nOK", **overrides}, expected_avd=name)
                with self.assertRaisesRegex(runner.AcceptanceError, error):
                    client.preflight()
                self.assertIsNone(client.api)
                self.assertFalse(any("create-user" in c or "instrument" in c for c in client.calls))

    def test_explicit_disposable_name_rejects_ambiguous_or_non_chikku_values(self):
        for name in ("", "personal_phone", "chikku_test\nOK", "chikku_../other", "chikku_" + "x" * 81):
            with self.subTest(name=name), self.assertRaisesRegex(runner.AcceptanceError, "EXPLICIT_CHIKKU_AVD_NAME"):
                FakeClient(expected_avd=name)

    def test_valid_preflight_has_no_mutations(self):
        client = FakeClient()
        self.assertEqual(client.preflight(), 36)
        self.assertFalse(any("create-user" in c or "remove-user" in c or "install-existing" in c for c in client.calls))

    def test_personal_device_and_nondedicated_avd_are_rejected_before_mutation(self):
        for overrides in (
            {("shell", "getprop", "ro.kernel.qemu"): "0"},
            {("emu", "avd", "name"): "developer_daily_phone\nOK"},
            {("emu", "avd", "name"): "chikku_acceptance_api36"},
            {("shell", "am", "get-current-user"): "10"},
            {("shell", "pm", "list", "users"): "Users:\nUserInfo{0:Owner:c13} running\nUserInfo{10:Existing:30}"},
            {("shell", "getprop", "ro.build.version.sdk"): "28"},
            {("shell", "pm", "path", "--user", "0", f"{PACKAGE}.test"): ""},
        ):
            with self.subTest(overrides=overrides):
                client = FakeClient(overrides)
                with self.assertRaises(runner.AcceptanceError):
                    client.preflight()
                self.assertFalse(any("create-user" in c or "remove-user" in c for c in client.calls))

    def test_user_list_parser_rejects_ambiguous_partial_and_duplicate_output(self):
        self.assertEqual(runner.parse_user_ids("Users:\nUserInfo{0:Synthetic:c13} running"), {0})
        for text in ("", "Error: unavailable", "Users:", "Users:\nUserInfo{0:Unclosed:c13",
                     "Users:\nUserInfo{0:A:c13}\nUserInfo{0:B:c13}", "Users:\nUserInfo{0:A:c13}\nunknown"):
            with self.subTest(text=text), self.assertRaises(runner.AcceptanceError):
                runner.parse_user_ids(text)

    def test_creation_confirmation_cannot_select_primary_user(self):
        self.assertEqual(runner.parse_created_user("Success: created user id 11\n"), 11)
        for text in ("Success: created user id 0", "Success: created user id -1",
                     "Error: created user id 11", "Success: created user id 11\nError: setup failed"):
            with self.subTest(text=text), self.assertRaises(runner.AcceptanceError):
                runner.parse_created_user(text)

    def test_created_profile_is_registered_before_setup_failure_and_cleanup_is_scoped(self):
        client = FakeClient()
        client.preflight()
        with self.assertRaises(runner.AcceptanceError):
            client.create("managed")
        self.assertEqual(client.owned, {11})
        self.assertTrue(client.cleanup())
        self.assertEqual(client.owned, set())
        removals = [call for call in client.calls if "remove-user" in call]
        self.assertEqual(removals, [("shell", "pm", "remove-user", "11")])

    def test_unowned_profile_removal_never_reaches_adb(self):
        client = FakeClient()
        for profile in (0, 10, 11):
            with self.assertRaises(runner.AcceptanceError):
                client.remove(profile)
        self.assertEqual(client.calls, [])

    def test_zero_exit_profile_start_without_real_unlock_cannot_count_as_setup(self):
        for started, state in (("", "RUNNING_UNLOCKED"), ("Success: user started", "RUNNING_LOCKED"),
                               ("Success: user started", "User is not started: 11")):
            class SetupClient(FakeClient):
                def command(self, *args, timeout=60):
                    if args[:3] == ("shell", "pm", "install-existing") or args[:2] == ("shell", "settings"):
                        self.calls.append(args)
                        return ""
                    if args[:3] == ("shell", "am", "start-user"):
                        self.calls.append(args)
                        return started
                    if args[:3] == ("shell", "am", "get-started-user-state"):
                        self.calls.append(args)
                        return state
                    return super().command(*args, timeout=timeout)
            with self.subTest(started=started, state=state):
                client = SetupClient()
                client.preflight()
                with self.assertRaises(runner.AcceptanceError):
                    client.create("private")
                self.assertEqual(client.owned, {11})
                self.assertTrue(client.cleanup())

    def test_stopped_private_stage_requires_confirmed_owned_user_stop(self):
        client = FakeClient({("shell", "am", "stop-user", "-f", "-w", "11"): "",
                             ("shell", "am", "is-user-stopped", "11"): "false"})
        with self.assertRaises(runner.AcceptanceError):
            client.stop(11)
        self.assertEqual(client.calls, [])
        client.owned.add(11)
        with self.assertRaises(runner.AcceptanceError):
            client.stop(11)
        client.responses[("shell", "am", "is-user-stopped", "11")] = "true"
        client.stop(11)

    def test_failed_cleanup_is_not_green_and_preserves_ownership_for_diagnosis(self):
        client = FakeClient({("shell", "pm", "remove-user", "11"): "Error: cannot remove user"})
        client.preflight()
        client.owned.add(11)
        self.assertFalse(client.cleanup())
        self.assertEqual(client.owned, {11})

    def test_async_removal_retains_ownership_until_fresh_id_disappears(self):
        class RemovingClient(FakeClient):
            def __init__(self):
                super().__init__()
                self.snapshots = iter(("Users:\nUserInfo{0:Synthetic:c13} running\nUserInfo{11:Synthetic:30}",
                                       "Users:\nUserInfo{0:Synthetic:c13} running\nUserInfo{11:Synthetic:30}",
                                       "Users:\nUserInfo{0:Synthetic:c13} running"))
                self.reads = 0

            def command(self, *args, timeout=60):
                if args == ("shell", "pm", "list", "users"):
                    self.calls.append(args)
                    self.reads += 1
                    # A queued removal cannot authorize the removed-stage assertion yet.
                    if self.owned != {11} or self.removed:
                        raise AssertionError("Ownership was discarded before absence confirmation")
                    return next(self.snapshots)
                return super().command(*args, timeout=timeout)

        client = RemovingClient()
        client.owned.add(11)
        clock = FakeClock()
        with patch.object(runner.time, "monotonic", clock.monotonic), patch.object(runner.time, "sleep", clock.sleep):
            client.remove(11)
        self.assertEqual(client.reads, 3)
        self.assertEqual(len(clock.waits), 2)
        self.assertEqual(client.owned, set())
        self.assertEqual(client.removed, {11})
        self.assertEqual([call for call in client.calls if "remove-user" in call], [("shell", "pm", "remove-user", "11")])

    def test_acknowledged_removal_timeout_fails_cleanup_and_retains_ownership(self):
        client = FakeClient()
        client.preflight()
        client.responses[("shell", "pm", "list", "users")] = "Users:\nUserInfo{0:Synthetic:c13} running\nUserInfo{11:Synthetic:30}"
        client.owned.add(11)
        clock = FakeClock()
        with patch.object(runner.time, "monotonic", clock.monotonic), patch.object(runner.time, "sleep", clock.sleep):
            self.assertFalse(client.cleanup())
        self.assertEqual(clock.elapsed, runner.REMOVAL_TIMEOUT_SECONDS)
        self.assertEqual(client.owned, {11})
        self.assertEqual(client.removed, set())
        self.assertTrue(clock.waits)

    def test_unparseable_or_missing_primary_user_list_cannot_confirm_removal(self):
        for users in ("Users:\nunknown", "Users:\nUserInfo{12:Synthetic:30}"):
            with self.subTest(users=users):
                client = FakeClient({("shell", "pm", "list", "users"): users})
                client.owned.add(11)
                with self.assertRaises(runner.AcceptanceError):
                    client.remove(11)
                self.assertEqual(client.owned, {11})
                self.assertEqual(client.removed, set())

    def test_creation_cannot_mutate_before_successful_preflight(self):
        client = FakeClient()
        with self.assertRaises(runner.AcceptanceError):
            client.create("managed")
        self.assertEqual(client.calls, [])
        self.assertEqual(client.pending_creation_names, set())

    def test_committed_timeout_and_unconfirmed_response_reconcile_exact_nonce_before_cleanup(self):
        for mode in ("committed", "unconfirmed"):
            with self.subTest(mode=mode):
                client = UncertainCreationClient(mode)
                client.preflight()
                clock = FakeClock()
                with patch.object(runner.time, "monotonic", clock.monotonic), patch.object(runner.time, "sleep", clock.sleep):
                    with self.assertRaises(runner.AcceptanceError):
                        client.create("managed")
                    self.assertEqual(client.owned, {11})
                    self.assertEqual(client.pending_creation_names, set())
                    self.assertFalse(any("install-existing" in call for call in client.calls))
                    self.assertTrue(client.cleanup())
                self.assertEqual(client.owned, set())
                self.assertEqual(client.removed, {11})
                self.assertEqual([call for call in client.calls if "remove-user" in call], [("shell", "pm", "remove-user", "11")])
                if mode == "committed":
                    self.assertEqual(clock.waits, [runner.REMOVAL_POLL_SECONDS])

    def test_ambiguous_or_different_nonce_creation_is_not_guessed_or_deleted(self):
        for mode in ("ambiguous", "unowned"):
            with self.subTest(mode=mode):
                client = UncertainCreationClient(mode)
                client.preflight()
                clock = FakeClock()
                with patch.object(runner.time, "monotonic", clock.monotonic), patch.object(runner.time, "sleep", clock.sleep):
                    with self.assertRaises(runner.AcceptanceError):
                        client.create("managed")
                    self.assertEqual(client.owned, set())
                    self.assertEqual(client.pending_creation_names, {client.creation_name})
                    self.assertFalse(client.cleanup())
                self.assertFalse(any("remove-user" in call for call in client.calls))
                self.assertEqual(client.pending_creation_names, {client.creation_name})

    def test_unconfirmed_creation_stays_unclean_even_when_snapshot_shows_only_primary_user(self):
        client = UncertainCreationClient("empty")
        client.preflight()
        clock = FakeClock()
        with patch.object(runner.time, "monotonic", clock.monotonic), patch.object(runner.time, "sleep", clock.sleep):
            with self.assertRaises(runner.AcceptanceError):
                client.create("managed")
            self.assertFalse(client.cleanup())
        self.assertEqual(client.owned, set())
        self.assertEqual(client.pending_creation_names, {client.creation_name})
        self.assertFalse(any("remove-user" in call for call in client.calls))

    def test_final_cleanup_requires_exact_preflight_ids_without_deleting_unowned_residue(self):
        client = FakeClient()
        client.preflight()
        client.responses[("shell", "pm", "list", "users")] = "Users:\nUserInfo{0:Synthetic:c13} running\nUserInfo{12:Synthetic:30}"
        self.assertFalse(client.cleanup())
        self.assertFalse(any("remove-user" in call for call in client.calls))

    def test_instrumentation_assumptions_and_incomplete_results_never_become_acceptance_pass(self):
        output = (
            f"INSTRUMENTATION_STATUS: class={runner.TEST_CLASS}\n"
            f"INSTRUMENTATION_STATUS: test={runner.TEST_METHOD}\n"
            "INSTRUMENTATION_STATUS: numtests=1\n"
            "INSTRUMENTATION_STATUS_CODE: 1\n"
            f"INSTRUMENTATION_STATUS: class={runner.TEST_CLASS}\n"
            f"INSTRUMENTATION_STATUS: test={runner.TEST_METHOD}\n"
            "INSTRUMENTATION_STATUS: numtests=1\n"
            "INSTRUMENTATION_STATUS: stack=synthetic-private-details-must-not-be-retained\n"
            "INSTRUMENTATION_STATUS_CODE: 0\n"
            "INSTRUMENTATION_CODE: -1\n"
        )
        result = runner.confirmed_native_pass(output)
        self.assertEqual(result["status"], "PASS")
        self.assertNotIn("synthetic-private-details", str(result))
        for attempt in (output.replace("CODE: 0", "CODE: -4"), output.replace("CODE: 0", "CODE: -2"),
                        output.replace("INSTRUMENTATION_CODE: -1", ""), "OK (1 test)",
                        output.replace(runner.TEST_CLASS, "synthetic.UnrelatedTest"),
                        output.replace(runner.TEST_METHOD, "unrelatedMethod")):
            with self.subTest(attempt=attempt):
                self.assertEqual(runner.confirmed_native_pass(attempt)["status"], "FAIL")

    def test_same_serial_cannot_run_two_acceptance_workflows(self):
        with runner.device_lock("emulator-65532"):
            with self.assertRaises(runner.AcceptanceError):
                with runner.device_lock("emulator-65532"):
                    self.fail("Concurrent lock unexpectedly acquired")
        with runner.device_lock("emulator-65532"):
            pass

    def test_temporary_home_setup_is_explicit_only_for_verified_api29_through34(self):
        for api, expected_flag in ((29, True), (34, True), (35, False), (36, False)):
            with self.subTest(api=api):
                client = PassingStageClient({("shell", "getprop", "ro.build.version.sdk"): str(api)})
                client.preflight()
                client.owned.add(11)
                self.assertEqual(client.stage("work-paused", 11)["status"], "PASS")
                instrument = client.calls[-1]
                self.assertEqual("cloudProfileLauncherSetup" in instrument, expected_flag)
                if expected_flag:
                    index = instrument.index("cloudProfileLauncherSetup")
                    self.assertEqual(instrument[index - 1:index + 2], ("-e", "cloudProfileLauncherSetup", "true"))
                self.assertFalse(any("set-home-activity" in call for call in client.calls))

    def test_native_setup_cannot_be_requested_before_successful_or_after_failed_preflight(self):
        client = PassingStageClient()
        client.owned.add(11)
        with self.assertRaises(runner.AcceptanceError):
            client.stage("work-paused", 11)
        self.assertEqual(client.calls, [])
        client.preflight()
        client.responses[("shell", "getprop", "ro.kernel.qemu")] = "0"
        with self.assertRaises(runner.AcceptanceError):
            client.preflight()
        with self.assertRaises(runner.AcceptanceError):
            client.stage("work-paused", 11)
        self.assertFalse(any("instrument" in call for call in client.calls))

    def test_native_stage_requires_owned_profile_and_own_confirmed_removal(self):
        client = PassingStageClient()
        client.preflight()
        before = len(client.calls)
        for stage, profile in (("work-paused", 11), ("work-active", 0), ("invalid", 11), ("work-removed", 11)):
            with self.subTest(stage=stage, profile=profile), self.assertRaises(runner.AcceptanceError):
                client.stage(stage, profile)
        self.assertEqual(len(client.calls), before)
        client.owned.add(11)
        client.remove(11)
        self.assertEqual(client.stage("work-removed", 11)["status"], "PASS")


if __name__ == "__main__":
    unittest.main()

