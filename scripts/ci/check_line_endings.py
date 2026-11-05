#!/usr/bin/env python3
"""LF-only check for files such as .claude/hooks/*.sh (NFR-09.6, KD-002).

A CRLF in a hook breaks its shebang on Linux, and a hook that cannot run is a guardrail that is silently off.
The check fails, listing every offending file, when a matched tracked file

  * has a CR byte in the working tree, or
  * is reported as `i/crlf` or `i/mixed` by `git ls-files --eol` (the index holds the CR).

A pattern that matches no tracked file also fails: a typo must not turn the gate into a no-op.

Usage:
    python scripts/ci/check_line_endings.py '.claude/hooks/*.sh' [more patterns]
    python scripts/ci/check_line_endings.py --repo <dir> <pattern> ...

Patterns are git pathspecs. Standard library only; runs on Python 3.11 or later.
"""
import argparse
import subprocess
import sys
from pathlib import Path

BAD_INDEX_STATES = {"i/crlf", "i/mixed"}


def tracked_files(repo, patterns):
    """(path, index state) for every tracked file that matches one of the git pathspecs."""
    proc = subprocess.run(["git", "-C", str(repo), "ls-files", "--eol", "-z", "--", *patterns],
                          capture_output=True, check=False)
    if proc.returncode != 0:
        raise RuntimeError("git ls-files failed: " + proc.stderr.decode("utf-8", "replace").strip())
    found = []
    for entry in proc.stdout.decode("utf-8", "replace").split("\0"):
        if not entry:
            continue
        info, _, path = entry.partition("\t")
        tokens = info.split()
        found.append((path, tokens[0] if tokens else ""))
    return found


def check(repo, patterns):
    """Return (problems, files checked). Each problem is a printable line naming the file."""
    files = tracked_files(repo, patterns)
    problems = []
    for path, index_state in files:
        reasons = []
        if index_state in BAD_INDEX_STATES:
            reasons.append(f"the index has CRLF line endings ({index_state})")
        full = Path(repo) / path
        try:
            data = full.read_bytes()
        except OSError:
            data = b""  # deleted in the working tree: nothing to inspect there
        if b"\r" in data:
            reasons.append("the working tree file contains a CR byte")
        if reasons:
            problems.append(f"{path}: " + "; ".join(reasons))
    return problems, files


def main(argv=None):
    parser = argparse.ArgumentParser(description="Fail when matched tracked files contain CR line endings.")
    parser.add_argument("patterns", nargs="+", help="git pathspec of the files to check, for example '.claude/hooks/*.sh'")
    parser.add_argument("--repo", default=".", help="repository to check (default: the current directory)")
    args = parser.parse_args(argv)
    try:
        problems, files = check(Path(args.repo), args.patterns)
    except (RuntimeError, OSError) as exc:
        print(f"FAIL: {exc}")
        return 1
    if not files:
        print("FAIL: no file matched " + " ".join(args.patterns) + " (check the pattern)")
        return 1
    if problems:
        print(f"FAIL: {len(problems)} of {len(files)} file(s) are not LF-only:")
        for problem in problems:
            print("  " + problem)
        return 1
    print(f"OK: {len(files)} file(s) matched, all LF-only")
    return 0


if __name__ == "__main__":
    sys.exit(main())
