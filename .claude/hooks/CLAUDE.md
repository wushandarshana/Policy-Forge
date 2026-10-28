# .claude/hooks/ — CLAUDE.md

Project guardrail hooks, wired in `.claude/settings.json`. They run under bash (Git Bash on Windows).

- **LF line endings only** (`.gitattributes` enforces `*.sh eol=lf`). CRLF breaks the shebang (KD-002).
- Input is the hook JSON on stdin. Parse it with `python` (no `jq` dependency on Windows).
- Exit 0 to allow. Exit 2 with a message on stderr to block (PreToolUse) or to send feedback to Claude (PostToolUse).
- Hooks must be fast (< 1 s), side-effect free and scoped by path, so they don't fire on unrelated files.
- Each hook names the rule it enforces (NFR or root CLAUDE.md rule number) in its error message.
