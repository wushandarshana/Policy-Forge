"""PreToolUse(Bash|PowerShell): enforce the PR-only merge rule (root CLAUDE.md rule 3).

The command line is split into segments (&&, ||, ;, |, newlines). Branch switches inside the
command (`git checkout main && git commit`) are tracked, so the rule applies to the branch each
git segment actually runs on. Only segments whose program is `git` are inspected, so text such as
`echo "git commit"` is ignored.

Branching strategy: feature (feat/ fix/ chore/) -> dev -> main. On the protected branches main and dev:
commit / cherry-pick / revert / am / rebase / history-moving reset / non-ff-only pull are blocked.
Merges need --no-ff and an allowed source: dev accepts feat/, fix/, chore/; main accepts dev or hotfix/.
--ff-only is allowed only from the branch's own upstream (syncing after a remote PR merge).
--abort/--continue and `reset HEAD <file>` are allowed. Pushing a protected branch under its own name
is allowed; force-pushing, deleting, or pushing another ref onto main/dev is blocked.

Known limits (deliberate evasion, not accidental use): commands wrapped in `bash -c`, subshells,
`env`/`xargs`, and plumbing that moves main without a commit (`branch -f`, `update-ref`,
`fetch . X:main`, `push --all/--mirror`). The backstop is the protected `main` branch on the GitLab
remote plus CI; see .claude/hooks/CLAUDE.md.
"""
import re
import shlex
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

# (protected-branch names live in PROTECTED below)
REWRITES_MAIN = {"commit", "cherry-pick", "revert", "am", "rebase", "reset", "pull"}
# git global options that consume the following token
GLOBAL_WITH_ARG = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--config-env"}
ENV_ASSIGN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")


SEPARATORS = ";&|\n"


def command_segments(command):
    """Token lists, one per command, split on ;, &&, ||, |, & and newlines that are OUTSIDE quotes.

    Tokenizing first matters: a quoted commit message such as `-m "a; b && c"` must stay one argument instead of
    being cut into fake commands (KD-012). Unbalanced quotes fall back to a naive split, which is conservative
    (it can only produce extra segments to inspect).
    """
    try:
        lex = shlex.shlex(command, posix=True, punctuation_chars=SEPARATORS)
        lex.whitespace = " \t\r"
        lex.whitespace_split = True
        out, cur = [], []
        for tok in lex:
            if tok and set(tok) <= set(SEPARATORS):
                if cur:
                    out.append(cur)
                cur = []
            else:
                cur.append(tok)
        if cur:
            out.append(cur)
        return out
    except ValueError:
        return [p.split() for p in re.split(r"&&|\|\||;|\||\n", command) if p.strip()]


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


def previous_branch(cwd):
    try:
        out = subprocess.run(["git", "rev-parse", "--abbrev-ref", "@{-1}"], cwd=cwd or None,
                             capture_output=True, text=True, timeout=5)
        return out.stdout.strip() or None
    except (OSError, subprocess.SubprocessError):
        return None


def switched_branch(sub, args, cwd=None):
    if sub not in ("checkout", "switch"):
        return None
    if "--" in args:  # `git checkout main -- file` restores a path; the branch does not change
        return None
    if args[:1] == ["-"]:
        return previous_branch(cwd)
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


CONTROL_FLAGS = {"--abort", "--continue", "--quit", "--skip"}
# Branching strategy (root CLAUDE.md rule 3): feature -> dev -> main. Both are protected.
PROTECTED = ("main", "dev")
# Merge sources each protected branch accepts (with --no-ff).
MERGE_SOURCES = {
    "dev": re.compile(r"^(feat|fix|chore)/[\w./-]+$"),
    "main": re.compile(r"^(dev|hotfix/[\w./-]+)$"),
}
# `git merge` options that consume the following token (so it is not mistaken for a merge source).
MERGE_OPTS_WITH_ARG = {"-m", "--message", "-F", "--file", "-s", "--strategy", "-X", "--strategy-option",
                       "--into-name", "--cleanup"}


def short(ref):
    return ref[len("refs/heads/"):] if ref.startswith("refs/heads/") else ref


def upstream_of(branch):
    """Only a protected branch's own upstream may be fast-forwarded into it (a sync, not a change)."""
    return {"origin/" + branch, "upstream/" + branch, "refs/remotes/origin/" + branch,
            "refs/remotes/upstream/" + branch, "@{u}", "@{upstream}"}


def merge_sources(args):
    sources, i = [], 0
    while i < len(args):
        a = args[i]
        if a in MERGE_OPTS_WITH_ARG:
            i += 2
            continue
        if not a.startswith("-"):
            sources.append(a)
        i += 1
    return sources


def merge_violation(branch, args):
    """Reason a merge into a protected branch is not allowed, or None."""
    if CONTROL_FLAGS & set(args):
        return None
    sources = merge_sources(args)
    if "--ff-only" in args and sources and all(s in upstream_of(branch) for s in sources):
        return None
    if "--no-ff" not in args:
        return f"merges into {branch} must use --no-ff to preserve PR history " \
               f"(--ff-only is allowed only from {branch}'s own upstream: origin/{branch} or @{{u}})."
    bad = [s for s in sources if not MERGE_SOURCES[branch].match(short(s))]
    if bad or not sources:
        allowed = "feat/, fix/ or chore/ branches" if branch == "dev" else "dev (or hotfix/*)"
        return f"merges into {branch} must come from {allowed}; got {bad or 'no branch'}."
    return None


def rewrites_protected(branch, sub, args):
    """True if this git subcommand, run on a protected branch, would create or move commits directly."""
    if CONTROL_FLAGS & set(args):
        return False  # finishing/aborting an operation never adds new history
    positional = [a for a in args if not a.startswith("-")]
    if sub == "pull":
        refspecs = positional[1:]
        return "--ff-only" not in args or any(short(r) != branch for r in refspecs)
    if sub == "reset":
        if "--" in args or not positional:
            return any(a in args for a in ("--hard", "--soft", "--keep", "--merge"))
        return not (positional[0] == "HEAD" and len(positional) > 1)  # `reset HEAD <file>` only unstages
    return sub in REWRITES_MAIN


def push_violation(args, branch):
    """Reason a push is not allowed, or None.

    Protected branches may be pushed under their own name (they only ever change through local --no-ff merges,
    which this hook enforces). Blocked: force-pushing or deleting a protected branch, and pushing any other
    ref onto one (e.g. `HEAD:main` from a feature branch, `feat/x:dev`).
    """
    if "--mirror" in args:
        return "push --mirror can rewrite or delete protected branches."
    force = any(a in ("-f", "--force") or a.startswith("--force-with-lease") for a in args)
    delete = "-d" in args or "--delete" in args
    positional = [a for a in args if not a.startswith("-")]
    refspecs = positional[1:]
    if not refspecs:
        targets = [(branch, branch, False)]  # bare push sends the current branch to its upstream
    else:
        targets = []
        for spec in refspecs:
            plus = spec.startswith("+")
            spec = spec.lstrip("+")
            src, dest = spec.split(":", 1) if ":" in spec else (spec, spec)
            src = branch if src == "HEAD" else short(src)
            dest = branch if dest == "HEAD" else short(dest)
            targets.append((src, dest, plus))
    for src, dest, plus in targets:
        if dest not in PROTECTED:
            continue
        if force or plus:
            return f"force-pushing {dest} is forbidden."
        if delete or not src:
            return f"deleting {dest} is forbidden."
        if src != dest:
            return f"pushing {src} onto {dest} bypasses review. Merge it with --no-ff locally (via /pr), then push {dest}."
    return None


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

    for seg in command_segments(command):
        call = git_call(seg)
        if call is None:
            continue
        sub, args = call
        new_branch = switched_branch(sub, args, data.get("cwd"))
        if new_branch:
            branch = new_branch
            continue
        if branch in PROTECTED and sub == "merge":
            reason = merge_violation(branch, args)
            if reason:
                hookio.block("PR-only merge", reason)
        elif branch in PROTECTED and rewrites_protected(branch, sub, args):
            hookio.block("PR-only merge", f"`git {sub}` on {branch} is forbidden. Work on a feature branch from dev "
                         "(git checkout -b feat/<name> dev) and merge via /pr (git merge --no-ff).")
        if sub == "push":
            reason = push_violation(args, branch)
            if reason:
                hookio.block("PR-only merge", reason)
    return 0


if __name__ == "__main__":
    sys.exit(main())
