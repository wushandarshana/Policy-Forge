# domain/ — CLAUDE.md

Pure business rules: the heart of PolicyForge. **No framework code here.**

- Allowed imports: `java.*` and other `..domain..` classes. **Forbidden:** `org.springframework..`, `jakarta..`,
  `..repository..`, `..service..`, `..web..` (enforced by `DomainPurityArchTest`).
- Money is `BigDecimal` only. Use the `Money` helper (`Money.of("12.50")`, `Money.intermediate(x)` scale 10,
  `Money.finalAmount(x)` scale 2 HALF_UP). Never construct a `BigDecimal` from a `double`; use the string or long
  constructor. `double` and `float` are banned (hook + `MoneyPrecisionArchTest`).
- Value types are immutable `record`s. Collections are copied with `List.copyOf` / `Map.copyOf`.
- Each rule class is small, stateless and deterministic. Inputs come in as arguments (including dates). No clocks,
  no I/O.
- Rule classes: `rating/*RatingModel`, `PremiumCalculator`, `UnderwritingRuleEvaluator`, `ConditionEvaluator`,
  `KycValidator`, `RiskInputValidator`, `PolicyStateMachine`, `EndorsementValidator`, `EndorsementPremiumAdjuster`,
  `ProRataRefundCalculator`, `GracePeriodPolicy`, `RenewalPolicy`, `PolicyNumberFormat`, `RuleSetValidator`.
- Each rule class has a unit test with the spec's exact worked numbers, tagged with the AC.
- Domain exceptions extend `PolicyForgeDomainException`. `InvalidPolicyStateException(from, to)` is the one named in
  AC-10.
