import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/run-reliability.py"
SPEC = importlib.util.spec_from_file_location("run_reliability", SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runner)


def status_block(test: str, code: int, *, count: int = 1, stream: str = "") -> str:
    return (f"INSTRUMENTATION_STATUS: class=sample.ExampleTest\n"
            f"INSTRUMENTATION_STATUS: test={test}\n"
            f"INSTRUMENTATION_STATUS: numtests={count}\n"
            f"INSTRUMENTATION_STATUS: stream={stream}\n"
            f"INSTRUMENTATION_STATUS_CODE: {code}\n")


class InstrumentationParserTest(unittest.TestCase):
    def test_quarantined_profile_device_cannot_start_reliability_work(self):
        with patch.object(runner, "adb_read", side_effect=["device", "1", "UNSAFE"]):
            with self.assertRaisesRegex(RuntimeError, "Unsafe profile acceptance"):
                runner.device_metadata("adb", "emulator-5554")

    def test_captured_native_success_log_is_pass(self):
        output = (ROOT / "scripts/tests/fixtures/native-parser-reference.log").read_text()
        parsed = runner.parse_instrumentation_output(output)
        self.assertEqual(parsed["instrumentation_code"], -1)
        self.assertEqual(parsed["counts"]["passed"], 1)
        self.assertEqual(parsed["status"], "PASS")

    def test_started_test_without_terminal_status_fails_even_with_success_final_code(self):
        output = status_block("crashed", 1) + "INSTRUMENTATION_CODE: -1\n"
        parsed = runner.parse_instrumentation_output(output)
        self.assertEqual(parsed["unfinished_tests"], {"sample.ExampleTest#crashed": 1})
        self.assertEqual(parsed["status"], "FAIL")

    def test_assertion_failure_is_failure_even_if_shell_exits_zero(self):
        output = status_block("assertionFails", 1) + status_block("assertionFails", -2, stream="AssertionError")
        output += "INSTRUMENTATION_CODE: 0\n"
        parsed = runner.parse_instrumentation_output(output)
        self.assertEqual(parsed["counts"]["failed"], 1)
        self.assertEqual(parsed["status"], "FAIL")

    def test_error_status_is_failure(self):
        output = status_block("throws", 1) + status_block("throws", -1, stream="IllegalStateException")
        output += "INSTRUMENTATION_CODE: 0\n"
        self.assertEqual(runner.parse_instrumentation_output(output)["status"], "FAIL")

    def test_assumption_skip_and_ignored_test_are_blocked_when_all_skipped(self):
        output = status_block("assumption", 1, count=2) + status_block("assumption", -4, count=2)
        output += status_block("ignored", 1, count=2) + status_block("ignored", -3, count=2)
        output += "INSTRUMENTATION_CODE: -1\n"
        parsed = runner.parse_instrumentation_output(output)
        self.assertEqual(parsed["counts"]["assumption_skipped"], 1)
        self.assertEqual(parsed["counts"]["ignored"], 1)
        self.assertEqual(parsed["status"], "BLOCKED")

    def test_pass_mixed_with_assumption_skip_is_pass(self):
        output = status_block("works", 1, count=2) + status_block("works", 0, count=2)
        output += status_block("unavailable", 1, count=2) + status_block("unavailable", -4, count=2)
        output += "INSTRUMENTATION_CODE: -1\n"
        parsed = runner.parse_instrumentation_output(output)
        self.assertEqual(parsed["accounted_tests"], 2)
        self.assertEqual(parsed["counts"]["passed"], 1)
        self.assertEqual(parsed["status"], "PASS")

    def test_missing_final_code_and_unaccounted_numtests_fail(self):
        no_final = status_block("works", 1) + status_block("works", 0)
        self.assertEqual(runner.parse_instrumentation_output(no_final)["status"], "FAIL")
        short_run = status_block("works", 1, count=2) + status_block("works", 0, count=2)
        short_run += "INSTRUMENTATION_CODE: -1\n"
        self.assertEqual(runner.parse_instrumentation_output(short_run)["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()

