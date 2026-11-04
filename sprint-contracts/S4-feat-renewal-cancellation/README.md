# Sprint 4: Renewal, Cancellation and Portfolio Dashboard (`feat/renewal-cancellation`)

| | |
|---|---|
| Harness | agent-harness 2.8.0, workflow backend |
| Routing | planner opus/xhigh · evaluator opus/high · generators sonnet/high (TASK-003 to TASK-005 at xhigh) |
| Verdict | **PASS**, 117/117 criteria on iteration 1 ([`specs/reviews/S4-sprint-eval.md`](../../specs/reviews/S4-sprint-eval.md)) |
| Commits | Made during the sprint by generators, locally, in TDD order (`test(red)` before `feat(green)`). Pushed only through the PR. |

## Run history

| Run | Result | Notes |
|---|---|---|
| `wf_1cc651f8-356` | Plan | 7 tasks, 118 criteria, with gaps G-1 to G-6 for the checkpoint. |
| Checkpoint | Approved with G-6 vetoed | The user approved G-1 to G-5 at their defaults and vetoed G-6 (SG-1 stays an accepted v1 limitation). The plan was edited to 117 criteria. |
| `wf_16966234-c44` | **PASS on iteration 1** | 9 agents, no retries. Task criteria were passed as pointers to `approved-plan.json`. |

## Spec changes made before code (spec is truth)
- `7d95f67 spec:` renewal payment (G-3), current paid term (G-5), cancellation reasons (G-4), SG-1 accepted (G-6 vetoed).
- `404b5e4 spec:` the security review's fixes, decided by the user: no backdated cancellation (AC-08.6), re-pricing a
  pending offer on a sum-insured change (AC-07.7), a bounded admin job date with a test-only switch (AC-07.8), and a
  bounded dashboard `asOf` (AC-11.6).

## Contract
- [`sprint-contract.md`](sprint-contract.md) is the approved plan, and [`approved-plan.json`](approved-plan.json) is
  its structured, post-checkpoint form.
- Contracted ACs: AC-07.1–07.6, AC-08.1–08.5, AC-10.3 (API half), AC-11.1–11.5, NFR-03.1, NFR-04.1, NFR-04.3,
  NFR-04.4, plus the security carry-overs (no Basic challenge, a fixed `instance` URN, the shared key sanitiser, a 503 on
  sequence exhaustion) and the sample-policy seed.

## Tasks and TDD evidence

| Task | Title | Model | Progress | Red | Green |
|---|---|---|---|---|---|
| TASK-001 | Domain rules: GracePeriodPolicy, RenewalPolicy (current paid term, offer, renewal dates, payment window), ProRataRefundCalculator and RefundQuote, the cancellation-date rule, the refund premium basis, portfolio figures and the shared unknown-key sanitiser | sonnet/high | [md](progress/TASK-001.md) | [log](evidence/TASK-001-red.log) | [log](evidence/TASK-001-green.log) |
| TASK-002 | Frontend: Renew and Cancel panels on Policy Detail, the Refunds tab, the Admin Dashboard page, the typed API client and Vitest | sonnet/high | [md](progress/TASK-002.md) | [log](evidence/TASK-002-red.log) | [log](evidence/TASK-002-green.log) |
| TASK-003 | Security hardening carry-overs (no Basic challenge, fixed problem instance, shared key sanitiser, exhausted policy-number sequence) | sonnet/xhigh | [md](progress/TASK-003.md) | [log](evidence/TASK-003-red.log) | [log](evidence/TASK-003-green.log) |
| TASK-004 | Renewal backend: V10 term rule version, current paid term, RenewalService cycle, RenewalScheduler, the admin job endpoint, the pay endpoint and the detail additions | sonnet/xhigh | [md](progress/TASK-004.md) | [log](evidence/TASK-004-red.log) | [log](evidence/TASK-004-green.log) |
| TASK-005 | Cancellation backend: V11 append-only refund records, refund preview, cancel, refunds in the detail, AC-10.3 API half | sonnet/xhigh | [md](progress/TASK-005.md) | [log](evidence/TASK-005-red.log) | [log](evidence/TASK-005-green.log) |
| TASK-006 | Admin portfolio dashboard API and the synthetic sample-policy seed | sonnet/high | [md](progress/TASK-006.md) | [log](evidence/TASK-006-red.log) | [log](evidence/TASK-006-green.log) |
| TASK-007 | App shell route for the Admin Dashboard, Playwright journeys (desktop + mobile), committed visual snapshots and sprint-wide verification | sonnet/high | [md](progress/TASK-007.md) | [log](evidence/TASK-007-red.log) | [log](evidence/TASK-007-green.log) |

## Evaluator results (HEAD `b29c511`)
- `./mvnw verify` (SPA packaged): BUILD SUCCESS, 479 unit and 550 integration tests, 0 failures. JaCoCo gates met.
- `npm test`: 148 passed. `npm run build`: OK. Playwright: 44/44 across desktop and mobile, twice in a row with no
  snapshot drift.
- `premium_check.py`: 4473.29, 10749.32, 8040.41, 6068.49, 6312.57, 0.00, 2929.23 and 14609.45 all match.
- Playwright MCP walkthrough: renew (6850.00) → pay → RENEWED; cancel preview (cooling-off 6850.00) → CANCELLED, with
  one Refunds row; admin dashboard at 1280 px and 375 px. A 401 sends no `WWW-Authenticate`, and every `instance` is a URN.

## Post-sprint reviews

| Review | Verdict |
|---|---|
| Security ([report](../../specs/reviews/security-feat-renewal-cancellation-20261104.md)) | First pass **REQUEST CHANGES** (1 blocker, 2 major, 2 minor). After the fixes, the re-review gave **APPROVE**: all five closed, 1 new minor. The S2/S3 minors are verified fixed. |
| Test quality ([report](../../specs/reviews/test-feat-renewal-cancellation-20261104.md)) | **APPROVE**: 0 blocker, 0 major, 7 minor |

## Review fix loop ([trace](../../docs/fix-loops/2026-11-04-s4-review-fixes.md))
Red [log](evidence/FIX-S4-red.log) → green [log](evidence/FIX-S4-green.log).
- **BLOCKER-1:** a customer could backdate the cancellation date into cooling-off and get a full refund after a year
  of cover. Cancellation dates must now be today or later (AC-08.6).
- **MAJOR-1:** a sum-insured change after the offer was priced renewed at the stale premium. The endorsement now
  re-prices the pending offer (AC-07.7).
- **MAJOR-2:** an unbounded admin job date could lapse the whole portfolio. The date is now bounded to today−30..today,
  and only tests can lift the bound (AC-07.8).
- **MINOR-1:** an extreme dashboard `asOf` gave a 500 (AC-11.6).
- **MINOR-2:** a renewal offer could race a cancel. The offer now takes the policy's lock.
- **Commits:** `6921161 test(red)` → `e79ade9 test:` → `39fae46 fix(green)`. The test review minors went in
  `2f27c0c test:`, and the repository rule in `b45bb0e docs:`.
- After the loop: 486 unit + 568 integration tests, 148 Vitest, Playwright 22/22 per project, AC audit 85/85.

## Follow-ups (minor, accepted)
- **Sprint 5:**
  - `-linux` snapshot baselines; stop tracking `frontend/coverage/lcov.info`.
  - An ArchUnit rule so only EndorsementService may call `repriceOffer` (security MINOR-A).
  - A race test between an endorsement re-price and a renewal payment.
  - An oracle cross-check for the AC-08.5 property test.
  - SG-2, SG-4 and SG-6 (see the plan).
- SG-1 (sum-insured endorsements don't re-underwrite) is an accepted v1 limitation, by user decision (G-6).

## Archive notes
Copied on 2026-11-04 from the gitignored workspace. `redact_evidence.py --check` reports 0 findings.
