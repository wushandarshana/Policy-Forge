# TASK-001 Progress

## Status
DONE

## What was built
Pure domain rules for the policy lifecycle and endorsements, in two new packages under `com.horizon.policyforge.domain`.

`domain.policy`:
- `PolicyStatus` (with `isInForce()` and `isTerminal()`).
- `PolicyStateMachine`, a literal translation of the spec table. A null `from` means a new policy. It has `transition` and `canTransition`.
- `InvalidPolicyStateException(from, to)`.
- `PolicyNumberFormat.format(productCode, year, sequence)`, which returns `PF-<TL|MO|HH>-<YYYY>-<NNNNNN>`.
- `InvalidPolicyNumberException`.
- `IssuanceDates`: `resolveEffectiveDate(requested, today)` accepts today to today + 60 inclusive and defaults a null date to today. `expiryDate(effective, termMonths)` is effective + months - 1 day.
- `InvalidEffectiveDateException`, with `field()` = `effectiveDate` and `code()` = `EFFECTIVE_DATE_OUT_OF_RANGE`.

`domain.endorsement`:
- `EndorsementType` (only `CHANGE_SUM_INSURED` re-rates).
- Value records: `Address`, `Nominee`, `EndorsementRequest`, `PolicySnapshot`, `EndorsementCommand`, `EndorsementViolation`, `EndorsementValidation` and `PremiumAdjustment`. `Address`, `Nominee`, `EndorsementRequest` and `EndorsementCommand` print no PII in `toString`.
- `EndorsementValidator.validate(snapshot, request, today)`. E1 throws `InvalidPolicyStateException` through `PolicyStateMachine` to ENDORSED and short-circuits. E2 to E10 are all collected in E-order, with fixed messages and dotted fields. A valid request returns a normalised `EndorsementCommand` (trimmed text, scale-2 amounts, resolved effective date).
- `EndorsementPremiumAdjuster.adjust(current, new, termStart, termEnd, effective)` returns a `PremiumAdjustment(adjustment, unexpiredDays, termDays)`. `modifiedRiskInputs(product, currentInputs, newComponents)` returns a copy with the sum-insured keys replaced by scale-2 strings.

Tests: 84 new JUnit tests across `PolicyStateMachineTest` (30-case AC-10.1 matrix, display names `AC-10.1: {from} -> {to} allowed={bool}`), `PolicyNumberFormatTest`, `IssuanceDatesTest`, `EndorsementValidatorTest` (parameterised E1-E11 matrix, P-4 display names) and `EndorsementPremiumAdjusterTest`. Adjuster values were cross-checked against `premium_check.py`: 1330.85, -1330.85, 2640.00 and 7.23. The re-rates with `PremiumCalculator` give TERM_LIFE 9490.00 and MOTOR 17025.00.

Commits (all on `feat/issuance-endorsement`, nothing pushed):
- `26c2f23` test(red): the domain tests plus compiling stubs. 61 failures and 4 NPE errors out of 84, all from stub behaviour.
- `33d8c73` feat(green): the implementation.
- Note: the red HOUSEHOLD upper-bound fixture was wrong (49.0M building + 1.0M contents equals the 50M max, which is inclusive). It was corrected to 49.5M in the green commit.

Verification: `./mvnw verify -Dskip.frontend=true` gives BUILD SUCCESS. 335 surefire tests pass and 212 failsafe ITs pass. The five ArchUnit classes are green (DomainPurity, MoneyPrecision, AppendOnly, ControllerPlacement, Layered). The JaCoCo check-overall (70%) and check-domain (80%) gates both pass. `ac_audit --offline --no-write` no longer lists AC-05.1, AC-06.1, AC-06.2, AC-06.4, AC-06.5, AC-06.6, AC-10.1, AC-10.2 or AC-10.3 as NOT COVERED. `redact_evidence.py --check` over both logs gives `total: 0`.

Logs: `.sprint/20261103-130317/sprint-progress/TASK-001-red.log` and `TASK-001-green.log`. Each ends with an evidence summary naming the AC IDs.

Interfaces for later tasks (cold-start notes):
- `PolicySnapshot.sumInsuredComponents` is keyed by risk-input name: `sumInsured` (TERM_LIFE), `vehicleValue` (MOTOR), `buildingSumInsured` and `contentsSumInsured` (HOUSEHOLD). `EndorsementCommand.newSumInsured` uses the same keys and holds only the replaced components.
- `EndorsementRequest` carries the product-specific wire fields (`newSumInsured`, `newBuildingSumInsured`, `newContentsSumInsured`). The web layer does the shape checks first (P-5). A missing payload for the type raises `IllegalArgumentException`.
- C6 fields produced: `effectiveDate`, `address.postcode`, `nominee`, `nominee.sharePercent`, `nominee.relationship`, `newSumInsured` (TERM_LIFE, MOTOR), and `newBuildingSumInsured` or `newContentsSumInsured` (HOUSEHOLD, first supplied component). For HOUSEHOLD, E8 checks the building + contents total.
- `EndorsementValidation.firstCode()` is the C6 top-level `code` (first violation in E-order).
- The adjuster does no re-rating. The service calls `PremiumCalculator` with the policy's own rule version on `modifiedRiskInputs(...)`, then `adjust(...)`. For non-re-rating types, `adjust(cur, cur, ...)` gives `0.00` plus the day counts, which is what the preview needs.
- Exception constants: `InvalidEffectiveDateException.CODE` and `.FIELD`. Validator code constants are public on `EndorsementValidator`.

## Files changed
- backend/src/main/java/com/horizon/policyforge/domain/policy/PolicyStatus.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/PolicyStateMachine.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/InvalidPolicyStateException.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/PolicyNumberFormat.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/InvalidPolicyNumberException.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/IssuanceDates.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/InvalidEffectiveDateException.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementType.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/Address.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/Nominee.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementRequest.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/PolicySnapshot.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementCommand.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementViolation.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementValidation.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementValidator.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/PremiumAdjustment.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementPremiumAdjuster.java
- backend/src/test/java/com/horizon/policyforge/domain/policy/PolicyStateMachineTest.java
- backend/src/test/java/com/horizon/policyforge/domain/policy/PolicyNumberFormatTest.java
- backend/src/test/java/com/horizon/policyforge/domain/policy/IssuanceDatesTest.java
- backend/src/test/java/com/horizon/policyforge/domain/endorsement/EndorsementFixtures.java
- backend/src/test/java/com/horizon/policyforge/domain/endorsement/EndorsementValidatorTest.java
- backend/src/test/java/com/horizon/policyforge/domain/endorsement/EndorsementPremiumAdjusterTest.java
- .sprint/20261103-130317/sprint-progress/TASK-001-red.log
- .sprint/20261103-130317/sprint-progress/TASK-001-green.log

## Open issues
- G-1: the spec commit `0bdda51` exists in `819a246..HEAD`. No further spec commit was made.
- Package choice: the policy classes live in `domain.policy` and `domain.endorsement` (matching the existing `domain.application` and `domain.rating` convention), not at the `domain` root as the skill text sketches. Later tasks should import from these packages. `domain/CLAUDE.md` lists the class names without packages, so no doc change was needed.
- The `EndorsementValidator` treats a missing payload for the request type as a programming error (`IllegalArgumentException`) rather than a violation. TASK-005 must keep the shape checks (P-5) ahead of it.
- A pre-existing dirty tree from the parallel TASK-002 (`frontend/`) was left untouched. Only TASK-001 paths were staged and committed.
- The JUnit `@Tag` on the parameterised matrix methods lists all the AC sub-ids it covers; per-row AC ids are in the display names.
