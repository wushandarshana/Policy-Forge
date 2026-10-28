# Sprint 1: Catalog and Quote (`feat/catalog-quote`)

| | |
|---|---|
| Harness | agent-harness 2.8.0, workflow backend |
| Routing | planner opus/xhigh · evaluator opus/high · generators sonnet/high |
| Started | 2026-10-28T05:27:16Z |
| Iterations | 2 of 3 (iteration 1 FAIL → retries TASK-002, TASK-006, TASK-007 → iteration 2 PASS) |
| Verdict | **PASS** ([`specs/reviews/S1-sprint-eval.md`](../../specs/reviews/S1-sprint-eval.md)) |
| Commits during sprint | none (user decision at the plan checkpoint; see the contract's Interpretation) |

## Contract
- [`sprint-contract.md`](sprint-contract.md): the approved plan. The planner wrote it, the orchestrator edited it at the
  checkpoint (git-history criteria replaced by red/green log criteria), and the user approved it.
- [`approved-plan.json`](approved-plan.json): the structured plan that drove scheduling. It was rebuilt from the
  contract file and validated.

## Contracted acceptance criteria
AC-01.1–01.8 (quote engine) · AC-02.1–02.7 (catalog) · NFR-04.2 (public quote read omits health inputs) ·
NFR-06.1, NFR-06.2 (correlation ID / JSON logs) · NFR-07.1 (health) · NFR-08 (ArchUnit).

## Tasks and TDD evidence

| Task | Scope | Progress | Red (failing first) | Green |
|---|---|---|---|---|
| TASK-001 | Backend scaffold, health, logging, security, errors | [md](progress/TASK-001.md) | [log](evidence/TASK-001-red.log) | [log](evidence/TASK-001-green.log) |
| TASK-002 | React Quote page + Vitest | [md](progress/TASK-002.md) | [log](evidence/TASK-002-red.log) | [log](evidence/TASK-002-green.log) |
| TASK-003 | Catalog, rule files, validator, append-only persistence | [md](progress/TASK-003.md) | [log](evidence/TASK-003-red.log) | [log](evidence/TASK-003-green.log) |
| TASK-004 | ArchUnit rules + negative fixtures | [md](progress/TASK-004.md) | [log](evidence/TASK-004-red.log) | [log](evidence/TASK-004-green.log) |
| TASK-005 | Rating models, PremiumCalculator | [md](progress/TASK-005.md) | [log](evidence/TASK-005-red.log) | [log](evidence/TASK-005-green.log) |
| TASK-006 | Quote persistence + public quote API | [md](progress/TASK-006.md) | [log](evidence/TASK-006-red.log) | [log](evidence/TASK-006-green.log) |
| TASK-007 | SPA packaging, Playwright smoke, sprint-wide checks | [md](progress/TASK-007.md) | [log](evidence/TASK-007-red.log) | [log](evidence/TASK-007-green.log) |

Cross-checks: [`TASK-007-id-evidence.log`](evidence/TASK-007-id-evidence.log) shows that each of the 19 IDs failed
before it passed. [`TASK-007-verify.log`](evidence/TASK-007-verify.log) is the full `./mvnw verify` output.

## Evaluator results (iteration 2)
- `./mvnw clean verify`: BUILD SUCCESS, 91 surefire + 56 failsafe tests, 0 failures. JaCoCo domain ≥ 80% and overall
  ≥ 70% met.
- `npm test`: 9 passed, 96.46% line coverage. `npm run build`: OK.
- `npx playwright test`: 2 passed (desktop-chromium, mobile-chromium).
- `premium_check.py spec-examples`: 6850.00 / 15150.00 / 11350.00 / 18150.00 all PASS.
- `ac_audit.py --offline`: all 19 sprint IDs COVERED (20/67 overall).
- Playwright MCP walkthrough of the Quote page at 375 px (iteration 1): no horizontal overflow.

## Post-sprint reviews

| Review | Rounds | Final verdict |
|---|---|---|
| Security ([report](../../specs/reviews/security-feat-catalog-quote-20261028.md)) | 3: CHANGES_REQUESTED (F-1 CSRF, F-2 deny-list leak) → CHANGES_REQUESTED (R-1 encoded-path size bypass) → **APPROVE** | APPROVE |
| Test quality ([report](../../specs/reviews/tests-feat-catalog-quote-20261028.md)) | 1 | APPROVE (M1 "uncommitted" is accepted, per the user's decision) |

The security findings were fixed through a recorded TDD fix loop over two rounds:
[`docs/fix-loops/2026-10-28-s1-security-fixes.md`](../../docs/fix-loops/2026-10-28-s1-security-fixes.md).
Spec additions: NFR-04.3, NFR-04.4, AC-01.9, AC-01.10, AC-01.11. Knowledge deposits: KD-007 to KD-011.
Final state: `./mvnw verify` 106 unit + 75 integration tests green, Vitest 11/11, Playwright 2/2.

## Archive notes
- Copied from the gitignored `.sprint/20261028-052716/` on 2026-10-28. Content is unchanged except for an archive
  header and two redactions in `evidence/`. The first covers 3 ephemeral Spring "generated security password" values
  in red-phase logs, printed before the synthetic users were configured. The second covers 10 Basic `Authorization`
  header values from MockMvc dumps; they were synthetic `user:password` credentials (security review F-5).
- Open item from the plan: **J8**. A vehicle older than 15 years, or a term-life age outside 18–65, gets a 400 at quote
  time, so UW-MO-002 can't fire in the quote → application flow. The quote spec wording needs clarifying in a later
  sprint.
