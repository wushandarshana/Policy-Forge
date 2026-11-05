# Test review: feat/issuance-endorsement vs dev (Sprint 3)

Reviewer: tester (Mode 2). Date: 2026-11-03. Range: `git diff 246e7cb..HEAD` (127 files).

## Observed results
- `./mvnw -o verify`: BUILD SUCCESS. Failsafe 321 tests, 0 failures, 0 errors, 0 skipped. JaCoCo gates passed.
- `npm test`: 13 files, 94 Vitest tests passed. Working tree clean afterwards.
- `python scripts/ac_audit.py --offline --no-write`: 65/81 covered, 0 PARENT-ONLY. Every Sprint 3 ID (AC-01.12, AC-01.13, AC-03.9, AC-05.1-4, AC-06.1-7, AC-10.1-4) is covered. The 16 NOT COVERED are AC-07, AC-08, AC-11 (later sprints).
- Playwright not rerun (port 8080 left free); reviewed statically.

## Checks
- Spec numbers: exact. AC-06.2 in EndorsementRatingIT:35-60 asserts 9490.00 / 1330.85 as strings, stored rows at scale 2, version 2 sum insured 7000000.00, and the reverse change -1330.85 and 2640.00 on the term start. The domain test EndorsementPremiumAdjusterTest and EndorsementPreviewIT carry the 184/365 proration and the 7.23 over 1 day case. AC-05.1 in PolicyIssuanceIT:99-127 asserts `^PF-TL-2026-\d{6}$`, 2026-11-01, 2027-10-31, 5000000.00, 6850.00, ACTIVE, and the response key set. PolicyNumberFormatTest:25 and IssuanceDatesTest:19 pin the same at domain level.
- AC-06.3 atomicity (EndorsementAtomicityIT): the failure is a `@TestConfiguration` `@Primary` JDK proxy over `PolicyVersionRepository` that throws on `save` while armed. No hook exists in production code. It is not vacuous: inside the failing call it counts endorsement rows via native query and asserts 1, so the insert had reached the database before the failure. Afterwards it asserts 500, no endorsement, one version, status ACTIVE, one transition, an equal before/after snapshot, and a retry that reuses sequence 1 and produces version 2.
- AC-05.2 (PolicyNumberingIT:107): 50 issues on a pool of 8 with a start latch, all 201, 50 distinct numbers, and no gap in the sequence. It also covers 5 sequential (n..n+4), per-product contiguity, a fresh clock year starting at 000001, and a concurrent double issue of one application giving one 201 and one 409. Meaningful.
- AC-06.6 and AC-06.7: the race test (EndorsementConflictIT:95, 132) asserts the winner is stored and the loser gets 409 POLICY_UPDATE_CONFLICT with nothing written. AC-06.7 combines ArchUnit (AppendOnlyArchTest:290-309, which also asserts the target classes exist, so it is not a vacuous rule) with an HTTP check that PUT, PATCH and DELETE do not succeed.
- AC-10.1: the parameterised matrix in PolicyStateMachineTest asserts 30 cells, 13 allowed and 17 forbidden. AC-10.2 and AC-10.4 are in EndorsementStateIT, with the transition count and the last `to` compared against status. A CANCELLED policy is set up by DB row, which the test documents (P-6).
- AC-10.3: domain half only (PolicyStateMachineTest:96, PolicyEntityTest:64). The display names state the Sprint 4 carry-over. Accepted.
- NFR-03.1: PolicyLogCaptureIT and EndorsementLogCaptureIT follow the Sprint 2 pattern (non-blank capture, ID present, PII absent). NFR-04: EndorsementSecurityIT, RoleBoundaryIT (+50 lines), the 404 fixed-body checks and PolicyBodyLimitIT cover the role and ownership paths.
- Hygiene: no `@Disabled`, `.skip`, `fixme`, `Thread.sleep` or `waitForTimeout` anywhere under backend/src/test, frontend/src or frontend/e2e. `backend/pom.xml` and the JaCoCo thresholds have no diff vs dev. Clock is injected (clock.setDate).
- TDD order, 819a246..HEAD, each ID has the red commit before the green: 26c2f23 then 33d8c73 (domain); 27c5062 then 12415da (pages); a05f186 then b44053e (AC-01.12, AC-03.9); 48e21e7 then 53a2f9b (issuance); 834bc25 then f5f8171 (endorsement); 433c03f then fb4fb78 (E2E). The spec commit 0bdda51 (preview endpoint) comes first, as the rules require. No green-before-red.
- Playwright (policy-lifecycle.spec.ts): all interaction through `getByTestId`, web-first `expect` assertions, and each test creates its own data via the API. Snapshots use `stylePath` hiding of policy numbers, dates and card titles, with the layout space kept. The reason is documented in snapshot-mask.css (Playwright `mask` misplaces on the mobile project for tall elements).

## Findings
- BLOCKER: none.
- MAJOR: none.
- MINOR-1: `12415da feat(green)` edits a red test, `frontend/src/pages/EndorsePage.test.tsx`, adding `install();` at lines 87 and 109. It does not weaken any assertion, but it is a test change inside a green commit. The orchestrator's order rule says to fix tests in a `test:` commit. Record it as a repair of a red test that was invalid.
- MINOR-2: `EndorsePage.test.tsx:133` ("shows a fixed not-found message when the policy does not exist") is named `AC-10.4`. It does not exercise the AC-10.4 Given/When/Then (status equals the last transition's `to`). It adds nothing to AC-10.4 coverage and could mislead the audit. Retag it to AC-06.1 or similar. AC-10.4 is genuinely covered by EndorsementStateIT:128.
- MINOR-3: snapshot baselines are `-win32.png` only (same as Sprint 2 MINOR-1). A Linux CI runner has no baselines. Sprint 5 CI must generate `-linux` ones.
- MINOR-4: `frontend/coverage/lcov.info` is tracked and churns (3030 lines in the diff). It is noise in review and in merges. Consider untracking it or ignoring it.
- MINOR-5: AC-06.3 uses a proxy on the repository, so the failure is injected at the Spring Data call and not at the database. This is a reasonable choice and it proves the rollback. It would not catch a version write that bypasses that repository bean. Acceptable today because `save` is the only write path.

VERDICT: APPROVE


## Resolution log (orchestrator, 2026-11-03)
- **MINOR-2 (mis-tagged test):** retagged to AC-06.1 in `b542136`; AC-10.4 stays covered by `EndorsementStateIT`.
- **MINOR-3 (win32-only snapshots), MINOR-4 (tracked lcov):** deferred to Sprint 5 (CI).
- **MINOR-1, MINOR-5:** accepted. No assertion was weakened, and `save` is the only write path.
