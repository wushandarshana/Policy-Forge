"""PreToolUse(Bash|PowerShell): enforce the PR-only merge rule (root CLAUDE.md rule 3).

Blocks `git commit` and `git push ... main` while the current branch is main.
`git merge --no-ff <branch>` on main is the sanctioned PR merge and is allowed.
"""
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

data = hookio.load()
cmd = (data.get("tool_input") or {}).get("command", "") or ""
if not re.search(r"\bgit\b", cmd):
    sys.exit(0)

try:
    # `branch --show-current` also works before the first commit (rev-parse would print "HEAD").
    branch = subprocess.run(["git", "branch", "--show-current"], cwd=data.get("cwd") or None,
                            capture_output=True, text=True, timeout=5).stdout.strip()
except (OSError, subprocess.SubprocessError):
    sys.exit(0)

if re.search(r"\bgit\s+(-c\s+\S+\s+)*commit\b", cmd) and branch == "main":
    hookio.block("PR-only merge", "direct commits on main are forbidden. "
                 "Create a feature branch (git checkout -b feat/<name>) and merge via /pr (git merge --no-ff).")
if re.search(r"\bgit\s+push\b.*\bmain\b", cmd) and branch != "main":
    hookio.block("PR-only merge", "pushing to main from a feature branch bypasses review. Merge via /pr first.")
if re.search(r"\bgit\s+merge\b", cmd) and branch == "main" and "--no-ff" not in cmd:
    hookio.block("PR-only merge", "merges into main must use --no-ff to preserve PR history.")

sys.exit(0)
