"""Tests for scripts/ci/quality_gates.py (plan C11): the runner that executes every CI quality gate.

Gates are injected as fake commands or through a recording runner, so no real gate (and no network) is involved.
"""
import io
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import CI_DIR, ROOT, clean_env, load_script, run_python, write_bytes  # noqa: E402

PY = sys.executable
C11_NAMES = ["ac-audit", "redact-evidence", "hook-tests", "hook-line-endings", "ci-script-tests", "oracle-fixture",
             "actionlint", "ci-schema"]
# The static gates of C11, as the arguments after the interpreter.
C11_STATIC = {
    "ac-audit": [["scripts/ac_audit.py", "--offline", "--no-write"]],
    "redact-evidence": [["scripts/redact_evidence.py", "--check", "sprint-contracts", "specs/reviews",
                         "docs/fix-loops"]],
    "hook-tests": [[".claude/hooks/tests/test_hooks.py"]],
    "hook-line-endings": [["scripts/ci/check_line_endings.py", ".claude/hooks/*.sh"]],
    "ci-script-tests": [["-m", "unittest", "discover", "-s", "scripts/tests", "-v"]],
    "oracle-fixture": [["scripts/gen_refund_oracle_cases.py", "--check"]],
}


class GateTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gates_module = load_script("quality_gates")

    def setUp(self):
        work = tempfile.TemporaryDirectory(prefix="pf-ci-gates-")
        self.addCleanup(work.cleanup)
        self.root = Path(work.name)
        write_bytes(self.root, ".github/workflows/ci.yml", b"name: CI\n")
        write_bytes(self.root, ".github/workflows/linux-snapshots.yml", b"name: Linux snapshots\n")
        write_bytes(self.root, ".gitlab-ci.yml", b"stages: [build]\n")

    def fake_tool(self, name):
        return f"/fake/tools/{name}"

    def build(self, resolve_tool=None, module_available=None):
        kwargs = {"resolve_tool": resolve_tool or self.fake_tool, "repo_root": self.root}
        if module_available is not None:
            kwargs["module_available"] = module_available
        return self.gates_module.build_gates(**kwargs)

    def run_main(self, argv, gates=None, runner=None):
        stream = io.StringIO()
        code = self.gates_module.main(argv, gates=gates, runner=runner, stream=stream)
        return code, stream.getvalue()

    @staticmethod
    def table_rows(output):
        """The data rows of the summary table as lists of cells."""
        rows = []
        for line in output.splitlines():
            if line.startswith("|") and not line.startswith("|---"):
                cells = [c.strip() for c in line.strip().strip("|").split("|")]
                if cells[0] != "gate":
                    rows.append(cells)
        return rows


class GateDefinitionTests(GateTestCase):
    def test_gate_list_and_order_equal_c11(self):
        """NFR-09.6: the quality gates are the eight of plan C11, in that order."""
        gates = self.build()
        self.assertEqual([g.name for g in gates], C11_NAMES)

    def test_static_gate_commands_equal_c11(self):
        """NFR-09.6: each static gate runs the C11 command with the current interpreter."""
        gates = {g.name: g for g in self.build()}
        for name, expected in C11_STATIC.items():
            with self.subTest(gate=name):
                argvs = gates[name].commands()
                self.assertEqual([a[1:] for a in argvs], expected)
                self.assertTrue(all(a[0] == PY for a in argvs))

    def test_actionlint_gate_uses_the_fetched_tools_on_the_workflows(self):
        """NFR-09.6: actionlint runs with the fetched shellcheck on every file under .github/workflows/."""
        gates = {g.name: g for g in self.build()}
        (argv,) = gates["actionlint"].commands()
        self.assertEqual(argv, ["/fake/tools/actionlint", "-shellcheck", "/fake/tools/shellcheck",
                                ".github/workflows/ci.yml", ".github/workflows/linux-snapshots.yml"])

    def test_schema_gate_checks_both_platforms_with_the_c11_schemas(self):
        """NFR-09.6: ci-schema runs check-jsonschema on the GitHub workflows and on .gitlab-ci.yml."""
        gates = {g.name: g for g in self.build(module_available=lambda name: True)}
        github, gitlab = gates["ci-schema"].commands()
        self.assertEqual(github[1:], ["-m", "check_jsonschema", "--builtin-schema", "vendor.github-workflows",
                                      ".github/workflows/ci.yml", ".github/workflows/linux-snapshots.yml"])
        self.assertEqual(gitlab[1:], ["-m", "check_jsonschema", "--builtin-schema", "vendor.gitlab-ci",
                                      "--data-transform", "gitlab-ci", ".gitlab-ci.yml"])

    def test_schema_gate_without_check_jsonschema_is_unavailable(self):
        """NFR-09.6: an environment without check-jsonschema fails the gate instead of skipping it."""
        gates = {g.name: g for g in self.build(module_available=lambda name: False)}
        with self.assertRaises(self.gates_module.GateUnavailable):
            gates["ci-schema"].commands()

    def test_actionlint_gate_without_workflows_is_unavailable(self):
        """NFR-09.6: with no workflow file to lint the actionlint gate fails instead of passing vacuously."""
        for path in (self.root / ".github" / "workflows").iterdir():
            path.unlink()
        gates = {g.name: g for g in self.build()}
        with self.assertRaises(self.gates_module.GateUnavailable):
            gates["actionlint"].commands()


class GateRunTests(GateTestCase):
    def recording_runner(self, failing=(), calls=None):
        calls = calls if calls is not None else []

        def runner(argv, cwd):
            calls.append((list(argv), cwd))
            return (1, "boom\n") if any(token in argv for token in failing) else (0, "fine\n")
        return runner, calls

    def test_all_gates_passing_exits_zero_and_runs_every_gate_in_order(self):
        """NFR-09.6: with every gate passing the exit code is 0 and all eight gates ran, in the C11 order."""
        runner, calls = self.recording_runner()
        code, output = self.run_main([], gates=self.build(module_available=lambda n: True), runner=runner)
        self.assertEqual(code, 0, output)
        self.assertEqual(len(calls), 9)  # ci-schema runs two commands
        self.assertEqual([r[0] for r in self.table_rows(output)], C11_NAMES)
        self.assertTrue(all(r[-1] == "PASS" for r in self.table_rows(output)))
        self.assertTrue(all(cwd == self.root for _, cwd in calls))

    def test_a_single_failure_exits_one_after_every_later_gate_has_run(self):
        """NFR-09.6: a failing gate never stops the run; later gates still execute and are reported."""
        runner, calls = self.recording_runner(failing=(".claude/hooks/tests/test_hooks.py",))
        code, output = self.run_main([], gates=self.build(module_available=lambda n: True), runner=runner)
        self.assertEqual(code, 1, output)
        rows = {r[0]: r for r in self.table_rows(output)}
        self.assertEqual(rows["hook-tests"][-1], "FAIL")
        self.assertEqual(rows["hook-tests"][-2], "1")
        for later in C11_NAMES[3:]:
            self.assertEqual(rows[later][-1], "PASS", f"{later} should still have run")
        self.assertEqual(len(calls), 9)

    def test_a_missing_tool_fails_the_gate_and_the_run(self):
        """NFR-09.6: a tool that cannot be resolved is a failure, never a skip, and later gates still run."""
        def resolve(name):
            if name == "actionlint":
                raise self.gates_module.MissingTool("actionlint could not be fetched")
            return self.fake_tool(name)
        runner, calls = self.recording_runner()
        code, output = self.run_main([], gates=self.build(resolve_tool=resolve, module_available=lambda n: True),
                                     runner=runner)
        self.assertEqual(code, 1, output)
        rows = {r[0]: r for r in self.table_rows(output)}
        self.assertEqual(rows["actionlint"][-1], "FAIL")
        self.assertEqual(rows["actionlint"][-2], "127")
        self.assertEqual(rows["ci-schema"][-1], "PASS")
        self.assertIn("could not be fetched", output)

    def test_every_gate_is_reported_in_a_summary_table_with_command_and_exit_code(self):
        """NFR-09.6: the summary table has the columns gate, command, exit and result for every gate."""
        runner, _ = self.recording_runner(failing=("scripts/ac_audit.py",))
        code, output = self.run_main([], gates=self.build(module_available=lambda n: True), runner=runner)
        header = next(line for line in output.splitlines() if line.startswith("| gate"))
        self.assertEqual([c.strip() for c in header.strip().strip("|").split("|")],
                         ["gate", "command", "exit", "result"])
        rows = {r[0]: r for r in self.table_rows(output)}
        self.assertIn("scripts/ac_audit.py --offline --no-write", rows["ac-audit"][1])
        self.assertEqual(rows["ac-audit"][2:], ["1", "FAIL"])
        self.assertEqual(rows["redact-evidence"][2:], ["0", "PASS"])
        self.assertEqual(code, 1)

    def test_real_subprocess_exit_codes_are_captured(self):
        """NFR-09.6: with the default runner a command's real exit status and output reach the table."""
        gates = [
            self.gates_module.Gate("passes", [[PY, "-c", "print('hello from the gate')"]]),
            self.gates_module.Gate("fails", [[PY, "-c", "import sys; print('bad'); sys.exit(3)"]]),
            self.gates_module.Gate("runs-last", [[PY, "-c", "print('still ran')"]]),
        ]
        code, output = self.run_main([], gates=gates)
        self.assertEqual(code, 1, output)
        rows = {r[0]: r for r in self.table_rows(output)}
        self.assertEqual(rows["fails"][2:], ["3", "FAIL"])
        self.assertEqual(rows["passes"][2:], ["0", "PASS"])
        self.assertEqual(rows["runs-last"][2:], ["0", "PASS"])
        self.assertIn("hello from the gate", output)
        self.assertIn("still ran", output)

    def test_a_gate_with_several_commands_fails_if_any_command_fails(self):
        """NFR-09.6: a gate made of two commands runs both and fails when either exits non-zero."""
        gate = self.gates_module.Gate("two", [[PY, "-c", "raise SystemExit(0)"], [PY, "-c", "raise SystemExit(4)"],
                                              [PY, "-c", "print('third')"]])
        code, output = self.run_main([], gates=[gate])
        self.assertEqual(code, 1, output)
        self.assertEqual(self.table_rows(output)[0][2:], ["4", "FAIL"])
        self.assertIn("third", output)

    def test_list_prints_the_gates_without_running_them(self):
        """NFR-09.6: --list prints every gate name in order and runs nothing."""
        runner, calls = self.recording_runner()
        code, output = self.run_main(["--list"], gates=self.build(module_available=lambda n: True), runner=runner)
        self.assertEqual(code, 0)
        self.assertEqual([line.split()[0] for line in output.splitlines() if line.strip()], C11_NAMES)
        self.assertEqual(calls, [])

    def test_only_runs_the_named_gates(self):
        """NFR-09.6: --only selects gates by name and unknown names are a usage error (exit 2)."""
        runner, calls = self.recording_runner()
        gates = self.build(module_available=lambda n: True)
        code, output = self.run_main(["--only", "hook-tests"], gates=gates, runner=runner)
        self.assertEqual(code, 0, output)
        self.assertEqual([r[0] for r in self.table_rows(output)], ["hook-tests"])
        self.assertEqual(len(calls), 1)
        code, _ = self.run_main(["--only", "no-such-gate"], gates=gates, runner=runner)
        self.assertEqual(code, 2)

    def test_report_file_holds_the_summary_table(self):
        """NFR-09.6: --report writes the same table to a file, for the quality-gates-report artifact."""
        runner, _ = self.recording_runner()
        report = self.root / "out" / "quality-gates.md"
        code, _ = self.run_main(["--report", str(report)], gates=self.build(module_available=lambda n: True),
                                runner=runner)
        self.assertEqual(code, 0)
        text = report.read_text(encoding="utf-8")
        for name in C11_NAMES:
            self.assertIn(name, text)

    def test_api_key_is_not_passed_to_the_gates(self):
        """NFR-09.6: the gate subprocesses run without ANTHROPIC_API_KEY, which only the review step may see."""
        env = self.gates_module.gate_environment({"ANTHROPIC_API_KEY": "k", "PATH": "p"})
        self.assertNotIn("ANTHROPIC_API_KEY", env)
        self.assertEqual(env["PATH"], "p")
        self.assertEqual(env["PYTHONUTF8"], "1")

    def test_command_line_entry_point_lists_the_gates(self):
        """NFR-09.6: `python scripts/ci/quality_gates.py --list` works as a program and exits 0."""
        proc = run_python([CI_DIR / "quality_gates.py", "--list"], env=clean_env(), cwd=ROOT)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertEqual([line.split()[0] for line in proc.stdout_text.splitlines() if line.strip()], C11_NAMES)


if __name__ == "__main__":
    unittest.main()
