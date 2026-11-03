# Sprint 2: Application and Underwriting (`feat/application-underwriting`)

| | |
|---|---|
| Harness | agent-harness 2.8.0, workflow backend |
| Routing | planner opus/xhigh · evaluator opus/high · generators sonnet/high |
| Verdict | **PASS**, 92/92 criteria ([`specs/reviews/S2-sprint-eval.md`](../../specs/reviews/S2-sprint-eval.md)) |
| Commits | Made during the sprint by generators, locally, in TDD order (`test(red)` before `feat(green)`). Pushed only through the PR. |

## Run history

| Run | Result | Notes |
|---|---|---|
| `wf_e4b8b7d1-027` | FAIL after 3 iterations | TASK-001 to TASK-005 DONE and committed (last green `1a77eb4`). TASK-006's generator backgrounded a long command and ended its turn, which terminated it (**KD-014**). As a BLOCKED task it was graded SKIP and never retried. Records: [`run1/`](run1/) |
| `wf_273f9690-e40` | ERROR, 0 tokens | CrowdStrike TLS inspection blocked agent API calls (**KD-015**). |
| `wf_8f395fc4-3cf` | **PASS on iteration 1** | TASK-006-only follow-up with the KD-014 foreground-only rule. The args were rebuilt byte-exact from the failed run ([`run1/followup-resume-args.json`](run1/followup-resume-args.json)). Commits: `8cb4242 test(red)`, then `b941f95 feat(green)`. |

## Contract
- [`sprint-contract.md`](sprint-contract.md) is the approved plan, and [`approved-plan.json`](approved-plan.json) is
  its structured form.
- Contracted ACs: AC-03.1–03.8 (application and KYC), AC-04.1–04.6 (underwriting decisions), AC-09.1–09.7 (workbench
  and override), NFR-03.1 (no PII in logs), NFR-04.1 (auth boundary), NFR-04.3 and NFR-04.4 (JSON-only, body limit).

## Tasks and TDD evidence

| Task | Title | Progress | Red | Green |
|---|---|---|---|---|
| TASK-001 | Underwriting rules engine (domain): UnderwritingRuleEvaluator, condition evaluation, UnderwritingOutcome and ReasonCode | [md](progress/TASK-001.md) | [log](evidence/TASK-001-red.log) | [log](evidence/TASK-001-green.log) |
| TASK-002 | Frontend page components: Apply, Application Status and Underwriter Workbench, with a Basic-auth session context, typed API client and Vitest | [md](progress/TASK-002.md) | [log](evidence/TASK-002-red.log) | [log](evidence/TASK-002-green.log) |
| TASK-003 | Application domain: KycValidator, application status lifecycle, and the manual-decision and override rules | [md](progress/TASK-003.md) | [log](evidence/TASK-003-red.log) | [log](evidence/TASK-003-green.log) |
| TASK-004 | Application submission API: persistence, append-only decisions, startup seed, POST /api/applications and GET /api/applications/mine | [md](progress/TASK-004.md) | [log](evidence/TASK-004-red.log) | [log](evidence/TASK-004-green.log) |
| TASK-005 | Underwriter workbench and admin override API: queue with masked KYC, manual decision, override, actor-ID audit and the role boundary | [md](progress/TASK-005.md) | [log](evidence/TASK-005-red.log) | [log](evidence/TASK-005-green.log) |
| TASK-006 | App shell routing, Playwright journeys (desktop + mobile), committed visual snapshots and sprint-wide verification | [md](progress/TASK-006.md) | [log](evidence/TASK-006-red.log) | [log](evidence/TASK-006-green.log) |

## Evaluator results (follow-up run, HEAD `b941f95`)
- `./mvnw -B verify`: BUILD SUCCESS. 251 unit tests (Maven summary; the per-class XML total is 219, the same run counted
  differently) and 212 integration tests, 0 failures. JaCoCo gates met.
- `npm test`: 38 passed (95% statements). `npm run build`: OK.
- Playwright: 16/16 across desktop and mobile Chromium, twice in a row with no snapshot drift. Baselines are in
  `frontend/e2e/__snapshots__/`.
- Playwright MCP walkthrough on the packaged app: quote → apply → REFERRED (UW-TL-002) → underwriter approves →
  APPROVED, with KYC masked and no horizontal scroll at 375 px.
- `ac_audit.py --offline`: 47/78 covered; every Sprint 1 and Sprint 2 ID is covered.

## Post-sprint reviews

| Review | Verdict |
|---|---|
| Security ([report](../../specs/reviews/security-feat-application-underwriting-20261029.md)) | **APPROVE**: 0 blocker, 0 major, 4 minor |
| Test quality ([report](../../specs/reviews/tests-feat-application-underwriting-20261029.md)) | **APPROVE**: 0 blocker, 0 major, 4 minor |

## Follow-ups (minor, accepted)
- **Security 1.** A 401 sends a Basic challenge and the sign-in form doesn't verify credentials, so the browser can
  cache Basic credentials that the app's Sign out can't clear. Fix: no challenge for SPA requests, verify at sign-in,
  sign out on 401.
- **Security 2, 3.** Unknown keys in `errors[].field` and the raw path in `instance` are echoed back. Fix: cap and
  sanitise the key; use a fixed `instance` (a Sprint 1 leftover).
- **Security 4.** Quotes have no owner, so another customer holding a quote ID could apply first. **This must be
  decided in the issuance spec before Sprint 3.**
- **Tests 1.** Snapshots are win32-only; Linux CI needs `-linux` baselines (Sprint 5).
- **Tests 2, 3.** The Workbench snapshot masks only the `h2`; the queue lookup assumes no pagination.

## Archive notes
Copied on 2026-11-03 from the gitignored workspace. `redact_evidence.py --check` reports 0 findings.
