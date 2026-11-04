# Security review: feat/renewal-cancellation (Sprint 4), 2026-11-04

Reviewer: security-reviewer agent (review only, no code changes). The orchestrator saved this file from the agent's
returned report, because the agent's rules forbid it writing report files.
Scope: `git diff 12f95c3..HEAD` (dev base, 18 commits, 169 files):
- **Renewal:** V10, RenewalService, RenewalScheduler, `POST /api/admin/jobs/renewal-cycle` and `POST /api/policies/{number}/renewal/pay`.
- **Cancellation:** V11 refund records, `GET /api/policies/{number}/cancel/preview` and `POST /api/policies/{number}/cancel`.
- **Dashboard:** `GET /api/admin/dashboard`.
- **Seed:** the synthetic portfolio seed and its service-private backdated issuance path.
- **Hardening:** no `WWW-Authenticate`, a fixed `instance` URN, the shared key sanitiser, a 503 on sequence exhaustion and a fixed container error page.
- **Frontend:** the Renew and Cancel panels, the Refunds tab and the Admin Dashboard page.

References:
- `specs/app_spec.md` §2, §5 (NFR-02, NFR-03.1, NFR-04.1–04.4) and §8.
- `specs/renewal-cancellation_spec.md` (G-3, G-4, G-5) and `specs/portfolio-dashboard_spec.md`.
- `backend/src/main/java/com/horizon/policyforge/web/CLAUDE.md` (KD-007..KD-010).

The spec changes came first, in their own commit (`7d95f67`).

Evidence run (JDK 21): a targeted `./mvnw -q verify` over the security, role-boundary, renewal, cancellation,
portfolio, problem-instance, error-path and numbering ITs, plus the ArchUnit, ProblemJsonSecurityHandler and
EchoedKeySanitiser unit tests. Failsafe: 289 run, 0 failures, 0 errors. Results per class:

| Area | Classes (tests) |
|---|---|
| Role and security | RoleBoundaryIT 48, SecurityBaselineIT 18, SecurityChallengeIT 19, RenewalSecurityIT 5, CancellationSecurityIT 5, EndorsementSecurityIT 3 |
| Renewal | RenewalCycleIT 12, RenewalPaymentIT 7, RenewalRaceIT 2, RenewalBodyLimitIT 4, RenewalCurrentTermIT 5, RenewalIsolationIT 2, RenewalLogCaptureIT 2, RenewalRuleVersionIT 1 |
| Cancellation | CancellationRefundIT 31, CancellationStateIT 10, CancellationConflictIT 3, CancellationBodyLimitIT 3, CancellationAtomicityIT 1, CancellationLogCaptureIT 2 |
| Dashboard and seed | PortfolioDashboardIT 8, PortfolioPipelineIT 8, PortfolioPremiumIT 4, PortfolioSeedIT 6, PortfolioLogCaptureIT 2, PortfolioEmptyIT 1 |
| Errors and numbering | ProblemInstanceIT 61, ErrorPathEndToEndIT 9, PolicyNumberingIT 7 |
| Surefire | BackdatedIssuanceArchTest 3, AppendOnlyArchTest 5, ProblemJsonSecurityHandlerTest 2, EchoedKeySanitiserTest 5 |

No live app was started.

## Checklist

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Auth boundary (NFR-04), roles match §8 | PASS | **Roles:** AdminJobController.java:61-62 and DashboardController.java:64-65 need ADMIN. RenewalController.java:65-66 and CancellationController.java:130-131 (preview) and :139-140 (cancel) need CUSTOMER. **Public paths:** SecurityConfig is unchanged, so no new path is public. `/error` returns only a fixed problem+json. **Tests:** anonymous callers get 401. CUSTOMER and UNDERWRITER get 403 on admin routes. UNDERWRITER and ADMIN get 403 on pay and cancel. |
| 1a | KD-007 JSON-only consumes (NFR-04.3) | PASS | **Declarations:** the job, pay and cancel declare `consumes = APPLICATION_JSON_VALUE`. **GET-only routes:** the preview and dashboard are GET only, and other verbs get 405. **Tests:** text/plain and form bodies get 415 with no change. |
| 1b | KD-010 default-deny body limit (NFR-04.4) | PASS | **Filter:** RequestBodySizeLimitFilter only changed its INSTANCE constant, so the 16 KB default covers the new POSTs. **Tests:** RenewalBodyLimitIT and CancellationBodyLimitIT get 413, including on encoded paths and before the shape check. |
| 2 | Ownership | PASS | **Single 404:** RenewalService.java:344-355 and CancellationService.java:245-255 return one fixed 404 for malformed, unknown and other customers' numbers. **Order:** this comes before any state or date check. **Actor:** taken from `Authentication#getName()`. |
| 2a | Client-controlled money and dates | **FAIL** | **Pay:** accepts only `{}`, and the amount is `pending.getPremium()`. **Cancel:** accepts no amount. **Findings:** the cancel date can be in the past (BLOCKER-1), the renewal premium can go stale (MAJOR-1), and the admin `date` is checked for format only (MAJOR-2). |
| 3 | Audit (NFR-04) | PASS | **Admin job:** each run appends a `RENEWAL_CYCLE_RUN` entry with the caller as actor. **Lapses:** carry `system:renewal-scheduler`. **Pay and cancel:** the actor is recorded on the transition and the refund row. **Dashboard reads:** not audited (P-10). |
| 4 | PII (NFR-03) | PASS | **Logs:** carry only numbers, dates and counts, and new request types redact `toString`. **Dashboard:** allow-listed, with no customer ID or name. **Log-capture ITs:** green. **Frontend:** no storage, console output, `dangerouslySetInnerHTML` or money arithmetic. |
| 5 | Injection | PASS | Spring Data derived queries only. No `@Query` or native SQL. |
| 6 | Append-only (NFR-02, NFR-05) and concurrency | PASS (see MINOR-2) | **Refund records:** RefundRecordEntity is `@Immutable` with no setters, and its repository has save and finders only. **Terms:** `markPaid` is one-way. **Migrations:** V1–V9 are untouched. **Races:** a double cancel, double pay, pay versus lapse and concurrent cycles each resolve to one winner and a 409. |
| 6a | Backdated issuance path | PASS | **Visibility:** `PolicyIssuanceService.issueBackdated` (:112-117) is package-private. **Caller:** its only caller is `PortfolioSeedService.create`, whose date comes from the clock and a fixed anchor with no request input. **Guard:** BackdatedIssuanceArchTest blocks callers outside `service..` and blocks public `*Backdated*` methods. **Residual:** the guard matches by name, so keep wrappers input-free. |
| 7 | Secrets and data | PASS | Synthetic seed holders, `XXXX-XXXX-01xx` Aadhaar, `ABCDE01xxF` PAN. No secrets. |
| 8 | Error leakage | PASS (see MINOR-1) | **`instance`:** a fixed URN for every problem. **`/error`:** returns a fixed problem+json. **Sequence exhaustion:** a fixed 503 detail. **New 409 codes:** fixed text. |

## Findings

### BLOCKER-1: a customer can backdate the cancellation date and claim a cooling-off full refund at any time in term 1
`CancellationDateRule.java:21-23` only requires `termStart <= date <= termEnd`, and never compares the date with
today. `CancellationService.java:216` passes the client's date through, and `ProRataRefundCalculator.java:84` decides
cooling-off from `DAYS.between(policyEffectiveDate, cancellationDate)`.

Scenario: on 2027-10-20, after 354 days of cover, customer1 cancels PF-TL-2026-000001 with
`{"reason":"OTHER","cancellationDate":"2026-11-02"}`. The date is accepted, so a refund of 6850.00 (the full premium,
fee 0.00) is recorded. The same works during grace. Even without cooling-off, any past date increases the pro-rata
refund. The code follows the spec as written, so the spec must change first.

Recommendation: `max(today, termStart) <= cancellationDate <= termEnd`, with cooling-off judged on today. Add an
AC-tagged test for a backdated date.

### MAJOR-1: an endorsement while an offer is pending renews the higher sum insured at the stale, lower premium
The cycle prices the offer once (RenewalService.java:239-253). EndorsementService still accepts CHANGE_SUM_INSURED
while an offer is pending (:208-210). Payment then builds version n+1 from the post-endorsement inputs, but with
`pending.getPremium()` from before the endorsement (RenewalService.java:320-323).

Scenario: on 2027-10-01 an offer is priced at 6850.00 for SI 5,000,000. On 2027-10-05 the customer endorses SI to
10,000,000 and pays only the pro-rata adjustment for 27 days. On 2027-10-06 they pay the renewal, and term 2 covers
10,000,000 at 6850.00. The trick repeats every year. This follows G-3 as written.

Recommendation, after a spec change, one of:
- (a) refuse rated-input endorsements while an offer is pending (409);
- (b) re-price the offer as part of the endorsement;
- (c) refuse payment if the inputs changed.

### MAJOR-2: the admin job `date` is unbounded, so one request can mass-lapse the portfolio irreversibly
RenewalService.java:145-151 only parses `date`, and a policy can be offered and lapsed in the same run.

Scenario: an admin types `?date=2207-10-01`, or a stolen admin1 credential is used. Every in-force policy is moved to
LAPSED, which is terminal, and there is no undo. `+999999999-12-31` is also accepted. The endpoint is "for testing"
but ships in every profile.

Recommendation: bound `date` in the spec and the code (400 naming `date`), and gate the job behind a property or
profile that is off by default outside test and demo builds.

### MINOR-1: an extreme `asOf` gives a 500 on the dashboard
`asOf=+999999999-12-31` makes `PortfolioRules.java:45` (`asOf.plusDays(60)`) throw `DateTimeException`, which becomes
a generic 500. The caller is ADMIN only and nothing leaks. Recommendation: bound `asOf` (for example years 2000–2100)
with 400 naming `asOf`.

### MINOR-2: a cycle offer racing a cancel leaves an unpaid term on a CANCELLED policy
Offer creation doesn't touch the policy's `@Version` (RenewalService.java:252), so a concurrent cancel or endorsement
doesn't conflict with it. No money is affected. Recommendation: take the policy's optimistic lock in the offer
transaction.

### Resolved from S2/S3
- **S3 MINOR-1 (Basic challenge):** no `WWW-Authenticate` is sent on any 401 (ProblemJsonSecurityHandler.java:35,
  GlobalExceptionHandler.java:187). SecurityChallengeIT (19) covers it. Together with the client's
  `credentials: 'omit'`, this closes the issue.
- **S3 MINOR-2 (unbounded key echo):** `EchoedKeySanitiser` (64 characters from `[A-Za-z0-9_.-]`) is wired into every
  echo site.
- **S3 MINOR-3 (raw-path `instance`):** fixed URNs everywhere, and ProblemErrorController replaces the container error
  page.
- **S3 note (500 on sequence exhaustion):** now a fixed 503 `POLICY_NUMBER_SEQUENCE_EXHAUSTED`.

### Notes (no action)
- There is no dedicated test for two concurrent pays (the protection is structural). Consider adding one.
- The seed's idempotency marker is the synthetic Aadhaar for each customer.
- After an early renewal payment, term 1's unexpired days can't be refunded (G-5). This works against the customer and
  is a product question.
- `frontend/coverage/lcov.info` is still committed.

VERDICT: REQUEST CHANGES

## Re-review (2026-11-04)

Scope: spec commit `404b5e4` (AC-07.7, AC-07.8, AC-08.6, AC-11.6), the code in `git diff 404b5e4..HEAD` (`6921161`,
`e79ade9`, `39fae46`, `2f27c0c`, `5b9af55`, `b45bb0e`), and the trace `docs/fix-loops/2026-11-04-s4-review-fixes.md`.
The orchestrator saved this section from the agent's returned text.

Evidence (JDK 21): a full `./mvnw -q verify` ran 115 classes and 1006 tests, with 0 failures and 0 errors. The new
classes are green: CancellationBackdateIT 4, RenewalRepriceIT 4, RenewalJobDateBoundIT 5, PortfolioAsOfBoundIT 2,
RenewalOfferRaceIT 2, and RenewalPaymentIT 8 (including the new two-payment race).

| Finding | Status | Evidence |
|---|---|---|
| BLOCKER-1: backdated cancellation | **CLOSED** | **Rule:** `CancellationDateRule.resolve` uses `max(today, termStart)` as the earliest date (:23-24), with today from the injected clock. **Preview:** cancel and preview share `CancellationService.prepare`, and the preview names `date`. **Cooling-off:** decided on a date that can no longer be in the past. **Tests:** CancellationBackdateIT checks 400 with nothing written for 2026-11-02 and for yesterday. 2027-10-20 refunds 0.00, and cooling-off on today still refunds 6850.00. |
| MAJOR-1: stale renewal offer | **CLOSED** | **Re-price:** `EndorsementService` re-quotes the pending term with the term's rule version, inside the `@Transactional` endorse, followed by a version-checked `saveAndFlush`. **Pay:** charges the term premium at payment time. **Tests:** RenewalRepriceIT checks 6850.00 → 9490.00 with the dates and rule version unchanged. An address change leaves the offer alone, and the preview writes nothing. **Pay-first-then-endorse:** the endorsement then pro-rates on term 2 (G-5), so that route doesn't work. |
| MAJOR-2: unbounded admin job date | **CLOSED** | **Bound:** `RenewalPolicy.isJobDateAllowed` (today−30..today) is applied before any read or write, as a fixed 400 naming `date`. **Switch:** `any-date` defaults to false. It is set only in `backend/src/test/resources/application.properties` and the Playwright webServer argument, never in src/main, a profile, CI or scripts. **Tests:** RenewalJobDateBoundIT. |
| MINOR-1: dashboard `asOf` 500 | **CLOSED** | `PortfolioRules.isSupportedAsOf` allows 2000-01-01..2100-12-31. Out-of-range and non-date values get one constant 400 naming `asOf` (PortfolioAsOfBoundIT). |
| MINOR-2: offer racing a cancel | **CLOSED** | **Fix:** `createOffer` calls `PolicyLocking.bumpVersion` (PESSIMISTIC_FORCE_INCREMENT). A stale policy becomes a `ConcurrencyFailureException`, and the per-policy transaction rolls back. **Tests:** RenewalOfferRaceIT covers both orderings. **Deadlocks:** no lock-order inversion found. H2 lock waits end in a timeout, which surfaces as a skip or a 409. |

### New findings
- **MINOR-A.** `PolicyTermEntity.repriceOffer` is public, and only its name is allow-listed. Its single caller,
  EndorsementService, is correct, but no rule enforces that it stays the only caller. Recommendation: an ArchUnit rule,
  like BackdatedIssuanceArchTest, that only `EndorsementService` may call it.
- **Notes:**
  - There is no IT that races an endorsement re-price against a pay. The protection is structural.
  - The detail for `CANCELLATION_DATE_OUT_OF_TERM` doesn't mention "not before today" (wording only).
  - With the test-only `any-date=true`, an extreme job date could still hit date arithmetic. That is out of production scope.

VERDICT: APPROVE

## Resolution log (orchestrator, 2026-11-04)
- **BLOCKER-1, MAJOR-1, MAJOR-2, MINOR-1:** the user decided the spec changes (commit `404b5e4`). They are fixed in
  `6921161` (red) → `39fae46` (green), and the re-review closed them.
- **MINOR-2:** fixed in the same commits. `repository/CLAUDE.md` records the locking rule (`b45bb0e`).
- **MINOR-A, and the re-price-versus-pay race test:** deferred to Sprint 5.
