# TASK-003 Progress

## Status
DONE

## What was built
A pure-domain package `com.horizon.policyforge.domain.application` (imports only `java.*` and domain classes). `KycValidator` returns every KYC format violation at once, each with a dotted field path and a fixed message that never contains the submitted value, and takes today's date as an argument. `KycValidator.checkAge(productCode, riskInputs, kyc, today)` reports `KYC_AGE_MISMATCH` at `kyc.dateOfBirth` per U-4 (TERM_LIFE against `age`, MOTOR against `driverAge`, zero tolerance, none for HOUSEHOLD), and `ageInYears` counts completed years. `ApplicationStatus` and `ApplicationLifecycle` hold the spec's transition table (SUBMITTED to UNDERWRITING, UNDERWRITING to APPROVED/REFERRED/DECLINED via the outcome mapping, REFERRED to APPROVED/DECLINED, DECLINED to APPROVED, APPROVED to ISSUED). Any other move raises `InvalidApplicationStateException(from, to)`. `ManualReasonCatalogue` and `ManualDecisionRules` implement U-10: the UW-MAN and UW-OVR catalogue with descriptions, APPROVE/DECLINE code pairing, the trimmed 10 to 2000 character comment, decisions only from REFERRED and overrides only from DECLINED. Input is validated first (`InvalidDecisionException` with `decision`/`reasonCode`/`comment` violations), then state, so the web layer can keep the C3 order of 400 then 409. `KycInput` and `KycAddress` render `[redacted]` as their string form (P-7).

Tests (87, all passing): `KycValidatorTest` (45; AC-03.2, AC-03.4 incl. the birthday boundary, leap day, per-product age rule and the P-7 string form), `ApplicationLifecycleTest` (19; AC-03.1 incl. the full 6x6 transition matrix and "SUBMITTED cannot skip UNDERWRITING"), `ManualDecisionRulesTest` (23; AC-09.4 comment bounds, AC-09.5 state, AC-09.6 code pairing). Test names use the full `AC-NN.M:` form with `@Tag("AC-NN")`.

## Files changed
- backend/src/main/java/com/horizon/policyforge/domain/application/ApplicationStatus.java
- backend/src/main/java/com/horizon/policyforge/domain/application/ApplicationLifecycle.java
- backend/src/main/java/com/horizon/policyforge/domain/application/InvalidApplicationStateException.java
- backend/src/main/java/com/horizon/policyforge/domain/application/KycAddress.java
- backend/src/main/java/com/horizon/policyforge/domain/application/KycInput.java
- backend/src/main/java/com/horizon/policyforge/domain/application/KycViolation.java
- backend/src/main/java/com/horizon/policyforge/domain/application/KycValidator.java
- backend/src/main/java/com/horizon/policyforge/domain/application/ManualDecision.java
- backend/src/main/java/com/horizon/policyforge/domain/application/ManualReasonCatalogue.java
- backend/src/main/java/com/horizon/policyforge/domain/application/ManualDecisionOutcome.java
- backend/src/main/java/com/horizon/policyforge/domain/application/ManualDecisionRules.java
- backend/src/main/java/com/horizon/policyforge/domain/application/DecisionViolation.java
- backend/src/main/java/com/horizon/policyforge/domain/application/InvalidDecisionException.java
- backend/src/test/java/com/horizon/policyforge/domain/application/KycValidatorTest.java
- backend/src/test/java/com/horizon/policyforge/domain/application/ApplicationLifecycleTest.java
- backend/src/test/java/com/horizon/policyforge/domain/application/ManualDecisionRulesTest.java
- .sprint/20261028-114922/sprint-progress/TASK-003-red.log, TASK-003-green.log (workspace, gitignored)

Commits on `feat/application-underwriting` (local only, nothing pushed):
- 447f27d `test(red): AC-03.1 AC-03.2 AC-03.4 AC-09.4 AC-09.5 AC-09.6 ...` (stubs throw UnsupportedOperationException; 87 tests run, 86 failed or errored)
- 3647ad5 `feat(green): AC-03.1 AC-03.2 AC-03.4 AC-09.4 AC-09.5 AC-09.6 ...`
- No `refactor:` commit: no cleanup was needed.

Verification: `./mvnw verify -Dskip.frontend=true` passed (233 unit tests, 75 ITs, all ArchUnit tests including `DomainPurityArchTest` and `MoneyPrecisionArchTest`, JaCoCo checks met). Line coverage is 95.5% for domain (97.0% for `domain.application`) and 91.1% overall (limits 80% and 70%). `scripts/redact_evidence.py --check` exits 0 on both logs. Maven needed `JAVA_HOME` exported from the machine value (the documented stale-environment case).

## Open issues
- API for TASK-004, all static or stateless: `new KycValidator().validate(KycInput, today)` returns `List<KycViolation>` (field, code `KYC_INVALID`, message). `checkAge(...)` returns `Optional<KycViolation>` with code `KYC_AGE_MISMATCH`. `KycInput.dateOfBirth` is raw text, so the controller DTO must pass the string, not a parsed `LocalDate`, and must map the violation fields (already dotted, e.g. `kyc.address.postcode`) straight to `errors[]`.
- `checkAge` fails closed (throws `PolicyForgeDomainException`) if the quote's `age`/`driverAge` is absent or not a whole number. It returns empty for an unparseable date of birth, because `validate` reports that. Per U-2 the age check must run only after the format, quote-exists, expiry and already-applied checks.
- Service flow in TASK-004: `ApplicationLifecycle.startUnderwriting(SUBMITTED)` then `applyOutcome(UNDERWRITING, outcome)`. In TASK-005 map `InvalidDecisionException` to 400 (violations carry `comment`, `reasonCode`, `decision`), `InvalidApplicationStateException` to 409 `INVALID_APPLICATION_STATE`. `ManualDecisionOutcome` carries the new status, the `ReasonCode` (code and description) and the trimmed comment to store.
- TASK-005 note: unknown JSON values for `decision` (neither APPROVE nor DECLINE) are rejected at deserialisation, not in the domain. Map that to 400 naming `decision`, since the domain only sees a null.
- The ISSUED transition exists in the domain only; there is no endpoint (U-15).
- A Windows warning appears on commit ("LF will be replaced by CRLF"); the build is unaffected.
