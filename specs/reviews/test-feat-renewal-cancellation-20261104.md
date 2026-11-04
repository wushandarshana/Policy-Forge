# Test review: feat/renewal-cancellation vs dev (Sprint 4)

Reviewer: tester (Mode 2). Date: 2026-11-04. Range: `git diff 12f95c3..HEAD` (169 files).

## Observed results
- `./mvnw -o verify`: BUILD SUCCESS. Surefire 479 tests and Failsafe 550 tests, 0 failures, 0 errors, 0 skipped. JaCoCo overall and domain gates passed.
- `npm test`: 148 Vitest tests passed (statements 96.5%, branches 89.23%). Working tree clean afterwards.
- `python scripts/ac_audit.py --offline --no-write`: 81/81 covered, 0 PARENT-ONLY, 0 NOT COVERED.
- Playwright not rerun (port 8080 left free); reviewed statically.

## Checks
- Spec numbers, AC-08: AC-08.1 asserts 4473.29 (fee 500.00, 265/365) in ProRataRefundCalculatorTest:31 and CancellationRefundIT:40, plus 2929.23 on a 8180.85 endorsed basis. AC-08.2 asserts 6850.00 with fee 0.00 and the day 14/15 boundary (6068.49 on day 15). AC-08.3 asserts 0.00 at domain level (ProRataRefundCalculatorTest:107) and as a stored row (CancellationRefundIT:434).
- AC-08.5 (ProRataRefundPropertyTest:21): a real 1,000-case loop, `new Random(20261104L)`, money from long cents (no double), asserts scale exactly 2 on amount and fee, 0.00 <= refund <= premium, and a non-vacuity guard that both cooling-off and pro-rata cases occur.
- AC-07 numbers: term 2027-11-01..2028-10-31 (RenewalPolicyTest:42, RenewalCycleIT:57); AC-07.4 at 2027-11-30 in force and 2027-12-01 LAPSED (GracePeriodPolicyTest:28, RenewalCycleIT:225); AC-07.5 on 2027-11-10 gives 200 and on 2027-12-01 gives 409 (RenewalPaymentIT:40, 145); AC-07.6 +16/+31 for MOTOR/HOUSEHOLD/TERM_LIFE (GracePeriodPolicyTest:56, 69; RenewalCycleIT:272).
- AC-07.3 (RenewalRuleVersionIT): a TERM_LIFE v2 fixture (`RuleSetFixtures.termLifeV2BandFactor130`, effective 2027-09-01) is published in its own context (`policyforge.test.context=renewal-rule-version-isolated`, `@DirtiesContext(AFTER_CLASS)`, no rule file added). Renewal offered at 7400.00 / ruleVersion 2, term 1 and version 1 stay 6850.00 / v1, version 2 after payment is 7400.00 / v2. Backed by V10 migration test and entity test.
- AC-07.2: sequential idempotency (RenewalCycleIT:174, snapshot of terms, versions, transitions equal apart from one audit entry) and a concurrent test (RenewalCycleIT:192, 4 threads with a start latch, exactly one term 2, policy listed once across all responses, one transition). Also RenewalPaymentIT:197 (six concurrent payments, one 200) and RenewalRaceIT (pay vs lapse in both directions).
- AC-08.4: atomicity (CancellationAtomicityIT:78) uses a `@Primary` proxy on the transition repository that fails on the CANCELLED save and, inside the failing call, counts refund rows to prove the insert had reached the database. After the failure: 500 with a fixed body, no refund, status/version/transition count unchanged, and a retry succeeds. Concurrency: CancellationConflictIT:97 (race, first 201, loser 409 POLICY_UPDATE_CONFLICT, one refund row). Append-only: ArchUnit rules on RefundRecordEntity/Repository assert the classes exist; V11 migration test covers NUMERIC(19,2), unique per policy and the DB checks.
- AC-10.3: domain half (PolicyStateMachineTest:97, PolicyEntityTest:64) and API half (CancellationStateIT:91, a parameterised test that lapses policies through the real renewal cycle, then asserts 409 invalid-policy-state on cancel and preview, no refund, and an unchanged snapshot).
- AC-11.2: exact decimal sum 10557.56 (PortfolioRulesTest:34 and PortfolioPremiumIT:42 with refund 4473.29 and endorsement 1330.85); negative adjustments, per-product 15150.00 and 11350.00, and unpaid offers collecting nothing. AC-11.1/11.3/11.4/11.5 cover the key set, the 60/61 day boundary, forecast exit on pay and lapse, and 403 for customer1, customer2 and underwriter1.
- NFR-03.1: Renewal/Cancellation/PortfolioLogCaptureIT. NFR-04.1/04.3/04.4: RenewalSecurityIT, CancellationSecurityIT (415 for text/plain and form), the BodyLimit ITs (413 with and without Content-Length, encoded path, cap before shape), SecurityChallengeIT, ProblemInstanceIT.
- Hygiene: no `@Disabled`, `.skip`, `.only`, `fixme`, `Thread.sleep` or `waitForTimeout` in backend/src/test, frontend/src or frontend/e2e. `backend/pom.xml` has no diff vs dev, so the JaCoCo thresholds are unchanged. Clocks are fixed (`MutableClock`/`FixedClockConfig`, `Clock.fixed` in domain tests). Mocking is limited to a repository proxy for fault injection; domain rules are tested plain.
- TDD order (`7d95f67..HEAD`), red before green for every ID: db692c8 -> 249e684 (AC-07), 47333cf -> bf7debc (AC-08, AC-10), 3abe2f6 -> 07df7e8 (AC-11, seed), c93edb1 -> 8b4d00a (domain rules), ea2d9d7 -> c95a016 (frontend), f61f067 -> b29c511 (E2E, route), 09ac8b0 and fe7d6a4 -> af24545 (carry-over security). The spec amendment 7d95f67 comes first. No green-before-red.
- Playwright: interaction through `getByTestId`, web-first `expect` assertions (`toHaveCount`, `toHaveScreenshot`), no `waitForTimeout`. Snapshots hide volatile dates, day counts and KPI values through `snapshot-mask.css` `stylePath` (same documented approach as Sprint 3, because `mask` misplaces on mobile). Baselines committed for desktop and mobile.

## Findings
- BLOCKER: none.
- MAJOR: none.
- MINOR-1: green commits edit tests. `249e684` changes `PolicyEntityTest.java:92,111` (new `PolicyTermEntityTest.of` argument, signature only); `bf7debc` changes `RefundRecordMigrationTest.java:67` and `07df7e8` changes `RuleSetSeedLoaderIT.java:64` (both switch `REFERENTIAL_INTEGRITY` off around a bare insert/delete) and `ApplicationSeedIT.java:140` (the `isEqualTo(1)` is replaced by a REFERRED-only filter plus a separate count of the seed row). No assertion is weakened, since the seed now adds ISSUED applications, but these belong in `test:` commits. Same pattern as Sprint 3 MINOR-1. Accepted as repair.
- MINOR-2: `PolicyTermEntityTest.java:54` is `AC-07.3` but asserts a `toString` contains the policy number and no PII. It adds nothing to AC-07.3 and is a toString assertion. Retag it NFR-03.1 (AC-07.3 is genuinely covered by RenewalRuleVersionIT, `PolicyTermMigrationTest:45` and `PolicyTermEntityTest:26`).
- MINOR-3: `PolicyStateMachineTest.java:97` display name still says "the API half is a Sprint 4 carry-over". The API half now exists (CancellationStateIT:91); update the name.
- MINOR-4: `ProRataRefundPropertyTest.java:21` asserts bounds, scale and cooling-off equality, not the refund formula against an independent oracle. The exact values are pinned by the example tests (4473.29, 6068.49, 14609.45), so the property is a safety net. Consider an oracle (premium x unexpired / termDays - fee) in a follow-up.
- MINOR-5: `frontend/e2e/dashboard.spec.ts:52` locates tiles by the CSS class `.kpi-tile` inside a test-id container, and `policy-lifecycle.spec.ts:253,284,320` use `[data-testid^=...]` prefix locators. Prefix test-id locators are acceptable; give the tiles individual test ids.
- MINOR-6: snapshot baselines are `-win32.png` only (carried over from Sprints 2 and 3); Sprint 5 CI needs `-linux` ones.
- MINOR-7: `frontend/coverage/lcov.info` is still tracked and churns (2713 lines in this diff). Untrack or ignore it.

VERDICT: APPROVE

## Resolution log (orchestrator, 2026-11-04)
- **MINOR-2 (AC-07.3 tag on a toString test), MINOR-3 (stale AC-10.3 name), MINOR-5 (`.kpi-tile` CSS locator):** fixed
  in `2f27c0c`, a `test:` commit.
- **MINOR-1 (test edits inside green commits):** accepted for the sprint commits. The fix loop kept test edits in
  `test:` commits (`e79ade9`, `2f27c0c`).
- **MINOR-4 (AC-08.5 oracle cross-check), MINOR-6 (`-linux` baselines), MINOR-7 (tracked lcov):** deferred to Sprint 5.
