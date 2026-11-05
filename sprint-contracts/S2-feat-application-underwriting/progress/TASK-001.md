# TASK-001 Progress

## Status
DONE

## What was built
A pure-domain underwriting engine in `com.horizon.policyforge.domain.underwriting` (imports only `java.*` and domain classes). `UnderwritingRuleEvaluator.evaluate(RuleSetVersion, Map facts)` evaluates every rule of the given version, with no rule code, threshold or product literal in code. DECLINE beats REFER, which beats AUTO_BIND. It returns every fired rule's code and description sorted by code (or `UW-OK` / "No underwriting rule fired", U-8) and carries the evaluated `ruleVersion`. `ConditionEvaluator` supports all nine operators (EQ, NEQ, GT, GTE, LT, LTE, IN, NOT_EMPTY, IS_TRUE) with `BigDecimal` built from integers or bounded plain-decimal text, so floating-point values are refused. It fails closed (U-6): an absent or null field, a missing operand, an unknown operator, an incomparable value, an unknown rule outcome or a rule with no conditions raises `UnderwritingEvaluationException`, with messages that name the rule and field, never the value. All conditions of a rule are evaluated (no short-circuit), so an absent field raises even when an earlier condition is false. `UnderwritingRuleEvaluator.withDerived(riskInputs, sumInsured, age)` builds the facts: a copy of the risk inputs plus the derived `sumInsured` (BigDecimal) and optional `age` (U-5).

Tests (40, all passing): AC-04.1 to AC-04.5 against the shipped v1 files and an isolated TERM_LIFE v2 fixture (UW-TL-001 threshold 65, built in test code from the v1 file, never a shipped rule file, U-15). AC-04.6 is a 13-row parameterised matrix, one row per v1 rule, with UW-TL-005 on BMI 46 expecting DECLINE [UW-TL-004, UW-TL-005] (KD-004) and UW-MO-002 at domain level only. A guard test asserts the matrix rows equal the 13 shipped rule codes. Operator tests and U-6 fail-closed tests cover the rest.

## Files changed
- backend/src/main/java/com/horizon/policyforge/domain/underwriting/UnderwritingOutcome.java
- backend/src/main/java/com/horizon/policyforge/domain/underwriting/ReasonCode.java
- backend/src/main/java/com/horizon/policyforge/domain/underwriting/UnderwritingResult.java
- backend/src/main/java/com/horizon/policyforge/domain/underwriting/UnderwritingEvaluationException.java
- backend/src/main/java/com/horizon/policyforge/domain/underwriting/ConditionEvaluator.java
- backend/src/main/java/com/horizon/policyforge/domain/underwriting/UnderwritingRuleEvaluator.java
- backend/src/test/java/com/horizon/policyforge/domain/underwriting/UnderwritingFixtures.java
- backend/src/test/java/com/horizon/policyforge/domain/underwriting/UnderwritingRuleEvaluatorTest.java
- backend/src/test/java/com/horizon/policyforge/domain/underwriting/UnderwritingReasonCodeMatrixTest.java
- backend/src/test/java/com/horizon/policyforge/domain/underwriting/ConditionEvaluatorTest.java
- .sprint/20261028-114922/sprint-progress/TASK-001-red.log, TASK-001-green.log (workspace, gitignored)

Commits on `feat/application-underwriting` (local only, nothing pushed):
- cd638af `test(red): AC-04.1-04.6 ...` (stubs throw UnsupportedOperationException; 40 tests run, 14 failed or errored on the stubs, including the U-6 tests expecting a domain exception)
- 831edd4 `feat(green): AC-04.1-04.6 ...`
- No `refactor:` commit: there was no cleanup to do.

Verification: `./mvnw verify -Dskip.frontend=true` passed (146 unit tests, 75 ITs, all ArchUnit tests, JaCoCo checks met). Domain line coverage is 95.1% and overall 90.4% (limits 80% and 70%). `scripts/redact_evidence.py --check` exits 0 on both logs.

## Open issues
- TASK-004 integration: U-6 means an absent `bmi` on a TERM_LIFE quote (the quote engine treats `bmi` and `healthDeclarations` as optional) makes UW-TL-004 / UW-TL-003 raise `UnderwritingEvaluationException`. TASK-004 must decide how to handle it: reject the application with a 400 or treat the missing optional input as a defined value (for example `healthDeclarations` as an empty list). It must not catch the exception and skip the rule.
- TASK-004 should build facts with `UnderwritingRuleEvaluator.withDerived(...)` and pass the sum insured as a `BigDecimal` (the quote's stored sum insured; for HOUSEHOLD, building plus contents). For TERM_LIFE `age` is already a risk input. MOTOR and HOUSEHOLD have no `age` fact unless the caller supplies one, so a rule that reads `age` there would fail closed.
- The evaluator is a plain class with a public no-arg constructor and is not yet a Spring bean. A `DomainConfig` bean is a TASK-004 concern.
- `ReasonCode` is a `(code, description)` record built from the rule set. TASK-003's manual and override catalogue (UW-MAN-*, UW-OVR-*) can reuse it or define its own type.
- The v2 TERM_LIFE rule file used by AC-04.5 exists only as a test fixture (`UnderwritingFixtures.termLifeV2AgeThreshold65`), as U-15 requires.
- A Windows warning appears on commit ("LF will be replaced by CRLF"); line endings of Java files are unaffected for the build.
