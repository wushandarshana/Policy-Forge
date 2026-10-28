"""Shared helpers for PolicyForge guardrail hooks.

Hook input arrives as JSON on stdin (Claude Code hook protocol). Exit code 2 blocks a
PreToolUse call (stderr is shown to Claude); exit 0 allows.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path


def load():
    try:
        return json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return {}


def file_path(data):
    return (data.get("tool_input") or {}).get("file_path", "") or ""


def project_root(data):
    """Repository root, independent of the session cwd (which may be a subfolder, e.g. after `cd backend`)."""
    env = os.environ.get("CLAUDE_PROJECT_DIR")
    if env:
        return env
    start = Path(file_path(data) or data.get("cwd") or ".")
    while not start.exists() and start != start.parent:
        start = start.parent
    if start.is_file():
        start = start.parent
    try:
        out = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=str(start),
                             capture_output=True, text=True, timeout=5)
        if out.returncode == 0 and out.stdout.strip():
            return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return data.get("cwd") or ""


def rel_path(data):
    """Repo-relative, forward-slash path of the file being written."""
    p = file_path(data).replace("\\", "/")
    root = project_root(data).replace("\\", "/").rstrip("/")
    if root and p.lower().startswith(root.lower() + "/"):
        return p[len(root) + 1:]
    return p


def new_text(data):
    """Text the tool call is about to write (Write content, Edit new_string, MultiEdit edits)."""
    ti = data.get("tool_input") or {}
    if "content" in ti:
        return ti.get("content") or ""
    if "new_string" in ti:
        return ti.get("new_string") or ""
    return "\n".join(e.get("new_string", "") for e in ti.get("edits") or [])


def strip_comments(java_or_ts):
    text = re.sub(r"/\*.*?\*/", "", java_or_ts, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def strip_strings(code):
    """Blank out string/char/template literals so words inside messages are not mistaken for code."""
    code = re.sub(r'"""[\s\S]*?"""', '""', code)
    code = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', code)
    code = re.sub(r"'(?:\\.|[^'\\\n])*'", "''", code)
    return re.sub(r"`(?:\\.|[^`\\])*`", "``", code)


def tracked_on_main(path, cwd):
    """True if `path` already exists on the main branch (i.e. it is published)."""
    try:
        out = subprocess.run(["git", "cat-file", "-e", f"main:{path}"], cwd=cwd or None,
                             capture_output=True, timeout=5)
        return out.returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


def block(rule, message):
    print(f"[PolicyForge guardrail] {rule}: {message}", file=sys.stderr)
    sys.exit(2)


def context(event, message):
    """Non-blocking extra context for Claude (PostToolUse)."""
    print(json.dumps({"hookSpecificOutput": {"hookEventName": event, "additionalContext": message}}))
    sys.exit(0)


def path_matches(path, pattern):
    return re.search(pattern, path) is not None
