# TASK-006 Progress

## Status
DONE

## What was built
The public quote API. `POST /api/quotes` (anonymous) returns 201 in the contract C2 shape: `quoteId`, `productCode`, `ruleVersion`, `sumInsured`, `annualPremium`, `breakdown[{factor,value}]` and `validUntil`. Money and breakdown values are decimal strings from `toPlainString()`. `QuoteService` loads the product's active rule version through `RuleSetService` (so the injected Clock decides which version applies), prices with the domain `PremiumCalculator`, and stores the result. `validUntil` is the Clock's today plus `lifecycle.quoteValidityDays`. `GET /api/quotes/{id}` (anonymous) returns the same shape plus the non-health `riskInputs`, with `bmi` and `healthDeclarations` removed. The full inputs stay in `quote.risk_inputs_json`, server-side only. Quote IDs are `"Q-" + UUID.randomUUID()` (UUIDv4, SecureRandom). Unknown quote ID, unknown product, or a product with no version in force is a 404 problem+json. Invalid inputs are a 400 with `errors[]` naming each field (the `InvalidRiskInputException` violations are mapped to `ValidationFailedException`). A missing `productCode` or `riskInputs` is a 400 naming that field. Nothing is stored for a rejected request.

Persistence is Flyway `V3__quote.sql` (`NUMERIC(19,2)` money, CLOB JSON, unique `quote_id`, FK to `rule_set_version(product_code, version)`), `QuoteEntity` (`@Entity @Immutable`, no setters, static factory `of`) and `QuoteRepository` (extends `Repository`, only `save` and `findByQuoteId`). `PremiumCalculator` is registered as a bean in `config/DomainConfig`. `spring.jackson.deserialization.use-big-decimal-for-floats: true` makes fractional JSON numbers reach the domain as `BigDecimal`, never `double`. The only log statement in the quote code logs `quoteId`, `productCode` and `ruleVersion`.

Evidence: `./mvnw -B -ntp clean verify` exits 0 with 147 tests (surefire 91, failsafe 56; 11 new). JaCoCo line coverage is 89.0% overall and 93.8% for domain (thresholds 70/80 unchanged, "All coverage checks have been met"). All ArchUnit tests pass with `QuoteEntity` and `QuoteRepository` present, so the set-wide append-only rules now match a real class. The red log shows 9 of the 10 `QuoteApiIT` tests and the `QuoteRuleVersionIT` test failing before any quote code existed (404 for the unmapped path, `BadSqlGrammar` for the missing `quote` table). `python scripts/ac_audit.py --offline --no-write` lists none of AC-01.1 to 01.8, AC-02.1 to 02.7, NFR-04.2, NFR-06.1, NFR-06.2 or NFR-07.1 as not covered.

AC-to-test map:
- AC-01.1: `QuoteApiIT` 6850.00, `ruleVersion` 1, C2 shape, `validUntil` 2026-11-27 (clock fixed at 2026-10-28), and the stored row (`rule_version`, `annual_premium`)
- AC-01.2 and AC-01.3 through the API (15150.00; 11350.00 with sum insured 7000000.00): `QuoteApiIT`
- AC-01.6: `QuoteRuleVersionIT` quotes MOTOR (15150.00, v1), publishes v2 (baseRate 0.030, effective 2026-10-28, via the PM API in test setup), quotes again (18150.00, `ruleVersion` 2), and checks the old quote still reads 15150.00 v1. It uses `@DirtiesContext(AFTER_METHOD)`.
- AC-01.7: `QuoteApiIT` (several invalid fields at once give `errors[]` = smoker, sumInsured, termYears; MOTOR vehicleValue above max; missing `riskInputs`; missing `productCode`; unknown product is 404). Each asserts the `quote` row count is unchanged.
- NFR-04.2: `QuoteApiIT` stores a TERM_LIFE quote with BMI and a health declaration, asserts the DB row holds them, and asserts the anonymous GET body contains the premium, breakdown, `ruleVersion` and `validUntil`, and none of `bmi`, `healthDeclarations` or their values. It also asserts the `Q-<UUIDv4>` regex (in both the POST and the GET), that two identical quotes get different IDs, and 404 for an unknown or malformed ID.
- NFR-03 (parent tag only, so it does not claim NFR-03.1): `QuoteApiIT` log capture shows the quote ID in the log and never the health text, the BMI, `healthDeclarations` or the sum insured, for an accepted, a read and a rejected request.

## Iteration 2 rework (evaluator FAIL: green log named none of the required IDs)
Only the evidence logs changed; no production or test code was touched. I re-ran `./mvnw -B -ntp clean verify` (exit 0, BUILD SUCCESS, JaCoCo checks met). `TASK-006-green.log` now opens with a per-test section giving each QuoteApiIT and QuoteRuleVersionIT test by full display name and result, read from the failsafe XML of that same run: AC-01.1 (1 PASS), AC-01.6 (1 PASS), AC-01.7 (3 PASS), NFR-04.2 (3 PASS), plus AC-01.2, AC-01.3 and NFR-03; the raw Maven output follows. `TASK-006-red.log` now opens with a table mapping each failing method to its test ID, showing AC-01.1, AC-01.6, AC-01.7 and NFR-04.2 each failing before the quote API existed.

## Files changed
Main:
- backend/src/main/resources/db/migration/V3__quote.sql
- backend/src/main/resources/application.yml (Jackson `use-big-decimal-for-floats`)
- backend/src/main/java/com/horizon/policyforge/repository/QuoteEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/QuoteRepository.java
- backend/src/main/java/com/horizon/policyforge/service/QuoteService.java
- backend/src/main/java/com/horizon/policyforge/service/QuoteResult.java
- backend/src/main/java/com/horizon/policyforge/web/QuoteController.java
- backend/src/main/java/com/horizon/policyforge/config/DomainConfig.java (adds the `PremiumCalculator` bean)

Tests:
- backend/src/test/java/com/horizon/policyforge/web/QuoteApiIT.java
- backend/src/test/java/com/horizon/policyforge/web/QuoteRuleVersionIT.java

Evidence (sprint workspace):
- .sprint/20261028-052716/sprint-progress/TASK-006-red.log
- .sprint/20261028-052716/sprint-progress/TASK-006-green.log

No git add, commit or push was made. `SecurityConfig` already allowed anonymous `POST /api/quotes` and `GET /api/quotes/*`, so it did not change.

## Open issues
- **Red log scope.** The only passing test in the red run was "NFR-04.2: an unknown quote ID is a 404 problem+json". Spring already answers any unmapped `/api/quotes/...` path with a 404 problem+json, so that test cannot fail before the controller exists. The other NFR-04.2 tests failed as expected. The red run used `-Dit.test=... -Dmaven.test.failure.ignore=true` so only the two new classes ran.
- **What the public GET returns.** The quote spec says the read "omits health inputs (`bmi`, `healthDeclarations`) from `riskInputs`", so the GET includes a `riskInputs` object with every other stored input. `smoker` is kept in it: it is a rating factor and already visible through the breakdown's `smokerFactor`. If product wants `smoker` hidden too, add it to `HEALTH_INPUTS` in `QuoteService`. The health filter is a deny-list of two names, so a future health input needs adding there. The POST response omits `riskInputs`, matching the C2 example exactly. The frontend `Quote` type does not declare `riskInputs`, and the extra field on GET is harmless to it.
- **Unknown extra inputs are stored.** The domain ignores unknown risk-input fields, but `risk_inputs_json` keeps whatever the client sent, bounded only by the servlet container's request size limit. Consider an allow-list of input names per rating model, or a size cap, in a later sprint.
- **Product code is echoed in the 404** ("No active rule set for product X on <date>", from TASK-003's `NoActiveRuleSetException`). It is not PII, and it is the same behaviour as the catalog. The unknown-quote 404 uses a fixed message and does not echo the ID.
- **Global Jackson change.** `use-big-decimal-for-floats` applies to every request body. All 147 tests pass with it, including the catalog publish tests, whose rule files carry decimals as strings anyway.
- **Application stage not built.** `QuoteService.get` returns the public view only. The later application flow (AC-03) will need a service method that reads the full inputs from `QuoteEntity.getRiskInputsJson()`. The columns and entity already hold them.
- **Environment.** `JAVA_HOME` was set per command. A large bash heredoc failed on this Windows shell again, so the files were written with the Write tool.
