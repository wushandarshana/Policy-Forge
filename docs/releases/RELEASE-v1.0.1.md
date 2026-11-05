# Release v1.0.1: Submission polish and CI review hardening

| | |
|---|---|
| Version | **v1.0.1** (patch: no application code changed) |
| Date | 2026-11-05 |
| Promotion | GitHub PR `dev` → `main`, "Create a merge commit"; tag `v1.0.1` on the merge commit |
| QA'd revision | `dev` @ `8e59663` (PR #9 merged) |
| Previous release | [v1.0.0](RELEASE-v1.0.0.md) (`7433e43`) |

## Included PRs

| PR | GitHub | Branch | Review sign-off |
|---|---|---|---|
| [PR-013](../prs/PR-013.md) | [#9](https://github.com/wushandarshana/Policy-Forge/pull/9) | `chore/fine-tuning` | CI fully green (run 42001133213). Live `claude-review`: security-reviewer (Opus) **APPROVE**, 1 minor; test-engineer (Sonnet) **APPROVE** |
| [PR-014](../prs/PR-014.md) | (this release record) | `chore/release-v1.0.1` | Docs only |

## Delivered
- **Submission docs:**
  - README quick start, seed data, sign-in users, demo walkthrough and a **rubric map** for every graded item.
  - `docs/tdd.md` rebuilt on the real commits, with a red → green table for each sprint.
  - `docs/prs/README.md` maps PR-001..PR-012 to GitHub #1–#8 and to their merge commits.
  - The fix-loop traces link to their PRs.
  - `plugin.json` is now version 1.0.0 and lists all 14 CLAUDE.md files.
- **Evidence:**
  - `coverage/backend/jacoco.xml` is refreshed: 94.0% of instructions, 93.5% of lines, 83.9% of branches.
  - The first live Claude Agent SDK run of `scripts/ac_audit.py` is recorded in `specs/reviews/ac-audit-20261105-1439.md`.
- **CI review hardening (NFR-09.7, TDD `1ddebcf` → `0648760` → `b7aee16`):**
  - The review fails closed on any symlink that escapes the checkout.
  - The `.git` config and hooks, the CLI files and the review inputs are hashed and checked again before each pass.
  - `git` and `ac_audit.py` run without the API key.
  - The review worktree is created at the PR's head commit.

## QA on `dev` @ `8e59663`
`git diff v1.0.0 8e59663 -- backend/src frontend/src frontend/e2e` is empty, so the v1.0.0 application QA still holds:
1012 backend tests, 173 Vitest tests and 48 Playwright tests. The checks for the changed files were re-run:

| Command | Result |
|---|---|
| `python -m unittest discover -s scripts/tests` (CI venv) | **210** tests OK |
| `python scripts/ci/quality_gates.py` | **8/8** gates, including actionlint and the CI schemas |
| `python scripts/ac_audit.py --offline --no-write` | **93/93** covered, 0 parent-only |
| `premium_check.py spec-examples` | **9/9** PASS |
| `redact_evidence.py --check docs sprint-contracts specs/reviews` | 0 findings |
| GitHub Actions, push to `dev` at `8e59663` (run 42003112105) | All jobs green, including `claude-review` and `ci-gate` |

## Known residuals
These are unchanged from [v1.0.0](RELEASE-v1.0.0.md#known-residuals-and-open-items), with two updates:
- The symlink, tree-check and key-free child minors are now fixed.
- **New (minor):** the PR-controlled `scripts/ac_audit.py` still runs, without the key, next to the runner process
  that holds the key (security-reviewer, PR #9).
- **New (follow-up):** `ac_audit.py` should send the agent the strongest references for each criterion (live SDK audit
  note).
