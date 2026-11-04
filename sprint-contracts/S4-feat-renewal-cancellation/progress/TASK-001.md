# TASK-001 Progress

## Status
DONE

## What was built
Pure domain rules in new packages `domain.renewal`, `domain.cancellation`, `domain.portfolio` plus `domain.EchoedKeySanitiser`. All are stateless final classes with instance methods (the sanitiser is static), registered as beans in `config/DomainConfig` (GracePeriodPolicy, RenewalPolicy, ProRataRefundCalculator, CancellationDateRule, RefundPremiumBasis, PortfolioRules) so TASK-003 to TASK-006 can inject them. Only `java.*` and domain imports, no double or float; DomainPurityArchTest, MoneyPrecisionArchTest and all other ArchUnit tests pass, JaCoCo overall and domain checks met.

APIs for later tasks:
- `TermPeriod(int termNo, LocalDate startDate, LocalDate endDate, boolean paid)` (domain.renewal) is the input value type for term rules; build it from the stored terms.
- `GracePeriodPolicy.isLapsed(termEnd, graceDays, runDate, paid)`.
- `RenewalPolicy`: `currentPaidTerm(terms)`, `termInForce(terms, date)`, `pendingTerm(terms)`, `isOfferDue(terms, offerDays, runDate)`, `renewalDates(termEnd, termMonths)` returning `RenewalDates(startDate, endDate)`, `payableUntil(currentPaidTermEnd, graceDays)`, `isPaymentAllowed(terms, graceDays, today)`. All return Optional or boolean and take `List<TermPeriod>`.
- `ProRataRefundCalculator.calculate(premium, termStart, termEnd, cancellationDate, policyEffectiveDate, firstTerm, coolingOffDays, cancellationFee)` returns `RefundQuote(int termDays, int unexpiredDays, boolean coolingOff, BigDecimal fee, BigDecimal amount, String formula)`. It throws IllegalArgumentException for a date outside the term, an empty term or a negative premium or fee, so run `CancellationDateRule` first. The formula strings follow C5 exactly.
- `CancellationDateRule.resolve(requested, today, termStart, termEnd, field)` returns the date or throws `InvalidCancellationDateException` (`field()`, `code()` = `CANCELLATION_DATE_OUT_OF_TERM`, fixed message). It extends PolicyForgeDomainException, so TASK-005 maps it to 400 naming `field()`.
- `RefundPremiumBasis.basis(termPremium, termStart, termEnd, List<DatedAdjustment(effectiveDate, amount)>)` returns scale 2.
- `PortfolioRules`: `premiumCollected(paidTermPremiums, adjustments, refunds)`, `total(perProduct)`, `inRenewalPipeline(PolicyStatus, terms, asOf)`, `inLapseForecast(PolicyStatus, terms, asOf)`, `PIPELINE_DAYS = 60`. Pipeline and forecast return false for a status that is not in force. Following P-11, the forecast needs no pending term, only that the current paid term ended before asOf.
- `EchoedKeySanitiser.sanitise(String)` (static): 64 characters max, `[A-Za-z0-9_.-]` kept, others become `_`, null gives "". TASK-003 must replace `PolicyIssuanceService.boundedKey` and `EndorsementRequestParser.fieldName` with it (not touched here).

Spec amendment commit `7d95f67` was verified in `12f95c3..HEAD`; no other spec commit created. G-6 vetoed: no AC-06.8 and no `SUM_INSURED_REQUIRES_UNDERWRITING` code anywhere in this task.

Commits (branch `feat/renewal-cancellation`, local only, nothing pushed):
- `c93edb1` test(red): AC-05.1 AC-06.1 AC-07.1 AC-07.2 AC-07.4 AC-07.5 AC-07.6 AC-08.1 AC-08.2 AC-08.3 AC-08.5 AC-11.2 AC-11.3 AC-11.4 (stubs returning wrong values; 36 of 42 new tests failed on assertions, 0 errors)
- `8b4d00a` feat(green): the implementations and the DomainConfig beans (all 322 tests green)

Evidence (in the workspace, `.sprint/` is git-ignored): `sprint-progress/TASK-001-red.log` and `sprint-progress/TASK-001-green.log`. The green log holds the full `./mvnw verify -Dskip.frontend=true` result and the `premium_check.py` oracle output: 4473.29, 10749.32, 8040.41, 6068.49, 6312.57, 0.00, 2929.23 (basis 8180.85), adjustment 1330.85, sum 10557.56 and `spec-examples` all PASS. Every Java value matches the oracle.

## Files changed
- backend/src/main/java/com/horizon/policyforge/domain/EchoedKeySanitiser.java
- backend/src/main/java/com/horizon/policyforge/domain/renewal/{TermPeriod,RenewalDates,GracePeriodPolicy,RenewalPolicy}.java
- backend/src/main/java/com/horizon/policyforge/domain/cancellation/{RefundQuote,ProRataRefundCalculator,InvalidCancellationDateException,CancellationDateRule,DatedAdjustment,RefundPremiumBasis}.java
- backend/src/main/java/com/horizon/policyforge/domain/portfolio/PortfolioRules.java
- backend/src/main/java/com/horizon/policyforge/config/DomainConfig.java (six new beans)
- backend/src/test/java/com/horizon/policyforge/domain/EchoedKeySanitiserTest.java
- backend/src/test/java/com/horizon/policyforge/domain/renewal/{GracePeriodPolicyTest,RenewalPolicyTest}.java
- backend/src/test/java/com/horizon/policyforge/domain/cancellation/{ProRataRefundCalculatorTest,ProRataRefundPropertyTest,CancellationDateRuleTest,RefundPremiumBasisTest}.java
- backend/src/test/java/com/horizon/policyforge/domain/portfolio/PortfolioRulesTest.java

## Open issues
- AC-08.4 (one RefundRecord, second cancel 409) is not part of this task; it belongs to TASK-005.
- The sanitiser is not yet wired into `PolicyIssuanceService` or `EndorsementRequestParser`, and the Sprint 3 100-character tests still pass unchanged; TASK-003 owns that.
- `domain/CLAUDE.md` lists the rule classes without packages. The new packages `renewal`, `cancellation` and `portfolio` are not named there (substrate, left for a human edit if wanted).
- The working tree also holds uncommitted TASK-002 frontend files, which I did not touch or stage.
- Git prints LF to CRLF warnings on commit (autocrlf); the files are not affected.
