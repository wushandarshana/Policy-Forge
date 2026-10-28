"""PreToolUse(Bash|PowerShell): enforce the PR-only merge rule (root CLAUDE.md rule 3).

The command line is split into segments (&&, ||, ;, |, newlines). Branch switches inside the
command (`git checkout main && git commit`) are tracked, so the rule applies to the branch each
git segment actually runs on. Only segments whose program is `git` are inspected, so text such as
`echo "git commit"` is ignored.

On main: commit / cherry-pick / revert / am / rebase / reset / pull are blocked; merge is allowed
only with --no-ff (the sanctioned PR merge). From any branch: pushing to main is blocked.
"""
import re
import shlex
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

MAIN = {"main", "refs/heads/main"}
REWRITES_MAIN = {"commit", "cherry-pick", "revert", "am", "rebase", "reset", "pull"}
# git global options that consume the following token
GLOBAL_WITH_ARG = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--config-env"}
ENV_ASSIGN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")


def segments(command):
    for part in re.split(r"&&|\|\||;|\||\n", command):
        part = part.strip()
        if part:
            yield part


def tokens(segment):
    try:
        return shlex.split(segment, posix=True)
    except ValueError:
        return segment.split()


def git_call(tok):
    """Return (subcommand, args) if this segment runs git, else None."""
    while tok and ENV_ASSIGN.match(tok[0]):
        tok = tok[1:]
    if tok and tok[0] == "&":  # PowerShell call operator
        tok = tok[1:]
    if not tok or Path(tok[0]).name.lower() not in ("git", "git.exe"):
        return None
    i = 1
    while i < len(tok) and tok[i].startswith("-"):
        opt = tok[i]
        i += 2 if opt in GLOBAL_WITH_ARG else 1
    if i >= len(tok):
        return None
    return tok[i], tok[i + 1:]


def switched_branch(sub, args):
    if sub not in ("checkout", "switch"):
        return None
    positional, i = [], 0
    while i < len(args):
        a = args[i]
        if a in ("-b", "-B", "-c", "-C", "--orphan") and i + 1 < len(args):
            return args[i + 1]
        if a == "--":
            break
        if not a.startswith("-"):
            positional.append(a)
        i += 1
    return positional[0] if positional else None


def pushes_to_main(args, branch):
    positional = [a for a in args if not a.startswith("-")]
    refspecs = positional[1:]
    if not refspecs:
        return branch == "main"  # bare `git push` / `git push origin` pushes the current branch
    for spec in refspecs:
        dest = spec.lstrip("+").split(":")[-1]
        if dest in MAIN or (":" not in spec and dest == "HEAD" and branch == "main"):
            return True
    return False


def main():
    data = hookio.load()
    command = (data.get("tool_input") or {}).get("command", "") or ""
    if "git" not in command:
        return 0
    try:
        branch = subprocess.run(["git", "branch", "--show-current"], cwd=data.get("cwd") or None,
                                capture_output=True, text=True, timeout=5).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return 0

    for seg in segments(command):
        call = git_call(tokens(seg))
        if call is None:
            continue
        sub, args = call
        new_branch = switched_branch(sub, args)
        if new_branch:
            branch = new_branch
            continue
        if branch == "main" and sub in REWRITES_MAIN:
            hookio.block("PR-only merge", f"`git {sub}` on main is forbidden. Create a feature branch "
                         "(git checkout -b feat/<name>) and merge via /pr (git merge --no-ff).")
        if branch == "main" and sub == "merge" and "--no-ff" not in args \
                and not {"--abort", "--continue", "--quit"} & set(args):
            hookio.block("PR-only merge", "merges into main must use --no-ff to preserve PR history.")
        if sub == "push" and pushes_to_main(args, branch):
            hookio.block("PR-only merge", "pushing to main bypasses review. Push the feature branch and merge via /pr.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
