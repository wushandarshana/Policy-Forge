# Fix loop: Sprint 4 review findings (backdated cancellation, stale renewal premium, unbounded job date and asOf, offer race)

- **Detected by:** the post-sprint reviews of `feat/renewal-cancellation`:
  - [`specs/reviews/security-feat-renewal-cancellation-20261104.md`](../../specs/reviews/security-feat-renewal-cancellation-20261104.md) (REQUEST CHANGES)
  - [`specs/reviews/test-feat-renewal-cancellation-20261104.md`](../../specs/reviews/test-feat-renewal-cancellation-20261104.md) (APPROVE with minors)
- **Findings fixed in this loop:**

  | ID | Severity | Issue | Spec decision (`404b5e4`) |
  |---|---|---|---|
  | SEC-BLOCKER-1 | BLOCKER | `CancellationDateRule` only required `termStart <= date <= termEnd`, so a customer could backdate the cancellation into cooling-off (a full refund at any time in term 1) or a larger pro-rata refund | AC-08.6: `max(today, termStart) <= cancellationDate <= termEnd`; a past date is 400 `CANCELLATION_DATE_OUT_OF_TERM` naming `cancellationDate` (preview: `date`), nothing written |
  | SEC-MAJOR-1 | MAJOR | A `CHANGE_SUM_INSURED` while a renewal offer was pending renewed the higher sum insured at the stale, lower premium | AC-07.7: the endorsement re-prices the unpaid term in the same transaction, with the term's recorded rule version; dates and version unchanged, never paid by this step |
  | SEC-MAJOR-2 | MAJOR | `POST /api/admin/jobs/renewal-cycle?date=` was format-checked only, so one request (`2207-10-01`, `+999999999-12-31`) could lapse the whole portfolio | AC-07.8: absent means today; otherwise `today - 30 <= date <= today` or 400 naming `date` and nothing runs; `policyforge.jobs.renewal-cycle.any-date` (default `false`) lifts it for tests only |
  | SEC-MINOR-1 | MINOR | `asOf=+999999999-12-31` on the dashboard gave a 500 (`DateTimeException` in `asOf.plusDays(60)`) | AC-11.6: `asOf` outside 2000-01-01..2100-12-31 or not a date is 400 naming `asOf` with a fixed detail |
  | SEC-MINOR-2 | MINOR | Offer creation did not touch the policy's `@Version`, so a cycle offer racing a cancel could leave an unpaid term on a CANCELLED policy | No spec change (consistency of AC-07.2 / AC-08.4) |
  | SEC note | — | No dedicated test for two concurrent payments | AC-07.5 race test added |
  | TEST-MINOR-2 | MINOR | `PolicyTermEntityTest` toString check tagged AC-07.3 | Retagged NFR-03.1 |
  | TEST-MINOR-3 | MINOR | `PolicyStateMachineTest` AC-10.3 display name called the API half a Sprint 4 carry-over | Name updated (CancellationStateIT) |
  | TEST-MINOR-5 | MINOR | `dashboard.spec.ts` located KPI tiles by the `.kpi-tile` CSS class | Tiles get `data-testid` and the test uses them |

The `red.log` and `green.log` linked below are copies of `.sprint/20261103-223107/sprint-progress/FIX-S4-{red,green}.log`.
Both pass `redact_evidence.py --check`.

## 1. Spec first: `404b5e4`
The four new criteria (AC-07.7, AC-07.8, AC-08.6, AC-11.6) and the endorsement-spec cross-reference were committed on
their own before any test or code, per the root non-negotiable "spec is truth".

## 2. Reproduce (red): `6921161`
Log: [`red.log`](2026-11-04-s4-review-fixes/red.log) (targeted run, `-Dmaven.test.failure.ignore=true` so both
surefire and failsafe report). Surefire 31 run / 6 failures, failsafe 25 run / 10 failures, all assertion failures.
Production stubs (`RenewalPolicy.isJobDateAllowed` returning true, `PortfolioRules.isSupportedAsOf` returning true,
a no-op `PolicyTermEntity.repriceOffer`) only make the tests compile.

| Test | Before the fix |
|---|---|
| `CancellationDateRuleTest` AC-08.6 (backdated 2026-11-02 rejected naming `cancellationDate` / `date`; lower bound max(today, termStart)) | no exception was raised |
| `CancellationBackdateIT` AC-08.6: on 2027-10-20 a cancel with 2026-11-02 is 400 and writes nothing | 201, refund written |
| `CancellationBackdateIT` AC-08.6: preview `date=2026-11-02` and yesterday 2027-10-19 are 400 | 200 |
| `CancellationBackdateIT` AC-08.6: cooling-off judged on the cancellation date (2026-11-02 refused on 2026-11-05) | 200 |
| `CancellationBackdateIT` AC-08.6: cancelling on 2027-10-20 refunds 0.00 (6850.00 x 12 / 365 - 500.00) | passed (control) |
| `PolicyTermEntityTest` AC-07.7: an unpaid offer is re-priced 6850.00 -> 9490.00; a paid term refuses | premium unchanged, no exception |
| `RenewalRepriceIT` AC-07.7: offer 6850.00 / v1, SI to 7,000,000 effective 2027-10-05 gives term premium 9490.00, pay on 2027-10-06 gives RENEWED version SI 7000000.00 / 9490.00 | term stayed at 6850.00 |
| `RenewalRepriceIT` AC-07.7: preview writes nothing; CHANGE_ADDRESS leaves the offer; no offer means no term change | passed (controls) |
| `RenewalPolicyTest` AC-07.8 and `RenewalJobDateBoundIT` AC-07.8 (clock 2027-10-01, any-date off): 2207-10-01, 2027-08-31, 2027-10-02 and `+999999999-12-31` are 400 naming `date` with no term, transition or audit row | 200, the cycle ran |
| `RenewalJobDateBoundIT` AC-07.8: 2027-09-01, 2027-10-01 and an absent date are accepted | passed (controls) |
| `PortfolioRulesTest` AC-11.6 and `PortfolioAsOfBoundIT` AC-11.6: `+999999999-12-31`, `-999999999-01-01`, 1999-12-31, 2101-01-01 and a non-date are 400 naming `asOf` with one fixed detail; 2000-01-01 and 2100-12-31 are 200 | 500 for the extreme dates, 200 for 1999-12-31 and 2101-01-01 |
| `RenewalOfferRaceIT` AC-07.2: an offer held before its term save loses to a committed cancel and writes no term | the term was written on the CANCELLED policy |
| `RenewalOfferRaceIT` AC-07.2: a cancel held before its policy save loses to a committed offer with 409 `POLICY_UPDATE_CONFLICT` | 201, the policy was CANCELLED with the unpaid offer |
| `RenewalPaymentIT` AC-07.5: two simultaneous payments give one 200 and one 409, one RENEWED version | passed (structural protection already held) |

Existing tests moved in the red commit (no assertion weakened, spec numbers kept): only
`CancellationDateRuleTest` "AC-08.1: the term start and the term end are accepted" now runs with today = the term start.
A provisional run of every cancellation, portfolio and lifecycle IT with the new rule found no other test that cancels
before its clock (they already move the clock to or before the cancellation date). The red commit also sets
`policyforge.jobs.renewal-cycle.any-date=true` in `backend/src/test/resources/application.properties` and passes
`--policyforge.jobs.renewal-cycle.any-date=true` to the Playwright webServer (`-Dspring-boot.run.arguments=...`).
`src/main` never sets it.

## 3. Arch allow-list: `e79ade9`
`AppendOnlyArchTest.policyTermEntityChangesOnlyThroughMarkPaid` lists the public mutators of `PolicyTermEntity`. The
spec now permits one more intention-revealing method, so the list is `markPaid`, `of`, `repriceOffer`. The no-setter
rule is unchanged. This is a `test:` commit of its own before green, so the green commit carries no test edits
(the Sprint 3 and Sprint 4 test reviews flagged test edits inside green commits).

## 4. Fix (green): `39fae46`
- **AC-08.6:** `CancellationDateRule.resolve` uses `earliest = max(today, termStart)`. Cancel and preview share it, so
  both name their own field. Cooling-off is still decided by `ProRataRefundCalculator` on the (now never past) date.
- **AC-07.7:** `EndorsementService.prepare` finds the pending term (`PolicyTerms.pending`) when the change re-rates and
  quotes the modified inputs with the term's rule version (the same `PremiumCalculator`). `endorse` calls
  `PolicyTermEntity.repriceOffer` in the endorsement transaction. `repriceOffer` refuses a paid term; dates and rule
  version stay `updatable = false`; the `premium` column mapping became updatable. No migration was needed (V8 has no
  trigger on `policy_term`), so V1-V11 are untouched. Payment already charges `pending.getPremium()`.
- **AC-07.8:** `RenewalService.runCycle(RunCycleCommand)` checks `RenewalPolicy.isJobDateAllowed(date, today)`
  (`JOB_DATE_MAX_DAYS_BACK = 30`) unless `policyforge.jobs.renewal-cycle.any-date` (injected, default `false`). The
  check runs before anything is read or written, so a refusal writes no term, transition or audit row. The scheduler
  path (`runCycle(LocalDate, actor)` for today) is unchanged.
- **AC-11.6:** `DashboardService` refuses an `asOf` outside `PortfolioRules.EARLIEST_AS_OF..LATEST_AS_OF` with the same
  fixed 400 message as a malformed one.
- **MINOR-2:** a new repository fragment `PolicyLocking.bumpVersion` (`PolicyLockingImpl`, `EntityManager.lock(...,
  PESSIMISTIC_FORCE_INCREMENT)`) runs `update policy set version = v + 1 where version = v` right after the offer term is
  saved. A cancel that committed first makes the offer transaction fail (the cycle logs and skips the policy, the term
  is rolled back); a cancel still in flight then fails on the bumped version with 409. A first attempt with a Spring
  Data `@Lock(OPTIMISTIC_FORCE_INCREMENT)` finder did not bump the version of the already-managed entity, which the
  race tests caught.
- **Frontend:** the two KPI tiles carry `data-testid="kpi-tile-active"` and `"kpi-tile-premium"` (pixels unchanged).

Cross-check: `premium_check.py quote TERM_LIFE {age 35, non-smoker, sumInsured 7000000.00, termYears 20} --version 1`
= **9490.00**; `refund 6850.00 2026-11-01 2027-10-31 2027-10-20 500.00` = **0.00**; `adjust 6850.00 9490.00 2026-11-01
2027-10-31 2027-10-05` = **195.29** (the AC-07.7 preview adjustment).

## 5. Retag: `2f27c0c`
`PolicyTermEntityTest` toString test is now `NFR-03.1: …`; the `PolicyStateMachineTest` AC-10.3 name points at
CancellationStateIT; `dashboard.spec.ts` locates the tiles by test id. No assertion changed.

## 6. Validate
Log: [`green.log`](2026-11-04-s4-review-fixes/green.log).
- `./mvnw -o verify` (JDK 21.0.12, full build including the frontend): **BUILD SUCCESS**. Surefire **486** and failsafe
  **568** tests, 0 failures, 0 errors, 0 skipped. JaCoCo checks met (thresholds unchanged).
- `npm test`: 16 files, **148/148** passed; `coverage/lcov.info` unchanged. `npm run build`: OK.
- `npx playwright test --update-snapshots=none`: desktop-chromium **22/22**, mobile-chromium **22/22** (run as two
  projects). After the retag commit, `e2e/dashboard.spec.ts` on both projects: 8/8. Port 8080 free afterwards.
- `python scripts/redact_evidence.py --check` on the sprint progress folder and on this folder: exit 0.
- `python scripts/ac_audit.py --offline --no-write`: **85/85** covered, 0 parent-only; AC-07.7, AC-07.8, AC-08.6 and
  AC-11.6 are COVERED.

## 7. Deferred follow-ups (not in this loop)
| Item | Source | Note |
|---|---|---|
| Snapshot baselines exist only as `-win32.png` | TEST-MINOR-6 | Generate `-linux` baselines when the Sprint 5 CI lands |
| `frontend/coverage/lcov.info` is tracked and churns | TEST-MINOR-7, SEC note | Untrack or ignore it, and update `frontend/CLAUDE.md` |
| AC-08.5 property test asserts bounds and scale, not the formula against an independent oracle | TEST-MINOR-4 | Add `premium x unexpired / termDays - fee` as an oracle inside the 1,000-case loop |
| `BackdatedIssuanceArchTest` guards by method name | SEC 6a residual | Keep wrappers of `issueBackdated` input-free; a structural guard (for example caller allow-list by class) is a follow-up |
| TEST-MINOR-1 (test edits in earlier green commits) | Test review | Accepted as repair; this loop keeps test edits out of the green commit |
| `repository/CLAUDE.md` names only `markPaid` for `PolicyTermEntity` | This loop | Human substrate: add `repriceOffer` (AC-07.7) and the `PolicyLocking` fragment |

## Lesson
A date the client chooses is an input like a money amount: bound it against the server's clock, not only against the
record's own range. And a writer that changes no column of an aggregate still needs that aggregate's lock if its row
only makes sense while the aggregate is unchanged.
