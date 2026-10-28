# TASK-005 Progress

## Status
DONE

## What was built
The quote engine domain in a new pure-Java package `domain/rating`. `PremiumCalculator` is a facade that selects `TermLifeRatingModel`, `MotorRatingModel` or `HouseholdRatingModel` from the rule set's `ratingModel` (via `RatingModel` enum) and returns a `PremiumBreakdown` (annual premium, sum insured, ordered `PremiumFactor` list). `RiskInputValidator` runs the same per-model validation and returns one `RiskInputViolation(field, message)` per missing or invalid input, including sum insured outside min/max (J9 field names: `sumInsured` for TERM_LIFE, `vehicleValue` for MOTOR, derived `sumInsured` for HOUSEHOLD). Pricing an invalid input throws `InvalidRiskInputException` (extends the new `PolicyForgeDomainException`) and prices nothing. All factors, rates, bands, fees and enumeration keys are read from the `RuleSetVersion`; arithmetic is `BigDecimal` only through a new `domain/Money` helper (scale 10 HALF_UP after every multiply or divide, one scale-2 HALF_UP rounding after the policy fee).

Evidence: `./mvnw -B -ntp clean verify` exits 0, 136 tests (surefire 91, failsafe 45; 24 new), JaCoCo overall 88.3%, domain 93.6% (thresholds 70/80 unchanged), all ArchUnit tests (including `DomainPurityArchTest` and `MoneyPrecisionArchTest`) pass with the new package. Red log shows all 24 new tests failing against throwing stubs; green log shows them passing. Oracle (`premium_check.py quote`, reading `backend/src/main/resources/rules/`) gives 6850.00, 15150.00, 11350.00, and 4650.00 (age 30) and 29550.00 (MOTOR, 2 claims, COMMERCIAL) which equal the Java values asserted in the tests; `premium_check.py spec-examples` shows AC-01.1/01.2/01.3/01.6 PASS (18150.00 for the 0.030 base rate, which the Java test `AC-01.6` also asserts on a rule set built from the v1 file with baseRate 0.030).

AC-to-test map (all `@DisplayName("AC-01.M: ...")` plus `@Tag("AC-01.M")`):
- AC-01.1 / 01.2 / 01.3: `PremiumCalculatorTest` exact premiums, `isEqualByComparingTo` and `scale() == 2`, from the shipped v1 files
- AC-01.4: 100 repeated quotes, one distinct premium
- AC-01.5: age 30 -> 0.80, 31 -> 1.20 in the breakdown, plus every band edge inclusive
- AC-01.6: MOTOR baseRate 0.030 -> 18150.00 with no code change
- AC-01.7: `RiskInputValidatorTest` (12 tests) plus `invalidInputsAreNotPriced`
- AC-01.8: per-product breakdown lists every applied factor (including `policyFee`), and the formula recomputed from the breakdown alone equals the premium

## Files changed
Domain (production, `backend/src/main/java/com/horizon/policyforge/domain/`):
- Money.java
- PolicyForgeDomainException.java
- rating/PremiumCalculator.java
- rating/RiskInputValidator.java
- rating/RatingAlgorithm.java
- rating/RatingAlgorithms.java
- rating/TermLifeRatingModel.java
- rating/MotorRatingModel.java
- rating/HouseholdRatingModel.java
- rating/PremiumBreakdown.java
- rating/PremiumFactor.java
- rating/RiskInputViolation.java
- rating/InvalidRiskInputException.java
- rating/InputReader.java
- rating/RatingSupport.java
- rating/SumInsuredCheck.java

Tests (`backend/src/test/java/com/horizon/policyforge/domain/rating/`):
- PremiumCalculatorTest.java
- RiskInputValidatorTest.java
- RatingFixtures.java

Evidence (sprint workspace):
- .sprint/20261028-052716/sprint-progress/TASK-005-red.log
- .sprint/20261028-052716/sprint-progress/TASK-005-green.log

No existing production or test file was modified. No git add, commit or push was made.

## Open issues
- **Contract for TASK-006.** Call `RiskInputValidator.validate(ruleSet, riskInputs)` and map each `RiskInputViolation(field, message)` to `ValidationFailedException.FieldViolation`, or catch `InvalidRiskInputException` from `PremiumCalculator.calculate`. `riskInputs` is a `Map<String, Object>`: whole numbers as `Integer`/`Long`/`BigDecimal`, money and BMI as decimal strings or `BigDecimal`, booleans as `Boolean` (or the text `true`/`false`), `healthDeclarations` as a `List`. If Jackson supplies a fractional JSON number it must use `BigDecimal` (`USE_BIG_DECIMAL_FOR_FLOATS`); a `Double` is accepted only through its `toString()` text. `PremiumBreakdown.factors()` values are `BigDecimal`; the web layer must render them with `toPlainString()` (money factors are scale 2; rates keep the rule file's scale, e.g. `0.030`, `0.80`; `claimsLast3Years` is a whole count). `PremiumBreakdown.sumInsured()` is the value to store (HOUSEHOLD is building plus contents). No beans were added to `config/DomainConfig`; the calculator and validator are stateless and can be created with `new` or registered by TASK-006.
- **Breakdown shape (design decision).** Breakdown factor names are the rule-set or input names, in formula order, and include the amounts the rates apply to (`sumInsured`, `vehicleValue`, `buildingSumInsured`, `contentsSumInsured`, `claimsLast3Years`) so the premium can be recomputed from the breakdown alone (AC-01.8). `ncbDiscount` is listed as the discount rate (applied as `1 - value`), and `claimLoading` with `claimsLast3Years` (applied as `1 + loading x claims`). The quote spec's example shows `{"factor":"baseRatePerThousand","value":"1.20"}`; that entry is present with exactly that name.
- **Validation choices beyond the spec.** Money inputs must be non-negative with at most 2 decimal places; `bmi` must be a positive decimal; numeric inputs must have at most 15 integer digits; `claimsLast3Years` and `propertyAgeYears` must be whole numbers of at least 0; enumeration checks use the keys in the rule set (so a new rule version can add a `termFactor` or `useFactor` key without code). A field that is both negative and out of range reports only the "must not be negative" violation, so each field appears once. Unknown extra inputs are ignored. `bmi` and `healthDeclarations` are validated as present and well-formed but never used in pricing (underwriting uses them).
- **HOUSEHOLD rounding detail.** The building and contents products are each rounded to scale 10 before being added. The oracle rounds their sum once. They agree for all rates with at most 10 decimal places (the v1 rates have 4), so the AC-01.3 value is identical, but a future rule set with a rate of more than 10 decimals could differ in the last intermediate digit.
- **J8 stands.** TERM_LIFE age outside 18-65 and MOTOR `vehicleAgeYears` above 15 are violations at quote time (outside every band), as the plan chose. The quote spec's remark that vehicles over 15 are rejected by underwriting therefore only matters in the underwriting evaluator.
- **Red method.** Value types, `Money`, `PolicyForgeDomainException`, the `RatingAlgorithm` interface and `InvalidRiskInputException` existed at the red run so tests compile. `PremiumCalculator`, `RiskInputValidator` and the three models were throwing stubs (`UnsupportedOperationException("red: ...")`). The red log header states this. `InputReader`, `RatingSupport`, `RatingAlgorithms` and `SumInsuredCheck` were written in the green step.
- **Not hard-coded check.** No test scans the source for factor literals. The only literal in rating code is the divisor 1000 (the "per thousand" unit in `baseRatePerThousand`). AC-01.6 proves rates come from the rule set. `grep` for `double`, `float`, `doubleValue` and `LocalDate.now` over the new code finds nothing.
- **Environment.** `JAVA_HOME` was set per command as in the plan. Large bash heredocs failed again on this Windows shell, so the Java files were written with the Write tool. No git add, commit or push was made.
