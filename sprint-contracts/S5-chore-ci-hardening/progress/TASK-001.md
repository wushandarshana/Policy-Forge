# TASK-001 Progress

## Status
DONE

## What was built
Backend hardening for NFR-08.1, AC-07.7 and AC-08.5. Nothing under `backend/src/main/` changed, no Flyway migration or rule file changed, and `backend/pom.xml` (the JaCoCo thresholds, 80% domain and 70% overall) is unchanged since `d9a5694`.

- **Amendments A and B were already present.** `git log d9a5694..HEAD` starts with `4730168 spec: Sprint 5 CI criteria NFR-08.1 and NFR-09.1-09.6, ...` (A, `specs/` only) and `f9a1664 chore(substrate): pin @playwright/mcp 0.0.84, ...` (B, five files). TASK-001 recreated neither and committed no substrate. `dev` is `d9a5694` and `main` is `8e265d6`, unchanged. Nothing was pushed.
- **NFR-08.1, `RepriceOfferCallerArchTest`** (`@Tag("NFR-08")`, production classes only through `ArchSupport.production()`).
  - The rule factory `onlyCalledBy(ownerClass, allowedCaller)` is `noClasses().that(not the allowed caller or its nested classes).should().callMethodWhere(repriceOffer on the owner or a subclass)`. Its `because` names NFR-08.1.
  - It passes on production, where `EndorsementService` is the only caller. A non-vacuity test fails the build if `repriceOffer` is renamed (the allowed caller must still call it, and only `PolicyTermEntity` declares it).
  - Negative fixture under `com.horizon.archfixtures.reprice`: `OfferTerm` (owner), `FixtureEndorsementService` (allowed), `RepriceCaller` (rejected and named), and `OtherPricer` with `OtherCaller` (a same-named method on another owner, not rejected). The test is titled "NFR-08.1 negative fixture: ...".
  - Red then green (P-3): `1e26f7f test(red)` has the factory stubbed to allow every caller, so the fixture test fails on "Expecting code to raise a throwable". `ed11eb6 feat(green)` has the real rule.
- **AC-07.7, `RenewalRepriceRaceIT`.** The AC-04.1 TERM_LIFE policy (6850.00, sum insured 5,000,000, term 2026-11-01..2027-10-31), offered on 2027-10-01. Both requests run on 2027-10-05, a payment and CHANGE_SUM_INSURED 7,000,000 effective 2027-10-05. A test-configuration `BeanPostProcessor` holds one named thread, `reprice-race-loser`. Production has no hook.
  - Payment wins, endorsement held: the endorsement gets 409 `POLICY_UPDATE_CONFLICT` with the fixed detail (no policy number, no input echoed) and writes no endorsement row. Term 2 is paid at 6850.00 with dates 2027-11-01..2028-10-31 and rule version 1. The status is RENEWED, version 2 is 6850.00 with sum insured 5000000.00, and the transitions are exactly ACTIVE, RENEWED (no ENDORSED).
  - Endorsement wins, payment held: the payment gets 409 `POLICY_UPDATE_CONFLICT` and writes nothing. Term 2 is unpaid at 9490.00 with dates and rule version unchanged. The status is ENDORSED, version 2 has sum insured 7000000.00 and premium 9490.00, the adjustment is 195.29, and there is no RENEWAL_PAID transition. A fresh payment on 2027-10-06 gives 200, and version 3 is RENEWED at 9490.00, equal to term 2's premium.
  - Both tests assert that exactly one request succeeded, and `assertEveryPaidTermMatchesItsVersion`: each paid term's premium equals the premium of the one version that starts with that term.
  - Committed as `49a588e test: AC-07.7 ...` (characterisation, P-3), because it passes against unchanged production.
- **AC-08.5, the oracle cross-check.**
  - `scripts/gen_refund_oracle_cases.py` (standard library only, Python 3.11 or later) imports `refund()` and `days()` read-only from `premium_check.py`, with bytecode writing off, and writes the C10 CSV `backend/src/test/resources/oracle/ac-08-5-refund-cases.csv`: header per C10, LF, 1,000 rows, seed 20261104 using only `getrandbits` so the output is the same on every Python.
  - It writes 37 boundary rows first: term first and last day, cooling-off at -1, 0 and +1 days for a first and a renewal term, spec examples AC-08.1 to AC-08.3, fee above and equal to the pro-rata share, fee 0.00, premiums 0.00, 0.01 and 9999999.99, 1-month, 24-month and 366-day leap terms, and exact half-cent ties. Then 963 seeded random rows.
  - The file has 50 cooling-off rows, 500 renewal-term rows and 274 non-cooling rows that floor to 0.00. `--check` exits 0 on the committed file, and on a one-row edit exits 1 printing the first differing line (committed and generated). It exits 0 again after the revert, and `cmp` shows the file identical.
  - `ProRataRefundOracleCrossCheckTest` is titled "AC-08.5: the production calculator equals the premium_check.py oracle on 1,000 generated cases". For each row it asserts the amount string (scale 2), `termDays`, `unexpiredDays`, `coolingOff`, and the fee (0.00 when cooling off, otherwise the row's fee). It also asserts 1,000 rows and at least one cooling-off, one renewal-term and one floor-to-0.00 row. The existing `ProRataRefundPropertyTest` stays.
  - Commits: `588feb0 feat(green)` (script and CSV) and `79a53e6 test: AC-08.5 ...` (characterisation).
- **Mutation proofs** (never committed, all in `TASK-001-red.log`). In each case `git checkout --` restored the file and `git diff --stat -- backend/src/main` was empty afterwards.
  - AC-07.7 mutation 1: `WriteConflicts.isUniqueViolation` always false. Both race tests fail on `problem(lost, 409)` (expected 409, was 500).
  - AC-07.7 mutation 2: `EndorsementService` no longer calls `repriceOffer`. The endorsement-wins test fails on "the offer is re-priced" (expected "9490.00", was "6850.00").
  - AC-08.5 mutation 1: final rounding HALF_EVEN. Fails on `amount` at row `half-cent-tie-0-01` (expected 0.01, was 0.00).
  - AC-08.5 mutation 2: cooling-off `<` changed to `<=`. Fails on `amount` at row `term-first-day` (expected 6350.00, was 6850.00).
  - AC-08.5 mutation 3: a cooling-off refund reports the cancellation fee. Fails on `fee` at row `cooling-off-15-first-before-edge` (expected 0.00, was 500.00).
- **Oracle CLI cross-check** (in `TASK-001-green.log`):
  - A renewal-term row (random-0001, 1062551.86) and a floor-to-zero row (random-0002, 0.00) are EQUAL to `premium_check.py refund`.
  - The cooling-off row (random-0026) cannot be compared through the CLI, which has no cooling-off flag and prints the pro-rata 1279670.35. Calling the oracle's `refund(..., cooling_off=19)` gives the full premium 1669508.28, equal to the CSV.
  - `spec-examples` passes all 9 rows, exit 0.
- **Full backend verify.** `./mvnw -B verify -Dskip.frontend=true`: 490 surefire tests and 570 failsafe tests, 0 failures. ArchUnit green, including `RepriceOfferCallerArchTest` (3 tests). `jacoco:check` for overall and domain reports "All coverage checks have been met". BUILD SUCCESS in 7:20 min.
- **AC audit** (`python scripts/ac_audit.py --offline --no-write`): NFR-08.1, AC-07.7 and AC-08.5 are COVERED. Overall 89 / 92 when it ran, 0 parent-only. The 3 not covered at that moment were NFR-09.1, NFR-09.2 and NFR-09.5, which belong to TASK-003 and TASK-004.
- **Commits** (all on `chore/ci-hardening`, explicit paths, no push):
  - `1e26f7f test(red): NFR-08.1 ...`
  - `ed11eb6 feat(green): NFR-08.1 ...`
  - `49a588e test: AC-07.7 ...`
  - `588feb0 feat(green): AC-08.5 NFR-09.6 gen_refund_oracle_cases.py ...`
  - `79a53e6 test: AC-08.5 ...`
- **Logs.** `sprint-progress/TASK-001-red.log` and `TASK-001-green.log` name NFR-08.1, AC-07.7 and AC-08.5. Both were redacted with `redact_evidence.py` (total 0).
- **Environment at the end.** Port 8080 is free and no Java process is running.

## Files changed
- backend/src/test/java/com/horizon/policyforge/architecture/RepriceOfferCallerArchTest.java
- backend/src/test/java/com/horizon/archfixtures/reprice/repository/OfferTerm.java
- backend/src/test/java/com/horizon/archfixtures/reprice/service/FixtureEndorsementService.java
- backend/src/test/java/com/horizon/archfixtures/reprice/web/RepriceCaller.java
- backend/src/test/java/com/horizon/archfixtures/reprice/other/OtherPricer.java
- backend/src/test/java/com/horizon/archfixtures/reprice/other/OtherCaller.java
- backend/src/test/java/com/horizon/policyforge/web/RenewalRepriceRaceIT.java
- backend/src/test/java/com/horizon/policyforge/domain/cancellation/ProRataRefundOracleCrossCheckTest.java
- backend/src/test/resources/oracle/ac-08-5-refund-cases.csv
- scripts/gen_refund_oracle_cases.py
- (not committed, in the gitignored workspace) .sprint/20261104-075010/sprint-progress/TASK-001.md, TASK-001-red.log, TASK-001-green.log

## Open issues
- **Plan deviation, AC-07.7 hold point (recorded plan defect).**
  - The plan said to hold the loser "at the policy `saveAndFlush`, as RenewalRaceIT does". That cannot work for this race. Both requests append policy version n + 1. With IDENTITY ids the held request has already inserted its own uncommitted `policy_version` row, so the other request's insert of the same unique key waits for the H2 lock timeout and fails.
  - The first version of the IT used that hold point. Both orders failed with the would-be winner getting 409 ("Timeout trying to lock table ... insert into policy_version"). The evidence is in the red log, section "DESIGN EXPERIMENT".
  - The IT instead holds the loser just before `policyVersionRepository.save` (test configuration only, same `BeanPostProcessor` pattern, no production hook). The winner then commits and the loser fails on the unique version key with 409 `POLICY_UPDATE_CONFLICT`, which the mutation proofs show is the path under test.
  - This race is therefore decided by the unique version key. The policy `@Version` check on `saveAndFlush` stays covered by the existing `RenewalRaceIT`. The class Javadoc explains this.
- **Plan wording, AC-08.5 CLI cross-check.** The `premium_check.py refund` subcommand has no `--cooling-off` or effective-date flag, so the cooling-off row can't be reproduced through the CLI alone. I cross-checked it through the oracle's `refund(..., cooling_off=N)` and the `spec-examples` AC-08.2 row. Adding a flag means editing `.claude/skills/**`, which is human-only substrate, so I left it. This is an optional follow-up for the orchestrator.
- **Parallel-task note.** The "clean `git status`" after each restore was checked with `git status --short -- backend` and `git diff --stat -- backend/src/main` (both empty). The repository-wide status showed TASK-002's in-progress `scripts/` changes at the time, which are not TASK-001's. `redact_evidence.py --check` over `sprint-progress/` reports 2 findings in `TASK-002-red.log`. The TASK-001 logs are clean, and the finding is TASK-002's to redact.
- **Line endings.** `.gitattributes` has `* text=auto` and `core.autocrlf=true` here, so the CSV is `i/lf` in the index but may be CRLF in a Windows working tree. `gen_refund_oracle_cases.py --check` and the JUnit reader both accept either, so no `.gitattributes` change was needed. TASK-004's quality-gates wiring can rely on `--check`.
- **Convention note.** The folder `CLAUDE.md` says to import with `@AnalyzeClasses`. `RepriceOfferCallerArchTest` uses `ArchSupport.production()` (same `DoNotIncludeTests` options) with `@Test` methods, so that every NFR-08.1 test carries a `@DisplayName("NFR-08.1: ...")` as root rule 8 requires. `@ArchTest` fields can't carry a display name.
- **Cross-task contract for TASK-004.** `quality_gates.py` gate 6 is `scripts/gen_refund_oracle_cases.py --check`. It exits 0 on the committed file and takes an optional `--file PATH`. The fixture is written LF-only, and the committed file is LF in the index.
