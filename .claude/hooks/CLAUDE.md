# .claude/hooks/ — CLAUDE.md

Project guardrail hooks, wired in `.claude/settings.json`. They run under bash (Git Bash on Windows).

- **LF line endings only** (`.gitattributes` enforces `*.sh eol=lf`). CRLF breaks the shebang (KD-002).
- Input is the hook JSON on stdin. Parse it with `python` (no `jq` dependency on Windows).
- Exit 0 to allow. Exit 2 with a message on stderr to block (PreToolUse) or to send feedback to Claude (PostToolUse).
- Hooks must be fast (< 1 s), side-effect free and scoped by path, so they don't fire on unrelated files.
- Each hook names the rule it enforces (NFR or root CLAUDE.md rule number) in its error message.
- Paths are resolved against `$CLAUDE_PROJECT_DIR` (or `git rev-parse --show-toplevel`), never the session cwd, so
  the hooks still apply after `cd backend`.
- **Fail closed:** if no working Python exists, the wrappers exit 2, so Write, Edit and git calls are blocked
  until Python is fixed. This is deliberate; a silently disabled guardrail is worse.
- **Known limits:** file edits made through shell commands (sed, redirection) aren't inspected. ArchUnit, Flyway
  `validateOnMigrate` and CI are the backstop. Inside this repo, don't also enable the packaged plugin hooks
  (`hooks.json`), or every hook runs twice.
- **Regression tests:** `python .claude/hooks/tests/test_hooks.py` must pass after any hook change.
