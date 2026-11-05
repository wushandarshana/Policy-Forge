# Test review: chore/ci-hardening vs dev (Sprint 5)

Reviewer: tester (Mode 2). Date: 2026-11-04. Range: `git diff d9a5694..HEAD` (53 files), TDD order `git log f9a1664..HEAD`.

## Observed results
- `./mvnw -o verify -Dskip.frontend=true`: BUILD SUCCESS. Surefire 490 tests and Failsafe 570 tests, 0 failures, 0 errors, 0 skipped. JaCoCo gates passed (`backend/pom.xml` has no diff vs dev).
- `python -m unittest discover -s scripts/tests`: with the system Python alone, 22 of 138 fail, all with "PyYAML is not installed" (`test_ci_config.py:93`). In a throwaway venv built from `scripts/requirements-ci.txt` (outside the repo): 138 tests, OK.
- `python scripts/ci/quality_gates.py`: 6 of 8 gates pass with the system Python (`ci-script-tests` and `ci-schema` fail because the CI-only packages are missing). In the venv, 8 of 8 pass (ac-audit, redact-evidence, hook-tests, hook-line-endings, ci-script-tests, oracle-fixture, actionlint, ci-schema).
- `python scripts/ac_audit.py --offline --no-write`: 92 of 92 covered, 0 PARENT-ONLY, 0 NOT COVERED.
- Vitest and Playwright were not rerun (port 8080 left free). The new Vitest table `frontend/src/visualBaselinePolicy.test.ts` was reviewed statically.
- Working tree unchanged by this review (the only untracked file is the security review of another agent).

## Checks
- NFR-08.1 (`RepriceOfferCallerArchTest`): the production rule is `noClasses().that(otherThan(EndorsementService)).callMethodWhere(repriceOffer on PolicyTermEntity or a subclass)`. Nested classes of the allowed caller are exempt, which is needed for lambdas. The negative fixture (`archfixtures/reprice`) asserts that `RepriceCaller` is named and that `FixtureEndorsementService.endorse` and `OtherCaller` are not, so it proves both rejection and acceptance. There are also two non-vacuity guards (the allowed caller really calls the method, and it is declared once). The red log shows the fixture test failing with "Expecting code to raise a throwable".
- AC-07.7 (`RenewalRepriceRaceIT`): both orders are tested with the spec's numbers (6850.00 offer, sum insured 5,000,000 to 7,000,000, re-price 9490.00, adjustment 195.29). The race is deterministic: a `BeanPostProcessor` proxy holds only the loser thread's `PolicyVersionRepository.save`, with latches and a 20 s bound, and production code has no hook. The assertions cover 409 `POLICY_UPDATE_CONFLICT` with a fixed detail, no endorsement row, no stray transition, term and version rows, and "every paid term equals the premium of its own version". Mutation proofs in the red log:
  1. `WriteConflicts.isUniqueViolation` always false: both tests fail with 500 instead of 409.
  2. `repriceOffer` call removed: the endorsement-wins test fails with 6850.00 instead of 9490.00.
  3. A restore step shows `git diff -- backend/src/main` is empty and the test passes.
  Both proofs are real. The payment-wins test is not hit by mutation 2, but mutation 1 covers it.
- AC-08.5 (`ProRataRefundOracleCrossCheckTest`): it reads a 1,000-row fixture that `gen_refund_oracle_cases.py` writes from `refund()` in `premium_check.py` (exact decimal arithmetic, another language). `--check` is a quality gate, so the file cannot be hand-edited. It asserts amount, fee, scale 2, termDays, unexpiredDays and coolingOff per row, with the row context in every message, and it has non-vacuity counts. Three real mutation proofs fail on named rows: HALF_EVEN rounding (half-cent tie row), cooling-off `<=` boundary (first-day row), and fee reported in cooling-off (row 5). This closes Sprint 4 MINOR-4.
- NFR-09.1 to NFR-09.3, NFR-09.5 and NFR-09.6 CI definitions (`test_ci_config.py`): every test docstring starts with its ID, and 61 docstrings across the script tests carry an `NFR-09.x` ID. The tests load the YAML with PyYAML and walk the job, `needs`, `permissions`, `uses`, `image`, `env` and `run` structures (jobs, triggers, concurrency, `needs` and `allow_failure`, artifacts). They do not grep. Text matching is used only for `run:` bodies (set -x, sha256sum after curl, event expressions), which is the right level. The pins are checked against `package-lock.json` and the pom (Playwright image equals the locked version, Node equals `frontend.node.version`), not against a copy. The mode of `backend/mvnw` is read from the git index.
- NFR-09.4 stub-CLI tests (`test_claude_review.py`, `fixtures/fake_claude.py`): the runner is executed as a subprocess against a temporary git repository with a fake CLI that records its calls. The fail-closed paths each assert the exit code (2), and most assert that the CLI was never started or that the second pass did not run:
  - no key and blank key: exit 2, fixed message, `read_calls == []`;
  - wrong version, and exact-match subtests (`2.1.3110`, `12.1.311`): exit 2, `pass_calls == []`;
  - invalid output: schema enum violation, missing required key, and no structured output or fenced JSON, all exit 2;
  - CLI error: `is_error` and exit status 1 both exit 2, and the second pass is not started;
  - empty diff: exit 0, "No changes", `GATE: PASS (0 BLOCKER)`, CLI never started;
  - one BLOCKER exits 1, from either pass; MAJOR and MINOR findings alone exit 0 and are published.
  The prompt, argv, model, cap, token-free child environment and key-redaction tests are separate and non-vacuous. The comment step is tested against a local fake API, including a 307 that must not be followed.
- Red log for NFR-09: `9e9b20c` and `54896b1` record failures on assertions (97 failures and 21 errors against stubs; 26 of 27 CI tests failing for missing files). The "15 vacuous passes" the commit declares are negative-only checks that pass on a stub, and each has a positive twin. Acceptable.
- NFR-09.5 helper (`frontend/e2e/support.ts:149`, `visual-baseline-policy.ts`): `expectVisual` returns instead of calling `test.skip`, so the functional assertions that follow always run. A skip is recorded by annotation, `console.warn` and an appended file. A mode typo throws (no silent skip), update mode always asserts, and a present baseline always asserts. No direct `toHaveScreenshot` remains outside `support.ts` (the other grep hits are comments). The Vitest table covers every row of the decision function.
- TDD order: 1e26f7f (red) -> ed11eb6 (NFR-08.1); 49a588e and 79a53e6 are characterisation tests whose mutation proofs are in the red log; 588feb0 precedes 79a53e6 because the generator creates the fixture; 9e9b20c (red) -> 12dc531, 10fae5e, 3da04da, 61f20f3 (NFR-09.3/09.4/09.6); 6d26416 (red) -> 59420aa (NFR-09.5); 54896b1 (red) -> 2ecaea2, 693b60a, c2886b8, 600d257 (CI files, mode bit). No green-before-red. 7c7fdf3 changes `test_ci_config.py` after its red commit (GitLab `environment: claude-review`); it is declared in the message as a fix found by the security review.
- Hygiene: no `@Disabled`, `.skip`, `Thread.sleep` or `waitForTimeout` in the added tests. The only wait is a bounded latch in the race IT. No floating point in the Java tests; money is compared as decimal strings and with scale checks.

## Findings
- BLOCKER: none.
- MAJOR: none.
- MINOR-1: `scripts/tests/test_ci_config.py:93` is the only way a developer finds that 22 CI tests need PyYAML, and `python -m unittest discover` fails hard without it. The failure message is explicit and fails closed, so it is not a gap in coverage, but `scripts/CLAUDE.md` and `scripts/README.md` should say that `pip install -r scripts/requirements-ci.txt` is a precondition (the README is already close). Same for `quality_gates.py` (`MissingTool` exit 127), which is correct.
- MINOR-2: `frontend/e2e/support.ts:149` `expectVisual` itself (path resolution, `platformHasBaselines`, recording) has no automated test. Only the pure `decideVisualBaseline` is tested, by `visualBaselinePolicy.test.ts:12-57`. The "functional assertions still run" clause of NFR-09.5 therefore rests on code review and the Playwright suite. A small Playwright self-check with `PF_VISUAL_BASELINES=auto` on a folder without baselines would close it. This is not blocking because the helper has no branch that throws or calls `test.skip` on the skip path.
- MINOR-3: `.github/workflows/ci.yml` and `.gitlab-ci.yml` leave `PF_VISUAL_BASELINES` at its default `auto`, so until the `-linux` baselines are committed the CI visual assertions are skipped (and only listed in the report). That matches NFR-09.5. Once the linux baselines exist, auto turns into assert, which is the intended path. Record a follow-up to commit them after the first `update-linux-snapshots` run.
- MINOR-4: `RepriceOfferCallerArchTest.java` is tagged `@Tag("NFR-08")` with `@Tag("NFR-08.1")` on each test. This is correct. Note only that the negative-fixture `@DisplayName` starts with "NFR-08.1 negative fixture:" without a colon directly after the ID; `/ac-coverage` counts it (audit is 92 of 92), so no action beyond consistency.
- MINOR-5: the AC-07.7 payment-wins test is not killed by mutation proof 2 and has no mutation of its own beyond proof 1 (the 409 translation). The surviving mutants of interest (for example the payment path skipping the version insert) are covered by the existing `RenewalRaceIT` and `RenewalPaymentIT`. Fine as is.

VERDICT: APPROVE

## Resolution log (orchestrator, 2026-11-05)
- **MINOR-1 (PyYAML precondition):** `scripts/CLAUDE.md` (`3f95d7e`) and `scripts/README.md` (`e21e813`) state the
  hashed install. The YAML tests name the command when the package is missing.
- **MINOR-2 (`expectVisual` untested):** Playwright self-check in `14d1541`.
- **MINOR-3 (CI visual assertions skip until the `-linux` baselines exist):** POST-PUSH. Add the
  `update-linux-snapshots` label, then commit the 20 baselines.
- **MINOR-4 (display-name colon):** fixed in `a56f01a`.
- **MINOR-5:** no action, as the reviewer noted.
