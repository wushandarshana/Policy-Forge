> Archived from `.sprint/20261103-130317/` on 2026-11-03 · branch `feat/issuance-endorsement` · harness agent-harness 2.8.0. Content below is unchanged from the harness artifact.

# Sprint Evaluation — Iteration 1

Evaluator runs (2026-11-03, branch `feat/issuance-endorsement` @ `fb4fb78`, clean tree):
- `./mvnw -o verify` in `backend/` (full, SPA packaged, frontend not skipped): BUILD SUCCESS. Surefire 396/0/0, failsafe 321/0/0, JaCoCo check-overall and check-domain "All coverage checks have been met". `backend/pom.xml` is unchanged since `819a246`.
- `npm test`: 13 files, 94 tests passed (statements 95.97%). The tree stayed clean afterwards, so the committed lcov is current. `npm run build`: passes.
- `npx playwright test --update-snapshots=none` (desktop-chromium and mobile-chromium): 28 passed, then 28 passed on a second consecutive run, with no snapshot drift.
- `python scripts/ac_audit.py --offline --no-write`: 65/81 covered, 0 parent-only. Only AC-07.x, AC-08.x and AC-11.x are NOT COVERED. The 8 NFR IDs are among the 81 audited IDs, and none is uncovered.
- `python scripts/redact_evidence.py --check .sprint/20261103-130317/sprint-progress`: total 0, exit 0.
- `premium_check.py spec-examples` (oracle): AC-06.2 gives 9490.00 and 1330.85, both PASS.
- Git: `dev` = 246e7cb and `main` = 8e265d6, locally and in `git ls-remote origin`. No `origin/feat/issuance-endorsement` exists. The `git diff 819a246..HEAD` on migrations adds only V7, V8 and V9, none of which uses a floating-point type. The only changes outside frontend/backend/.sprint are the G-1 spec files. No PUT, PATCH or DELETE mapping exists in `backend/src/main`.
- Playwright MCP walkthrough on the packaged jar: customer1 quoted TL (6850.00), signed in, applied (APPROVED, AUTO_BIND), and used the Issue panel with a blank date. That navigated to `#/policies/PF-TL-2026-000001`: ACTIVE, the Status tab showing "— → ACTIVE ISSUANCE", and v1 with the KYC address. Next, CHANGE_ADDRESS returned to the detail page showing ENDORSED, v1 with the old address and v2 with the new one. For CHANGE_SUM_INSURED, Confirm was disabled until the preview, and the preview showed 6850.00, 9490.00 and 2640.00 with 365/365 days. Confirming returned to the detail page with SI 7000000.00, premium 9490.00, and Endorsements v1→v2 0.00 and v2→v3 2640.00. My Policies showed the card with ENDORSED, 7000000.00, 9490.00, due date 2027-11-03 and 2 endorsements. At 375px, scrollWidth was at most innerWidth on the Endorse, Detail and My Policies pages. The only console error was a favicon 404.
- Live curl probes: the detail keys are exactly C3 and the header is exactly C1. riskInputs has no bmi or healthDeclarations, and no name, DOB, Aadhaar or PAN appears in the detail. `refunds: []`. The preview returned 200 and wrote nothing (endorsementCount and currentVersionNo unchanged). PUT, PATCH and DELETE on endorsements return 405. Detail returns 403 for underwriter1, 200 for admin1 and 404 for customer2. Anonymous `mine` returns 401, and text/plain endorse returns 415. A bad relationship returns 400 `NOMINEE_RELATIONSHIP_INVALID` with field `nominee.relationship` and a fixed detail. Port 8080 was freed after the walkthrough.

## Results

| Task | Criterion | Status | Reason |
|------|-----------|--------|--------|
| TASK-001 | Generates PolicyStatus, PolicyStateMachine and InvalidPolicyStateException via the skill, null from = new policy | PASS | The classes exist in `domain.policy`, `PolicyStateMachineTest` passes, and null→ACTIVE is the only allowed null cell in the surefire report |
| TASK-001 | Proves AC-10.1 with 30 (from,to) pairs, 13 allowed and 17 forbidden, all named "AC-10.1:" | PASS | The surefire XML has 30 `AC-10.1: X -> Y allowed=` cases, 13 of them allowed=true, plus a 30/13/17 count test |
| TASK-001 | Proves AC-10.3 at domain level (LAPSED→CANCELLED throws) | PASS | Test "AC-10.3: a LAPSED policy cannot be CANCELLED…" passes |
| TASK-001 | Implements PolicyNumberFormat and proves AC-05.1 number formats | PASS | `PolicyNumberFormatTest` is green, and the live issue produced `PF-TL-2026-000001` |
| TASK-001 | Implements issuance date rules (today..today+60, expiry = eff + months − 1 day) and proves AC-05.1 | PASS | `IssuanceDatesTest` is green, and the live policy shows 2026-11-03 → 2027-11-02 |
| TASK-001 | Implements EndorsementType and an EndorsementValidator pure function that defaults the effective date to today | PASS | Present, with test "AC-06.6: a missing effective date defaults to today…" |
| TASK-001 | Applies E1–E11 with exact codes; E1 short-circuits; E2–E10 collected with fixed messages | PASS | Tests "AC-10.2: E1 short-circuits…", "AC-06.6: E2 to E10 are all collected in E-order…" and "AC-06.1: messages are fixed text…" pass |
| TASK-001 | Proves the matrix with one row per E1–E11 and P-4 display names | PASS | The `validationMatrix` rows are named AC-10.2 (E1 CANCELLED and LAPSED), AC-06.6 (E2, E3), AC-06.4 (E4), AC-06.5 (E5–E7), AC-06.2 (E8, E9) and AC-06.1 (E10, E11) |
| TASK-001 | Proves AC-06.5 nominee boundary (80%+30% exceeded, +20% ok, 4th = limit) | PASS | The named tests pass |
| TASK-001 | Implements EndorsementPremiumAdjuster (scale 10, HALF_UP 2dp) and modified risk inputs per product | PASS | `EndorsementPremiumAdjusterTest` is green, and MoneyPrecisionArchTest passes |
| TASK-001 | Proves AC-06.2 adjuster values 1330.85 (184/365), −1330.85, 2640.00, 7.23 | PASS | The asserts are at lines 78–109 of the adjuster test, and the oracle agrees |
| TASK-001 | Proves AC-06.2 re-rating with PremiumCalculator: TL 9490.00, MOTOR 17025.00 | PASS | The tests at lines 154–176 pass, and the live preview gave 9490.00 |
| TASK-001 | Imports only java.* and domain; no double/float; DomainPurity and MoneyPrecision ArchTests pass | PASS | ArchUnit is green in the full verify |
| TASK-001 | test(red) before feat(green); logs saved naming the ACs | PASS | 26c2f23 red precedes 33d8c73 green, and both logs exist and pass the redaction check |
| TASK-001 | Passes mvnw verify with ArchUnit green and JaCoCo ≥80% domain / ≥70% overall | PASS | The evaluator's full verify passes both JaCoCo checks |
| TASK-002 | Confirms or creates the G-1 spec commit first | PASS | 0bdda51 `spec: endorsement preview endpoint…` is the first commit after 819a246 and touches only the two spec files |
| TASK-002 | Typed client for C1–C5; parses C6 problems | PASS | `frontend/src/api/policies.ts` and `types.ts` exist. Live E2E and MCP runs matched the API wire shapes, and TASK-006 found no mismatches |
| TASK-002 | Sends Basic on POST /api/quotes when signed in and none when signed out; a 401 shows a fixed message with no retry (AC-01.13) | PASS | Three AC-01.13 Vitest tests pass, and the AC-01.13 Playwright journey passes on both projects |
| TASK-002 | Issue panel for APPROVED only; `{}` when blank; navigation callback; 409 and 400 handling; Issued state | PASS | `ApplicationStatusPage.issue.test.tsx` passes. Live, the Issue panel shows for APPROVED, and a blank date issued and navigated to the detail page |
| TASK-002 | My Policies renders C2 in API order with all fields and a link | PASS | The Vitest test passes, and the live card showed every C2 field verbatim |
| TASK-002 | Policy Detail header, the four tabs, null from as a dash, 404 message, and the Endorse link only for in-force statuses | PASS | The Vitest tests pass. Live, the header, Versions, Endorsements, Status (— → ACTIVE) and the Refunds tab are present |
| TASK-002 | Endorse page with the type selector and per-type forms; Confirm posts the exact C4 body | PASS | The Vitest tests pass, and the live address and sum-insured endorsements returned 201 |
| TASK-002 | Confirm requires a successful preview on a sum-insured change; values verbatim; editing clears the preview | PASS | Live, Confirm was disabled before the preview. The "edit clears preview" AC-06.2 Vitest test passes |
| TASK-002 | errors[] shown at their fields; fixed messages for C6 codes and the 409s; client-side checks cover required fields only | PASS | The AC-06.4, AC-06.5 and AC-10.2 Vitest tests and the AC-06.5 E2E pass |
| TASK-002 | No money arithmetic; values verbatim; noMoneyArithmetic.test.ts passes | PASS | `noMoneyArithmetic.test.ts` passes |
| TASK-002 | data-testids everywhere; grid/flex with the 640px breakpoint; no horizontal scroll at 375px | PASS | The MCP check at 375px found no overflow on Endorse, Detail or My Policies, and the mobile Playwright `expectNoHorizontalScroll` passes |
| TASK-002 | Vitest tests titled AC-01.13, 05.1, 05.3, 06.1, 06.2, 06.4, 06.5, 06.6, 10.2, 10.4 | PASS | All 94 Vitest tests pass, and the AC titles are present |
| TASK-002 | Leaves shell routing unchanged; existing tests pass; nothing outside frontend/; no new npm deps | PASS | `App.tsx` changed only in TASK-006, and green commit 12415da touches only `frontend/` |
| TASK-002 | test(red) before feat(green); logs saved | PASS | 27c5062 precedes 12415da, and both logs exist |
| TASK-002 | Passes npm test (lcov refreshed) and npm run build | PASS | The evaluator re-ran both: green, with the tree clean afterwards |
| TASK-003 | Flyway V7 nullable owner; V1–V6 unchanged; owner set on insert only | PASS | The migration diff since 819a246 adds V7/V8/V9 only, and the progress notes `updatable = false` |
| TASK-003 | POST /api/quotes stays public; owner only for valid CUSTOMER; 401 on invalid credentials and nothing stored | PASS | QuoteOwnershipIT is green in the full verify. Live, an anonymous quote succeeded |
| TASK-003 | Proves AC-01.12 (owner stored, response key sets unchanged, underwriter → no owner, invalid → 401) | PASS | QuoteOwnershipIT passes in verify, and AC-01.12 is COVERED in the audit |
| TASK-003 | U-2 step 2: another customer's owned quote gets QUOTE_NOT_FOUND before the other checks | PASS | QuoteOwnershipIT passes |
| TASK-003 | Proves AC-03.9 (JSON-equal body, owner 201, anonymous claim then 409, expired → NOT_FOUND) | PASS | QuoteOwnershipIT passes, and AC-03.9 is COVERED |
| TASK-003 | Seeds the U-14 quote owned by customer2; seed tests pass | PASS | ApplicationSeedIT passes in verify |
| TASK-003 | Existing AC-01/03/04/09 tests pass; re-proves NFR-04.1 for the public quote endpoints | PASS | The full verify is green, and an anonymous quote worked live |
| TASK-003 | Logs IDs only; ArchUnit passes | PASS | ArchUnit is green, and the log-capture ITs are green |
| TASK-003 | test(red) before feat(green); logs saved | PASS | a05f186 precedes b44053e |
| TASK-003 | Passes mvnw verify | PASS | The evaluator's full verify passes |
| TASK-004 | Flyway V8 tables; V1–V7 unchanged; no floating-point column | PASS | The diff adds only V8, and a grep for double/float/real in V7–V9 finds nothing |
| TASK-004 | Immutable version and transition entities; single-mutator PolicyEntity; markPaid-only term; NFR-02 ArchTest checks | PASS | AppendOnlyArchTest is green. The `saveAndFlush` on the non-append-only PolicyRepository is noted in the progress file |
| TASK-004 | PolicyNumberGenerator in service, inside the transaction, issue-year counter | PASS | PolicyNumberingIT passes |
| TASK-004 | POST issue: CUSTOMER, JSON only, C1, P-5 order, single transaction writing the C7 records and audit | PASS | PolicyIssuanceIT passes. Live, the issue returned ACTIVE with v1, the ISSUANCE transition and the APPLICATION status ISSUED flow |
| TASK-004 | Proves AC-05.1 on a fixed clock (number, dates, SI and premium equal to the quote, ACTIVE) | PASS | PolicyIssuanceIT passes in verify |
| TASK-004 | AC-05.1 date window and MO/HH prefixes | PASS | PolicyIssuanceIT and PolicyNumberingIT pass |
| TASK-004 | Proves AC-05.2 (50 concurrent distinct, contiguous, per-product, first = 000001) | PASS | PolicyNumberingIT passes |
| TASK-004 | Proves AC-05.3 (REFERRED and DECLINED 409, re-issue 409, concurrent same-application issue gives one 201) | PASS | PolicyIssuanceIT passes |
| TASK-004 | Proves AC-05.4 (one transition, one version, status = last to) | PASS | PolicyIssuanceIT passes |
| TASK-004 | 404 for another customer's, unknown or malformed application ID, with nothing stored | PASS | PolicyIssuanceIT passes |
| TASK-004 | GET mine (C2) and GET detail (C3, P-9 allow-list); nextDue and endorsementCount; 404 and 403 | PASS | PolicyViewIT passes. Live, the detail keys are exactly C3, with no health or KYC fields and `refunds: []`. customer2 gets 404 and underwriter1 gets 403 |
| TASK-004 | NFR-04.1 role boundary for the three endpoints | PASS | RoleBoundaryIT passes. Live, anonymous `mine` gets 401, underwriter gets 403 and admin gets 200 on detail |
| TASK-004 | NFR-04.3 415 and NFR-04.4 413, including the encoded path | PASS | PolicyBodyLimitIT and PolicyIssuanceIT pass |
| TASK-004 | NFR-03.1 log capture for issuance and the GETs | PASS | PolicyLogCaptureIT passes |
| TASK-004 | Thin controllers; ArchUnit passes | PASS | LayeredArchTest is green |
| TASK-004 | test(red) before feat(green); logs saved | PASS | 48e21e7 precedes 53a2f9b |
| TASK-004 | Passes mvnw verify | PASS | The evaluator's full verify passes |
| TASK-005 | Flyway V9 endorsement table with NUMERIC(19,2) money; V1–V8 unchanged | PASS | The diff adds only V9, with no floating-point types |
| TASK-005 | Immutable EndorsementEntity; AC-06.7 ArchTest existence check | PASS | AppendOnlyArchTest is green |
| TASK-005 | POST endorsements: CUSTOMER owner, JSON only, C4, P-5 order, delegates to the domain | PASS | EndorsementIT passes. Live, address and sum-insured endorsements returned 201 and text/plain got 415 |
| TASK-005 | One transaction appends the endorsement, version n+1 and the ENDORSED transition; concurrent race gives 409 POLICY_UPDATE_CONFLICT | PASS | EndorsementConflictIT and EndorsementAtomicityIT pass. Live, the transitions were ACTIVE, ENDORSED, ENDORSED |
| TASK-005 | InvalidPolicyStateException → 409 invalid-policy-state with a fixed detail | PASS | EndorsementStateIT passes |
| TASK-005 | Proves AC-06.1 (before 1 and after 2; v1 keeps the old address; ENDORSED) | PASS | EndorsementIT passes, and the same result was reproduced live in the UI |
| TASK-005 | Proves AC-06.2 on a fixed clock (9490.00 and 1330.85; v2 values; newer rule-version isolation) | PASS | EndorsementRatingIT and EndorsementRuleVersionIT pass |
| TASK-005 | Proves AC-06.2 for MOTOR and HOUSEHOLD, matching the oracle | PASS | EndorsementRatingIT passes (MOTOR 15150.00 → 17025.00) |
| TASK-005 | Proves AC-06.3 atomicity with injected failure and no production hook | PASS | EndorsementAtomicityIT passes, using a test `@Primary` wrapper |
| TASK-005 | Proves AC-06.4 (MOTOR ADD_NOMINEE gets 400 NOMINEE_NOT_SUPPORTED, nothing written) | PASS | EndorsementIT passes |
| TASK-005 | Proves AC-06.5 (80% + 30% exceeded; fourth nominee limit) | PASS | EndorsementIT passes, and the AC-06.5 E2E passes |
| TASK-005 | Proves AC-06.6 (sequences 1–3, versions 1–4, before-last-endorsement and out-of-term dates) | PASS | EndorsementIT passes, and the AC-06.6 E2E passes |
| TASK-005 | Proves AC-06.7 (ArchUnit coverage, no PUT/PATCH/DELETE mappings, owner PUT/PATCH/DELETE fail) | PASS | AppendOnlyArchTest is green and no mappings exist. Live PUT, PATCH and DELETE returned 405 |
| TASK-005 | Proves AC-10.2 (CANCELLED and LAPSED get 409 on endorse and preview, nothing written) | PASS | EndorsementStateIT passes |
| TASK-005 | Proves AC-10.4 over several sequences | PASS | EndorsementStateIT runs the eight sequences and passes |
| TASK-005 | C5 preview writes nothing; AC-06.2 preview 9490.00, 1330.85, 184, 365 | PASS | EndorsementPreviewIT passes. A live preview left the counts unchanged |
| TASK-005 | Detail endorsements with allow-listed `changes`; endorsementCount from the table | PASS | Live, My Policies showed 2 endorsements, and the detail lists both |
| TASK-005 | NFR-04.1 for endorse and preview | PASS | EndorsementSecurityIT and RoleBoundaryIT pass |
| TASK-005 | NFR-04.3 415 and NFR-04.4 413, including the encoded path | PASS | PolicyBodyLimitIT passes. Live, text/plain got 415 |
| TASK-005 | NFR-03.1 log capture for the three endorsement types and the preview | PASS | EndorsementLogCaptureIT passes |
| TASK-005 | G-1 commit precedes the first preview commit; thin controllers; ArchUnit passes | PASS | 0bdda51 precedes 834bc25, and ArchUnit is green |
| TASK-005 | test(red) before feat(green); logs saved | PASS | 834bc25 precedes f5f8171 |
| TASK-005 | Passes mvnw verify | PASS | The evaluator's full verify passes |
| TASK-006 | Mounts the C9 routes behind sign-in, adds the My Policies nav link, wires the issue, Endorse-link and confirm navigation | PASS | Verified live via MCP: the nav link, issue → detail, Endorse link → `/endorse`, and confirm → detail all work |
| TASK-006 | Vitest routing tests AC-05.1 and AC-06.1; existing routing tests pass | PASS | `App.test.tsx` passes within the 94 green tests |
| TASK-006 | Resolves SPA/API mismatches, listed in progress | PASS | None were found. The live walkthrough confirmed the SPA and API agree |
| TASK-006 | policy-lifecycle.spec.ts with the six named journeys | PASS | All six titles are present and pass on both projects |
| TASK-006 | data-testid selectors, no waitForTimeout, own data per test, parallel on both projects; Sprint 1 and 2 journeys pass | PASS | `waitForTimeout` count is 0, and the 28/28 full-suite run (all specs) passed on both projects |
| TASK-006 | Snapshots for My Policies, Versions tab and Endorse preview, masked, committed, platform-suffixed; second run passes | PASS | Six `-win32` baselines are committed, and two consecutive evaluator runs passed with `--update-snapshots=none` |
| TASK-006 | Full mvnw verify (SPA packaged) with JaCoCo unchanged; npm test and build pass | PASS | The evaluator re-ran all three green, and the pom is unchanged |
| TASK-006 | ac_audit reports every scope ID and every Sprint 1 and 2 ID COVERED | PASS | 65/81, 0 parent-only. Only AC-07, AC-08 and AC-11 are uncovered, and they are out of scope |
| TASK-006 | git log: test(red) naming each ID before feat(green); G-1 precedes the preview commits | PASS | Verified from the log: each scope ID is named in a red commit that precedes its task's green commit. NFR IDs are named in red commits only, which still precede the green commits. 0bdda51 is first |
| TASK-006 | dev and main unchanged locally and on origin; feature branch not pushed | PASS | 246e7cb and 8e265d6 locally and on ls-remote, with no feature ref on origin |
| TASK-006 | redact_evidence --check exits 0 over sprint-progress | PASS | total 0, exit 0 |
| TASK-006 | Records commands, the AC-10.3 carry-over and SG-1..3; leaves 8080 free | PASS | The progress file records all of these. Port 8080 was free after the evaluator's run |
| TASK-006 | test(red) with failing E2E and routing tests before feat(green); logs show both projects | PASS | 433c03f precedes fb4fb78, and both logs exist |

## Retry Tasks
(none)

## Overall
PASS
