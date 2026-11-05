# Sprint 3: Issuance and Endorsement (`feat/issuance-endorsement`)

| | |
|---|---|
| Harness | agent-harness 2.8.0, workflow backend |
| Routing | planner opus/xhigh · evaluator opus/high · generators sonnet/high (TASK-004 and TASK-005 at xhigh) |
| Verdict | **PASS**, 93/93 criteria on iteration 1 ([`specs/reviews/S3-sprint-eval.md`](../../specs/reviews/S3-sprint-eval.md)) |
| Commits | Made during the sprint by generators, locally, in TDD order (`test(red)` before `feat(green)`). Pushed only through the PR. |

## Run history

| Run | Result | Notes |
|---|---|---|
| `wf_a2fee484-467` | Plan | 6 tasks, 93 criteria. At the checkpoint the user approved the plan and gap G-1 (endorsement preview endpoint). |
| `wf_305bb29c-ee9` | **PASS on iteration 1** | 8 agents, ~6.1 M ms wall clock, no retries. Ran with the KD-014 foreground-only rule from the start. |

## Spec changes made before code (spec is truth)
- `819a246 spec:` quote ownership when signed in (AC-01.12, AC-01.13, AC-03.9). User decision on S2 security minor 4.
- `0bdda51 spec:` endorsement preview endpoint (plan G-1, approved at the checkpoint).

## Contract
- [`sprint-contract.md`](sprint-contract.md) is the approved plan, and [`approved-plan.json`](approved-plan.json) is
  its structured form.
- Contracted ACs: AC-01.12, AC-01.13, AC-03.9 (quote ownership); AC-05.1–05.4 (issuance); AC-06.1–06.7
  (endorsements, including the preview); AC-10.1, 10.2, 10.4 and the domain half of AC-10.3 (state machine);
  NFR-03.1, NFR-04.1, NFR-04.3, NFR-04.4.

## Tasks and TDD evidence

| Task | Title | Model | Progress | Red | Green |
|---|---|---|---|---|---|
| TASK-001 | Policy and endorsement domain rules: PolicyStatus, PolicyStateMachine, InvalidPolicyStateException, PolicyNumberFormat, issuance dates, EndorsementType, EndorsementValidator and EndorsementPremiumAdjuster | sonnet/high | [md](progress/TASK-001.md) | [log](evidence/TASK-001-red.log) | [log](evidence/TASK-001-green.log) |
| TASK-002 | Frontend pages: My Policies, Policy Detail and Endorse, the Issue action on Application Status, signed-in quote credentials, typed API client and Vitest | sonnet/high | [md](progress/TASK-002.md) | [log](evidence/TASK-002-red.log) | [log](evidence/TASK-002-green.log) |
| TASK-003 | Quote ownership: owner recorded on signed-in quotes, owner-only apply, anonymous claim | sonnet/high | [md](progress/TASK-003.md) | [log](evidence/TASK-003-red.log) | [log](evidence/TASK-003-green.log) |
| TASK-004 | Policy issuance API: policy persistence, PolicyNumberGenerator, POST /api/applications/{id}/issue, GET /api/policies/mine and GET /api/policies/{number} | sonnet/xhigh | [md](progress/TASK-004.md) | [log](evidence/TASK-004-red.log) | [log](evidence/TASK-004-green.log) |
| TASK-005 | Endorsement API: append-only endorsements, atomic version + transition, sum-insured re-rating, preview, state errors and history | sonnet/xhigh | [md](progress/TASK-005.md) | [log](evidence/TASK-005-red.log) | [log](evidence/TASK-005-green.log) |
| TASK-006 | App shell routes for the policy pages, Playwright journeys (desktop + mobile), committed visual snapshots and sprint-wide verification | sonnet/high | [md](progress/TASK-006.md) | [log](evidence/TASK-006-red.log) | [log](evidence/TASK-006-green.log) |

## Evaluator results (HEAD `fb4fb78`)
- `./mvnw -o verify` (SPA packaged): BUILD SUCCESS, 396 unit and 321 integration tests, 0 failures. JaCoCo gates met.
- `npm test`: 94 passed. `npm run build`: OK.
- Playwright: 28/28 across desktop and mobile Chromium, twice in a row with no snapshot drift.
- `premium_check.py`: AC-06.2 gives 9490.00 and 1330.85 (PASS).
- Playwright MCP walkthrough on the packaged app: quote → apply → issue (`PF-TL-2026-000001`, ACTIVE) → address change
  (ENDORSED, v1/v2) → sum-insured preview (9490.00, 2640.00) → confirm → My Policies. No horizontal scroll at 375 px.
- `ac_audit.py --offline`: 65/81 covered, 0 parent-only. Only AC-07, AC-08 and AC-11 (Sprint 4) are uncovered.

## Post-sprint reviews

| Review | Verdict |
|---|---|
| Security ([report](../../specs/reviews/security-feat-issuance-endorsement-20261103.md)) | **APPROVE**: 0 blocker, 0 major, 3 minor (all S2 carry-overs; S2 minor 4 resolved) |
| Test quality ([report](../../specs/reviews/test-feat-issuance-endorsement-20261103.md)) | **APPROVE**: 0 blocker, 0 major, 5 minor |

## Review fix loop ([trace](../../docs/fix-loops/2026-11-03-s3-review-fixes.md))
Red [log](evidence/FIX-S3-red.log) → green [log](evidence/FIX-S3-green.log).
- `e892374 test(red)` → `70206d6 fix(green)`: every fetch uses `credentials: 'omit'` (cached browser Basic credentials
  can no longer make a signed-out quote owned by someone else), the session is cleared on a 401 to an authenticated
  request, and unknown keys echoed by the issue endpoint are capped at 100 characters.
- `b542136 test:` the Endorse page not-found test is retagged from AC-10.4 to AC-06.1.
- After the loop: 396 unit + 322 integration, 100 Vitest, 28/28 Playwright, all green.

## Follow-ups (minor, accepted)
- **Sprint 4:** the AC-10.3 API half (plan P-6); the sample-policy seed (P-11); SG-1 (sum-insured endorsements don't
  re-underwrite); server-side Basic challenge suppression; raw-path `instance` (now also on issue, endorse, preview);
  a shared character-restricting sanitiser for echoed keys; ADMIN policy reads unaudited; a generic 500 on
  policy-number sequence exhaustion.
- **Sprint 5:** `-linux` snapshot baselines; stop tracking `frontend/coverage/lcov.info`.
- Tests minor 1 (`install();` added to a red test inside a green commit) and minor 5 (AC-06.3 injects at the
  repository proxy) are accepted as is.

## Archive notes
Copied on 2026-11-03 from the gitignored workspace. `redact_evidence.py --check` reports 0 findings.
