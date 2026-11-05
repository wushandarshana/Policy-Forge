"""Stub Claude Code CLI for the CI script tests. It never talks to an API and never reads a credential.

The review runner starts it through PF_CLAUDE_CLI as `python fake_claude.py --fake-config <file> <CLI arguments>`.
The runner gives the CLI an allowlisted environment (NFR-09.7), so the stub takes its behaviour from the JSON config
file, not from environment variables. Keys of the config:

    log          directory where every call is recorded (calls.jsonl, pass-N.stdin, pass-N.env.json, pass-N.input/)
    version      what `--version` prints (default 2.1.311)
    security     scenario for the security-reviewer pass (default approve)
    test         scenario for the test-engineer pass (default approve)
    leak         a key-shaped string the leak_key scenario echoes to stdout and stderr
    echo_env_key true: the leak_key scenario also echoes the ANTHROPIC_API_KEY of this process
    write        {"path": "<relative to the working directory, or absolute>", "data": "<text>"}, or a list of them:
                 the `write` scenario appends each data (default text; "" creates an empty file)
    delete       a list of paths (relative to the working directory) the `write` scenario deletes
    then         the scenario that runs after `write` (default approve), e.g. exit1
    probe        a list of paths (relative to the working directory): each pass records in pass-N.probe.json the
                 text of each one, or null when it does not exist

Scenarios: approve, blocker, major_minor, changes_no_blocker, minor_only, fenced, no_output, invalid_schema,
missing_key, exit1, is_error, leak_key, write (writes a file, then approves).
"""
import json
import os
import shutil
import sys
from pathlib import Path

PREFIX = {"security-reviewer": "SEC", "test-engineer": "TEST"}
INPUT_DIR = Path("ci-out") / "review" / "input"


def finding(agent, severity, number=1, detail=None):
    """A schema-valid finding whose id and title are derived from the agent and severity."""
    return {
        "id": f"{PREFIX[agent]}-{severity}-{number}",
        "severity": severity,
        "title": f"Fake {severity.lower()} finding {number} from {agent}",
        "file": "src/a.txt",
        "line": 3,
        "detail": detail or f"Fake detail for {severity} finding {number} of {agent}.",
    }


def verdict(agent, scenario, leak=""):
    """The C6 verdict a given scenario produces for an agent."""
    out = {
        "agent": agent,
        "verdict": "APPROVE",
        "checklist": [
            {"item": f"{agent} checklist item one", "status": "PASS", "evidence": "src/a.txt:1"},
            {"item": f"{agent} checklist item two", "status": "N/A", "evidence": "not applicable"},
        ],
        "findings": [],
        "summary": f"Fake summary from {agent}.",
    }
    if scenario == "blocker":
        out["verdict"] = "CHANGES_REQUESTED"
        out["findings"] = [finding(agent, "BLOCKER")]
    elif scenario == "major_minor":
        out["verdict"] = "CHANGES_REQUESTED"
        out["findings"] = [finding(agent, "MAJOR"), finding(agent, "MINOR")]
    elif scenario == "changes_no_blocker":
        out["verdict"] = "CHANGES_REQUESTED"
        out["findings"] = [finding(agent, "MAJOR")]
    elif scenario == "minor_only":
        out["findings"] = [finding(agent, "MINOR")]
    elif scenario == "leak_key":
        out["verdict"] = "CHANGES_REQUESTED"
        out["findings"] = [finding(agent, "MINOR", detail=f"The reviewer echoed this value: {leak}")]
    return out


def envelope(structured=None, result="", **extra):
    """The single JSON object `--output-format json` prints."""
    body = {"type": "result", "subtype": "success", "is_error": False, "result": result, "num_turns": 3,
            "total_cost_usd": 0.01, "session_id": "fake-session"}
    if structured is not None:
        body["structured_output"] = structured
    body.update(extra)
    return body


def record(log_dir, argv, stdin, env):
    if not log_dir:
        return
    log = Path(log_dir)
    log.mkdir(parents=True, exist_ok=True)
    calls = log / "calls.jsonl"
    number = len(calls.read_text(encoding="utf-8").splitlines()) + 1 if calls.exists() else 1
    with calls.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps({"n": number, "argv": argv, "version_call": "--version" in argv,
                                 "cwd": os.getcwd()}) + "\n")
    if "--version" not in argv:
        (log / f"pass-{number}.stdin").write_bytes(stdin)
        (log / f"pass-{number}.env.json").write_text(json.dumps(env), encoding="utf-8")
        if INPUT_DIR.is_dir():  # what the review input folder held while this pass ran
            shutil.copytree(INPUT_DIR, log / f"pass-{number}.input")


def record_probe(log_dir, paths):
    """pass-N.probe.json: {path: text or None} of the given paths in the working directory of the latest pass."""
    if not log_dir or not paths:
        return
    log = Path(log_dir)
    number = len((log / "calls.jsonl").read_text(encoding="utf-8").splitlines())
    seen = {path: Path(path).read_text(encoding="utf-8") if Path(path).is_file() else None for path in paths}
    (log / f"pass-{number}.probe.json").write_text(json.dumps(seen), encoding="utf-8")


def load_config(argv):
    """(config, remaining argv). The runner puts `--fake-config <file>` first, from PF_CLAUDE_CLI."""
    if len(argv) >= 2 and argv[0] == "--fake-config":
        return json.loads(Path(argv[1]).read_text(encoding="utf-8")), argv[2:]
    return {}, argv


def main(argv):
    config, argv = load_config(argv)
    log_dir = config.get("log")
    if "--version" in argv:
        record(log_dir, argv, b"", {})
        print(f"{config.get('version', '2.1.311')} (Claude Code)")
        return 0
    stdin = sys.stdin.buffer.read()
    record(log_dir, argv, stdin, dict(os.environ))
    record_probe(log_dir, config.get("probe"))
    agent = "security-reviewer" if b"**security reviewer**" in stdin else "test-engineer"
    scenario = config.get("security" if agent == "security-reviewer" else "test", "approve")
    leak = config.get("leak", "")
    if scenario == "leak_key":
        sys.stderr.write(f"debug: the stub CLI echoes {leak}\n")
        if config.get("echo_env_key"):
            sys.stderr.write(f"debug: and the process key {os.environ.get('ANTHROPIC_API_KEY', '')}\n")
            leak = leak + " " + os.environ.get("ANTHROPIC_API_KEY", "")
    if scenario == "write":
        writes = config.get("write") or []
        for item in [writes] if isinstance(writes, dict) else writes:
            target = Path(item["path"])
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("a", encoding="utf-8", newline="\n") as handle:
                handle.write(item.get("data", "written by the stub CLI\n"))
        for relative in config.get("delete") or []:
            Path(relative).unlink()
        scenario = config.get("then", "approve")
    if scenario == "exit1":
        sys.stderr.write("fake claude: the call failed\n")
        return 1
    if scenario == "no_output":
        print(json.dumps(envelope(result="I could not produce a structured verdict.")))
        return 0
    if scenario == "is_error":
        print(json.dumps(envelope(is_error=True, subtype="error_max_turns", result="")))
        return 0
    if scenario == "fenced":
        good = json.dumps(verdict(agent, "approve"), indent=2)
        text = ("First draft:\n```json\n{\"decoy\": true}\n```\n"
                f"Final verdict:\n```json\n{good}\n```\nDone.")
        print(json.dumps(envelope(result=text)))
        return 0
    if scenario == "invalid_schema":
        bad = verdict(agent, "approve")
        bad["verdict"] = "MAYBE"
        print(json.dumps(envelope(structured=bad)))
        return 0
    if scenario == "missing_key":
        bad = verdict(agent, "approve")
        del bad["summary"]
        print(json.dumps(envelope(structured=bad)))
        return 0
    structured = verdict(agent, scenario, leak=leak)
    print(json.dumps(envelope(structured=structured, result=f"Review done. {leak}".strip())))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
