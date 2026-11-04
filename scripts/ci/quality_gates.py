#!/usr/bin/env python3
"""Run every CI quality gate and report each result (NFR-09.6, plan C11).

The same entry point runs on GitHub Actions, on GitLab CI and on a developer machine, so the gates cannot drift
apart. All gates run, in this order, each as a subprocess of the current interpreter; a failing gate never stops
the run. A summary table (gate, command, exit code, result) is printed at the end and the exit code is 1 if any
gate failed. A tool that cannot be found or fetched is a failure, never a skip.

    1. ac-audit            scripts/ac_audit.py --offline --no-write
    2. redact-evidence     scripts/redact_evidence.py --check sprint-contracts specs/reviews docs/fix-loops
    3. hook-tests          .claude/hooks/tests/test_hooks.py
    4. hook-line-endings   scripts/ci/check_line_endings.py '.claude/hooks/*.sh'
    5. ci-script-tests     -m unittest discover -s scripts/tests -v
    6. oracle-fixture      scripts/gen_refund_oracle_cases.py --check
    7. actionlint          the pinned actionlint with the pinned shellcheck on .github/workflows/
    8. ci-schema           check-jsonschema on the GitHub workflows and on .gitlab-ci.yml

Usage:
    python scripts/ci/quality_gates.py                  # run all gates
    python scripts/ci/quality_gates.py --list           # print the gates and their commands
    python scripts/ci/quality_gates.py --only ac-audit  # run selected gates (repeat --only for several)
    python scripts/ci/quality_gates.py --report ci-out/quality-gates/report.md

Standard library only; runs on Python 3.11 or later. Gate subprocesses never receive ANTHROPIC_API_KEY.
"""
import argparse
import importlib.util
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CI_DIR = Path(__file__).resolve().parent
MISSING_TOOL_EXIT = 127
GATE_TIMEOUT_SECONDS = 1800
ENV_BLOCKED = ("ANTHROPIC_API_KEY",)


class GateUnavailable(Exception):
    """A gate cannot be started (nothing to check, or its environment is incomplete). It counts as a failure."""

    exit_code = 1


class MissingTool(GateUnavailable):
    """A tool that a gate needs is not available. It counts as a failure, never as a skip."""

    exit_code = MISSING_TOOL_EXIT


class Gate:
    """A named gate: one or more commands that must all exit 0.

    `source` is a list of argv lists, or a zero-argument callable returning one (used when the commands need a
    tool that is resolved lazily). `display` is the text shown by --list and in the summary table.
    """

    def __init__(self, name, source, cwd=None, display=None):
        self.name = name
        self._source = source
        self.cwd = cwd
        self._display = display

    def commands(self):
        argvs = self._source() if callable(self._source) else self._source
        return [list(argv) for argv in argvs]

    def display(self):
        if self._display is not None:
            return self._display
        return " && ".join(display_command(argv) for argv in self.commands())


def display_command(argv):
    """A copy-pasteable rendering of an argv list; the interpreter is shown as plain `python`."""
    parts = []
    for index, token in enumerate(argv):
        token = str(token)
        if index == 0 and token == sys.executable:
            token = "python"
        if not token or any(ch in token for ch in " \t*?[]()'\"|&;<>"):
            token = "'" + token.replace("'", "'\\''") + "'"
        parts.append(token)
    return " ".join(parts)


def module_available(name):
    return importlib.util.find_spec(name) is not None


def fetch_tool(name):
    """Resolve a pinned tool through scripts/ci/fetch_tools.py (which downloads and verifies it on first use)."""
    proc = subprocess.run([sys.executable, str(CI_DIR / "fetch_tools.py"), name], capture_output=True, check=False,
                          timeout=900)
    lines = proc.stdout.decode("utf-8", "replace").strip().splitlines()
    path = lines[-1].strip() if lines else ""
    if proc.returncode != 0 or not path or not Path(path).is_file():
        detail = proc.stderr.decode("utf-8", "replace").strip().splitlines()
        raise MissingTool(f"{name} is not available: " + (detail[-1] if detail else "fetch_tools.py failed"))
    return path


def workflow_files(repo_root):
    folder = Path(repo_root) / ".github" / "workflows"
    names = sorted(p.name for p in folder.glob("*") if p.is_file() and p.suffix in (".yml", ".yaml")) \
        if folder.is_dir() else []
    return [f".github/workflows/{name}" for name in names]


def build_gates(resolve_tool=fetch_tool, repo_root=ROOT, module_available=module_available):
    """The eight gates of plan C11, in order. Commands are relative to `repo_root`."""
    py = sys.executable

    def actionlint_commands():
        files = workflow_files(repo_root)
        if not files:
            raise GateUnavailable("no workflow file under .github/workflows/ to lint")
        return [[resolve_tool("actionlint"), "-shellcheck", resolve_tool("shellcheck"), *files]]

    def schema_commands():
        if not module_available("check_jsonschema"):
            raise MissingTool("check-jsonschema is not installed (pip install -r scripts/requirements-ci.txt)")
        files = workflow_files(repo_root)
        if not files:
            raise GateUnavailable("no workflow file under .github/workflows/ to check")
        return [
            [py, "-m", "check_jsonschema", "--builtin-schema", "vendor.github-workflows", *files],
            [py, "-m", "check_jsonschema", "--builtin-schema", "vendor.gitlab-ci", "--data-transform", "gitlab-ci",
             ".gitlab-ci.yml"],
        ]

    def static(name, args, display):
        return Gate(name, [[py, *args]], cwd=repo_root, display="python " + display)

    return [
        static("ac-audit", ["scripts/ac_audit.py", "--offline", "--no-write"],
               "scripts/ac_audit.py --offline --no-write"),
        static("redact-evidence", ["scripts/redact_evidence.py", "--check", "sprint-contracts", "specs/reviews",
                                   "docs/fix-loops"],
               "scripts/redact_evidence.py --check sprint-contracts specs/reviews docs/fix-loops"),
        static("hook-tests", [".claude/hooks/tests/test_hooks.py"], ".claude/hooks/tests/test_hooks.py"),
        static("hook-line-endings", ["scripts/ci/check_line_endings.py", ".claude/hooks/*.sh"],
               "scripts/ci/check_line_endings.py '.claude/hooks/*.sh'"),
        static("ci-script-tests", ["-m", "unittest", "discover", "-s", "scripts/tests", "-v"],
               "-m unittest discover -s scripts/tests -v"),
        static("oracle-fixture", ["scripts/gen_refund_oracle_cases.py", "--check"],
               "scripts/gen_refund_oracle_cases.py --check"),
        Gate("actionlint", actionlint_commands, cwd=repo_root,
             display="actionlint -shellcheck shellcheck .github/workflows/*.yml"),
        Gate("ci-schema", schema_commands, cwd=repo_root,
             display="python -m check_jsonschema --builtin-schema vendor.github-workflows .github/workflows/*.yml"
                     " && python -m check_jsonschema --builtin-schema vendor.gitlab-ci --data-transform gitlab-ci"
                     " .gitlab-ci.yml"),
    ]


def gate_environment(env=None):
    """The environment of a gate subprocess: the caller's, without the API key, with UTF-8 output."""
    source = os.environ if env is None else env
    out = {k: v for k, v in source.items() if k.upper() not in ENV_BLOCKED}
    out["PYTHONUTF8"] = "1"
    return out


def run_subprocess(argv, cwd):
    """Run one command and return (exit code, combined output). A command that cannot start exits 127."""
    try:
        proc = subprocess.run(argv, cwd=str(cwd), env=gate_environment(), stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, check=False, timeout=GATE_TIMEOUT_SECONDS)
    except FileNotFoundError as exc:
        return MISSING_TOOL_EXIT, f"command not found: {exc}\n"
    except subprocess.TimeoutExpired:
        return 124, f"timed out after {GATE_TIMEOUT_SECONDS} seconds\n"
    return proc.returncode, proc.stdout.decode("utf-8", "replace")


class Result:
    def __init__(self, name, display, exit_code, output):
        self.name = name
        self.display = display
        self.exit_code = exit_code
        self.output = output

    @property
    def passed(self):
        return self.exit_code == 0


def run_gate(gate, runner, default_cwd):
    cwd = gate.cwd if gate.cwd is not None else default_cwd
    try:
        argvs = gate.commands()
    except GateUnavailable as exc:
        return Result(gate.name, gate.display(), exc.exit_code, f"{type(exc).__name__}: {exc}\n")
    exit_code, chunks = 0, []
    for argv in argvs:
        code, output = runner(argv, cwd)
        chunks.append(output)
        if code != 0 and exit_code == 0:
            exit_code = code
    return Result(gate.name, gate.display(), exit_code, "".join(chunks))


def render_table(results):
    lines = ["| gate | command | exit | result |", "|---|---|---|---|"]
    for r in results:
        lines.append(f"| {r.name} | {r.display.replace('|', '/')} | {r.exit_code} | {'PASS' if r.passed else 'FAIL'} |")
    return "\n".join(lines)


def run_gates(gates, runner=None, default_cwd=ROOT, stream=None):
    """Run every gate (never stopping early), print each gate's output and the summary table; return the results."""
    runner = runner or run_subprocess
    stream = stream or sys.stdout
    results = []
    for gate in gates:
        print(f"=== gate: {gate.name} ===", file=stream, flush=True)
        result = run_gate(gate, runner, default_cwd)
        if result.output.strip():
            print(result.output.rstrip("\n"), file=stream)
        print(f"--- {gate.name}: exit {result.exit_code} ({'PASS' if result.passed else 'FAIL'})", file=stream,
              flush=True)
        results.append(result)
    failed = [r.name for r in results if not r.passed]
    print("", file=stream)
    print(render_table(results), file=stream)
    print("", file=stream)
    print(f"{len(results) - len(failed)}/{len(results)} gates passed" + (f"; FAILED: {', '.join(failed)}" if failed else ""),
          file=stream, flush=True)
    return results


def write_report(path, results):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    body = ["# Quality gates", "", render_table(results), ""]
    for r in results:
        body += [f"## {r.name} ({'PASS' if r.passed else 'FAIL'}, exit {r.exit_code})", "", "```", r.output.rstrip("\n"),
                 "```", ""]
    path.write_text("\n".join(body), encoding="utf-8", newline="\n")


def main(argv=None, gates=None, runner=None, stream=None):
    parser = argparse.ArgumentParser(description="Run every CI quality gate and report each result.")
    parser.add_argument("--list", action="store_true", help="print the gates and their commands, then exit")
    parser.add_argument("--only", action="append", metavar="NAME", help="run only this gate (repeatable)")
    parser.add_argument("--report", metavar="FILE", help="also write the table and every gate's output to FILE")
    args = parser.parse_args(argv)
    stream = stream or sys.stdout
    gates = build_gates() if gates is None else gates
    if args.list:
        for gate in gates:
            print(f"{gate.name:<20} {gate.display()}", file=stream)
        return 0
    if args.only:
        known = {g.name for g in gates}
        unknown = [name for name in args.only if name not in known]
        if unknown:
            print(f"unknown gate(s): {', '.join(unknown)}; known: {', '.join(g.name for g in gates)}",
                  file=sys.stderr)
            return 2
        gates = [g for g in gates if g.name in args.only]
    results = run_gates(gates, runner=runner, stream=stream)
    if args.report:
        write_report(args.report, results)
    return 0 if all(r.passed for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
