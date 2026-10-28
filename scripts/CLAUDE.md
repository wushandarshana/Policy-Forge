# scripts/ — CLAUDE.md

Automation that drives Claude programmatically through the **Claude Agent SDK** (`claude-agent-sdk`, Python).

- `ac_audit.py`: reads `specs/*_spec.md` and test sources, asks a Claude agent (read-only tools) to map every AC-NN
  to tests and flag spec/code drift, and writes `specs/reviews/ac-audit-<date>.md`. Exit code 1 if any AC is
  uncovered, so CI can use it.
- Scripts must be read-only with respect to production code. Allowed tools: `Read`, `Glob`, `Grep`.
- Requires `ANTHROPIC_API_KEY` (or a logged-in Claude Code). Install with `pip install -r scripts/requirements.txt`.
