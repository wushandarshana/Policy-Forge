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
    """Root of the work tree that contains the file being written.

    Resolved from the file's own location first, so the session cwd (e.g. after `cd backend`) does not matter and a
    git worktree under .claude/worktrees/<n>/ resolves to that worktree, not the main checkout.
    """
    start = Path(file_path(data) or data.get("cwd") or os.environ.get("CLAUDE_PROJECT_DIR") or ".")
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
    return os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or ""


def rel_path(data):
    """Repo-relative, forward-slash path of the file being written."""
    # resolve() expands Windows 8.3 short names (C:\Users\ABCDEF~1) so both sides use the same form
    p = str(Path(file_path(data)).resolve()).replace("\\", "/") if file_path(data) else ""
    root = project_root(data)
    root = str(Path(root).resolve()).replace("\\", "/").rstrip("/") if root else ""
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


# One alternation scanned left to right: whichever token starts first wins, so a "/*" inside a string literal
# (e.g. "/api/products/**") cannot open a fake comment, and quotes inside comments cannot open fake strings.
_TOKENS = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'|`(?:\\.|[^`\\])*`'
                     r'|/\*[\s\S]*?(?:\*/|$)|//[^\n]*')


def strip_code(text):
    """Blank comments and string/char/template literals in a single pass; keeps line structure."""
    def repl(m):
        tok = m.group(0)
        if tok.startswith("/"):
            return "\n" * tok.count("\n") or " "
        quote = tok[:3] if tok.startswith('"""') else tok[0]
        return quote + quote
    return _TOKENS.sub(repl, text)


# Kept for callers; both now use the single-pass scanner.
strip_comments = strip_code
strip_strings = strip_code


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
