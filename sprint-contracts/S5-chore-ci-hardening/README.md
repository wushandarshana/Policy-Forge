# Sprint 5: CI hardening (`chore/ci-hardening`)

| | |
|---|---|
| Harness | agent-harness 2.8.0, workflow backend |
| Routing | planner opus/xhigh · evaluator opus/high · generators sonnet (TASK-001, 002 and 004 at xhigh) |
| Verdict | **PASS**, 74/74 criteria on iteration 1 ([`specs/reviews/S5-sprint-eval.md`](../../specs/reviews/S5-sprint-eval.md)) |
| Commits | Made locally by the generators and the fix loop, in TDD order. Pushed only through the PR. |

## User decisions
- **CI platform:** build both GitLab CI (the PDF requirement) and GitHub Actions (runs on `origin`).
- **Claude review:** required. Two passes per run, with the security-reviewer on Opus and the test-engineer on Sonnet,
  each capped at 5 USD.
- **Checkpoint:** all defaults (G-1 to G-8) approved.
- **After the security review:** the key moves to a protected GitHub Environment `claude-review` with the owner as
  required reviewer, plus CODEOWNERS (spec `be6b9f3`).

## Run history

| Run | Result | Notes |
|---|---|---|
| `wf_5902fdb1-017` | Plan | 5 tasks, 74 criteria, G-1 to G-8. Amendments A (`4730168`, spec) and B (`f9a1664`, substrate) were committed at the checkpoint. |
| `wf_3c5d8d87-174` | **PASS on iteration 1** | 7 agents, no retries. POST-PUSH steps are listed separately and were not graded. |

## Spec changes made before code
- `4730168 spec:` NFR-08.1 and NFR-09.1–09.6. SG-3, SG-5, SG-6 and SG-7 recorded as accepted v1 limitations.
- `be6b9f3 spec:` NFR-09.7, the sandboxed review, and the key in the protected environment. Follows the security
  review.
- `47857df spec:` CODEOWNERS is not enforced while there is a single owner, and the accepted same-repo workflow
  residual. Follows the re-review.

## Tasks and TDD evidence

| Task | Title | Model | Progress | Red | Green |
|---|---|---|---|---|---|
| TASK-001 | Backend hardening: the repriceOffer caller ArchUnit rule (NFR-08.1), the re-price versus pay race IT (AC-07.7) and the AC-08.5 premium_check.py oracle cross-check | sonnet/xhigh | [md](progress/TASK-001.md) | [log](evidence/TASK-001-red.log) | [log](evidence/TASK-001-green.log) |
| TASK-002 | CI scripts: the Claude review runner and gate, the quality-gates runner, the pinned tool fetcher, the LF check, the API-key redaction, the AC audit's Python test glob and their unittest suite | sonnet/xhigh | [md](progress/TASK-002.md) | [log](evidence/TASK-002-red.log) | [log](evidence/TASK-002-green.log) |
| TASK-003 | Frontend and E2E: untrack lcov, the exact Playwright pin, the visual-baseline policy and helper (NFR-09.5), the packaged-JAR server option, and local Playwright runs | sonnet/high | [md](progress/TASK-003.md) | [log](evidence/TASK-003-red.log) | [log](evidence/TASK-003-green.log) |
| TASK-004 | CI definitions: GitHub `ci.yml` and `linux-snapshots.yml`, `.gitlab-ci.yml`, the executable `mvnw`, the config tests (NFR-09.1–09.3) and a local lint plus quality-gates run | sonnet/xhigh | [md](progress/TASK-004.md) | [log](evidence/TASK-004-red.log) | [log](evidence/TASK-004-green.log) |
| TASK-005 | Sprint-wide verification, the POST-PUSH runbook and the knowledge-deposit drafts | sonnet/high | [md](progress/TASK-005.md) | — | [log](evidence/TASK-005-green.log) |

## Post-sprint reviews

| Review | Verdict |
|---|---|
| Security ([report](../../specs/reviews/security-chore-ci-hardening-20261104.md)) | First pass **REQUEST CHANGES**: 1 blocker (the reviewer could write files through `git --output` and get them run by a step that holds a token), 3 major (PR code controls the key and the gate; secrets readable through tools; GitLab token scope), 7 minor. After the user's decision and the fix loop, the re-review gave **APPROVE**: all closed, 5 new minors. |
| Test quality ([report](../../specs/reviews/test-chore-ci-hardening-20261104.md)) | **APPROVE**: 0 blocker, 0 major, 5 minor |

## Review fix loop ([trace](../../docs/fix-loops/2026-11-04-s5-review-fixes.md))
Red [log](evidence/FIX-S5-red.log) → green [log](evidence/FIX-S5-green.log). Commits: `b43bdac test(red)` →
`a56f01a`, `14d1541 test:` → `e21e813 fix(green)` → `ddc25dd`, `63e0fdf ci:` → `9d3fdc8 docs:`.
- **Reviewer tools:** no shell tool, and reads limited to the checkout (`/proc`, system roots, `.git` and home
  credentials are denied). The diff and log are given as files.
- **Environment:** the child environment is an allowlist, so no platform tokens or registry passwords reach the CLI.
- **Tree check:** the review fails closed if any file outside `ci-out/` changes.
- **Comment:** a separate job, holding no key, that runs base-branch code, edits only its own bot comment, and accepts
  `https` URLs only.
- **Aggregator:** `ci-gate` on both platforms, so a skipped review can't count as passing.
- **Ownership and installs:** CODEOWNERS, plus locked installs (`npm ci` from a lockfile, `pip --require-hashes`).
- **After the loop:**
  - script tests: 170 OK, quality gates 8/8, AC audit 93/93;
  - Maven: 490 unit + 570 integration tests;
  - frontend: Vitest 173, Playwright 48 (24 per project).

## POST-PUSH (orchestrator and owner)
1. The owner creates the `claude-review` environment with themselves as required reviewer, adds the key as an
   environment secret, and deletes the repository-level secret.
2. Push and open the PR. On the first real run, confirm that CLI 2.1.311 accepts the restricted-tool flags, that the
   deny rules hold, and that the lockfile installs work on Linux.
3. Add the label `update-linux-snapshots`, download the 20 `-linux` baselines, review them, and commit them in their
   own commit.
4. Amendment D: KD-016 onward. Then require `ci-gate` in the ruleset, with the owner's approval.

## Follow-ups (minor, accepted)
- Security NEW-MINOR-1: fail closed on a symlink that resolves outside the repository.
- Security NEW-MINOR-2: harden the tree check (`core.fsmonitor`, hooks path, hash `.git/config` and the CLI binary,
  re-verify the inputs).
- Security NEW-MINOR-5: keep the key out of the `git` and `ac_audit.py` child processes.
- A `/proc` canary pass, and proxy variables in the allowlist if a runner needs them.
- A GitLab copy of CODEOWNERS, if GitLab code-owner approval is wanted.

## Archive notes
Copied on 2026-11-05 from the gitignored workspace. `redact_evidence.py --check` reports 0 findings.
