# Harness Setup

## Foundation plugin

The brief asks for the "Claude Harness Engine" plugin (Generator–Evaluator harness). Anthropic doesn't publish a
plugin by that name, and none was available in this environment. We use the public, MIT-licensed
**[bouob/agent-harness](https://github.com/bouob/agent-harness)** (v2.8.x). It implements the same
Planner → Generator → Evaluator sprint pattern with a plan checkpoint, retries, and an evaluator that can use
Playwright MCP.

### Install (run inside Claude Code in this repo)

```
/plugin marketplace add bouob/agent-harness
/plugin install agent-harness@agent-harness
/reload-plugins
/agent-harness:init
```

`/agent-harness:init` writes `.claude/agent-harness.local.json` (model routing; gitignored).

### What the harness provides vs what this repo adds

| Brief's harness capability | agent-harness | PolicyForge substrate (`.claude/`) |
|---|---|---|
| planner | ✅ Planner → `sprint-plan.md` | `specs/` as input |
| generator (→ implementer) | ✅ Generator (code/write/research) | domain agents: `quote-engine-agent`, `endorsement-agent`, `renewal-orchestrator-agent` |
| evaluator (→ reviewer) | ✅ Evaluator → `sprint-eval.md`, up to 3 retries | Playwright MCP in `.mcp.json`; `premium-calc-evaluator` skill |
| security-reviewer | ❌ | `.claude/agents/security-reviewer.md` |
| test-engineer (→ tester) | ❌ | `.claude/agents/test-engineer.md` |
| design-critic | ❌ | `.claude/agents/design-critic.md` |
| ui-designer | ❌ | `.claude/agents/ui-designer.md` |
| 12+ hooks | ❌ (Codex-only hooks) | `.claude/hooks/*.sh`, wired in `.claude/settings.json` |
| sprint contracts | `.sprint/<ts>/` (gitignored working dir) | `/sprint-archive` copies to `sprint-contracts/` and `specs/reviews/` |
| self-healing loop | evaluator retry (≤3) | `docs/fix-loops/` traces; `/pr` re-runs reviewers |

## Sprint cycle

1. `/sprint specs/<feature>_spec.md` (plan checkpoint: approve or edit `sprint-plan.md`; this is the **sprint contract**).
2. Generator tasks run test-first (see `docs/tdd.md`).
3. Evaluator: `./mvnw verify`, `npm test`, Playwright MCP walkthrough of the feature's UI, and AC checklist → `sprint-eval.md`.
4. Ratchet: failed criteria are retried, up to 3 iterations. Anything still failing is recorded and fixed in a
   follow-up.
5. `security-reviewer` and `test-engineer` agents review the diff.
6. `/sprint-archive` → `/pr` (`--no-ff` merge of the feature branch into `dev`). After QA, `/release` promotes
   `dev` → `main`.
