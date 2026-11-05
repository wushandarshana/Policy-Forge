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
6. `/sprint-archive` → `/pr` (opens a GitHub PR into `dev`; it is merged with a merge commit once every CI job is
   green). After QA, `/release` promotes `dev` → `main`.

## CI pipelines (Sprint 5)

GitHub Actions runs on `origin` (`.github/workflows/ci.yml`). `.gitlab-ci.yml` is the equivalent for a GitLab
project. Both run the same jobs through the same scripts, for pushes to `dev` and `main` and for pull or merge
requests into them.

| Job | What it gates |
|---|---|
| `build` | `./mvnw -B verify` with the SPA packaged: unit and integration tests, ArchUnit, JaCoCo (80% domain, 70% overall). Publishes the JAR |
| `frontend-unit` | `npm ci`, `npm test` (Vitest with coverage; lcov is an artifact, not committed), `npm run build` |
| `e2e` | Playwright on both projects in `mcr.microsoft.com/playwright` at the locked `@playwright/test` version, against the packaged JAR. The report and traces are kept on failure |
| `quality-gates` | `python scripts/ci/quality_gates.py`: offline AC audit, evidence redaction check, hook regression suite, LF check on hooks, CI script tests, AC-08.5 oracle fixture, actionlint, CI schemas |
| `claude-review` | Runs after the other four pass, in the protected environment `claude-review`. The pinned Claude Code CLI (from `scripts/ci/claude-cli/package-lock.json`) reviews the diff twice, with the `security-reviewer` and `test-engineer` criteria. It has no shell tool, reads only the checkout, gets an allowlisted environment, and fails closed if it changes any file. Any BLOCKER fails the job. The report is an artifact |
| `claude-review-comment` | PRs and MRs only. Holds the platform token but never the API key, runs the comment code from the **base** branch, and edits only its own bot comment |
| `ci-gate` | Fails unless every gate job succeeded, so a skipped review never counts as passing. This is the check to require |

### Add the API key
- **GitHub:** create the environment `claude-review` (Settings → Environments) with **Required reviewers** set to the
  repository owner. Add `ANTHROPIC_API_KEY` there as an **environment secret**, and delete any repository-level copy.
  Every run then waits for the owner's approval before the review starts. The optional variable
  `PF_REVIEW_MAX_BUDGET_USD` sets the cap per review pass (default `5.00`).
- **GitLab:**
  - Create the protected environments `claude-review` (deployment approval required) and `claude-review-comment`.
  - Add `ANTHROPIC_API_KEY` (masked, protected), scoped to `claude-review`.
  - Optionally add `PF_GITLAB_REVIEW_TOKEN` (a project access token, Reporter role, scope `api`, masked, protected),
    scoped to `claude-review-comment`.
  - Don't protect the feature branches for this.
- Never set `NODE_TLS_REJECT_UNAUTHORIZED=0` (KD-015). A PR from a fork gets no secrets, so its review fails closed. A
  maintainer re-pushes the branch to `origin` to review it.

### Required checks and code owners
`dev` and `main` accept PRs only (ruleset "Safety Guard Rails"). After the first green run, the ruleset also requires
`ci-gate`. `.github/CODEOWNERS` names the owner of the CI files, CI scripts and reviewer agents. With a single owner,
don't enable "Require review from Code Owners", because it would block the owner's own PRs; the protected
environment's approval is the control. GitLab reads CODEOWNERS only from the root, `docs/` or `.gitlab/`. On GitLab,
enable "Pipelines must succeed".

### Linux visual baselines
Baselines are per platform: `-win32` locally, `-linux` in CI. On a platform with no baselines, E2E skips only the
visual assertions and lists them (NFR-09.5). To create or refresh the `-linux` set:
1. Add the label `update-linux-snapshots` to the PR. You can also run the `Linux snapshots` workflow by hand once it
   is on `main`, or the GitLab manual job `e2e-update-linux-snapshots`.
2. Download the result with `gh run download <run-id> --name linux-snapshot-baselines --dir frontend/e2e/__snapshots__`.
3. Review the images, and commit them in their own commit.
