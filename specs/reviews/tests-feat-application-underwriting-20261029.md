# Test review: feat/application-underwriting vs dev (Sprint 2)

Reviewer: tester (Mode 2). Date: 2026-10-29.

## Observed results
- `./mvnw -q verify`: green. Surefire 219 tests, Failsafe 212 tests, 0 failures, 0 errors, 0 skipped. JaCoCo gates passed.
  (The harness reported 251 surefire. My count sums `tests=` over report XML files and may differ from the harness method. Not a failure.)
- `npm test`: 38 Vitest passed, coverage 95.05% lines. `frontend/coverage/lcov.info` restored; tree clean.
- `python scripts/ac_audit.py --offline --no-write`: 47/78 covered, 0 PARENT-ONLY. Every Sprint 2 AC (AC-03.1-8, AC-04.1-6, AC-09.1-7, NFR-03.1, NFR-04.x) is covered. The 31 NOT COVERED are all AC-05..AC-08, AC-10, AC-11 (later sprints).
- Playwright not rerun by me (port 8080 left free); reviewed statically.

## Checks
- Then-clause literals: sampled and found exact. QUOTE_EXPIRED (ApplicationSubmissionIT:210-247, including the validUntil boundary day and expiry-before-duplicate order), KYC_AGE_MISMATCH (KycValidatorTest:163-231, ApplicationSubmissionIT:251-281, birthday and leap-day boundaries), masked values `**********1234` and `******234F` (UnderwritingQueueIT:116-117), AC-09.6 code pairing, AC-04.1-04.3 reason lists. No weak-assertion test found under an AC name.
- AC-04.6: UnderwritingReasonCodeMatrixTest has 13 rows (all v1 rules). UW-TL-005 expects `["UW-TL-004","UW-TL-005"]` (KD-004 nested threshold). UW-MO-002 is tested at domain level only, as spec J8 says. A guard test asserts one row per rule in the three v1 files.
- AC-04.5: a real v2 fixture (RuleSetFixtures, threshold 65), tested at domain level (UnderwritingRuleEvaluatorTest:93) and through the API (ApplicationRuleVersionIT:56) asserting stored ruleVersion = 2.
- NFR-03.1: ApplicationLogCaptureIT uses OutputCaptureExtension. It asserts the capture is non-blank and contains the application ID, so it is not vacuous. It covers submit, a rejected submit, queue read, decision and override, rejected decisions, an unknown key, a wrong role, and a nested body. It asserts Aadhaar, PAN, health text, name, DOB and comment are absent.
- Git order: every sprint ID has a `test(red)` commit before its `feat(green)` (cd638af to 831edd4; 8e77617 to ac9e7ce; 447f27d to 3647ad5; 4632739 to 3001b7b; a985d4a to 1a77eb4; 8cb4242 to b941f95). No green-before-red. `c6db2d7 test:` fixes a red test after the red commit, which is legitimate (it was invalid, U-10).
- JaCoCo: backend/pom.xml has no diff vs dev (0.80 domain / 0.70 overall unchanged), no new exclusions.
- No `@Disabled`, `.skip`, `fixme`, `Thread.sleep` or `waitForTimeout` under backend/src/test, frontend/src or frontend/e2e. Date logic uses MutableClock/FixedClockConfig, and ClockConfigTest bans bare `now()` in production code.
- Playwright isolation: each test creates its own quote and application via API and locates its row by ID, so the parallel desktop and mobile projects do not collide. The seeded rows are tolerated. Web-first assertions throughout. DOB values are computed with a 180-day margin so they are stable across midnight.

## Findings
- MAJOR: none.
- BLOCKER: none.
- MINOR-1: Snapshot baselines are `*-win32.png` only (frontend/e2e/__snapshots__, playwright.config.ts snapshotPathTemplate). A Linux GitLab runner will fail every `toHaveScreenshot` for lack of baselines. Sprint 5 CI must generate and commit `-linux` baselines, or run the visual tests only locally until then.
- MINOR-2: workbench.spec.ts:35 masks only `h2` in the case detail. If the detail pane shows submittedAt, the application ID outside h2, or the age, the baseline will flake. It was stable twice per the evaluator. Consider masking every ID and date element, as apply-underwrite.spec.ts does. apply-form masks the DOB input, which is correct.
- MINOR-3: workbench `openWorkbench` expects the new row to be visible in the queue. If the queue grows with seed and other tests' rows and gets paginated or capped, this becomes flaky. Today it is fine.
- MINOR-4: Surefire test count differs between my tally (219) and the harness (251). This is probably counting method only. Confirm the evaluator's counting method before it is used as a regression baseline.

VERDICT: APPROVE

---

## Resolution log (orchestrator, 2026-11-03)
- **MINOR 4 (219 vs 251 unit tests): resolved, not a regression.** A single `./mvnw -B test` run at `b941f95`
  reports `Tests run: 251` in Maven's summary, while the per-class surefire XML files from that same run add up to
  219. All 23 test classes in `src/test/java` (excluding `archfixtures`) have a report, and every one passed. The gap
  is a reporting difference between Maven's console summary and the per-class XML totals, not missing tests. The
  regression baseline is Maven's summary: **251 unit + 212 integration**.
- **MINOR 1 (win32-only snapshots):** deferred to Sprint 5. CI must generate `-linux` baselines, or run the visual
  assertions locally only until then.
- **MINOR 2, 3 (masking scope, queue growth):** recorded as follow-ups. The evaluator observed stable snapshots in two
  consecutive runs.
