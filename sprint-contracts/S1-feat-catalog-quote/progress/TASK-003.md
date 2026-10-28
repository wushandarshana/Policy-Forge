# TASK-003 Progress

## Status
DONE

## What was built
The product catalog end to end. Three seed rule files (`rules/{term-life,motor,household}/v1.json`, version 1, effectiveFrom 2026-01-01, spec factor tables, UW rules, launch lifecycle values with J5 defaults) are validated by a new pure-domain `RuleSetValidator`, which returns every violation at once, and loaded on startup by `config/RuleSetSeedLoader` into an append-only `rule_set_version` table (Flyway `V1`, NUMERIC money columns, CLOB for the exact JSON). `RuleSetService` resolves the active version with the injected Clock (`ActiveRuleSetResolver`, `NoActiveRuleSetException` extends `ResourceNotFoundException` so it maps to 404), lists products and versions, and publishes new versions (current max + 1, validated first, row plus `audit_entry` written in one transaction via `AuditService`, Flyway `V2`). `ProductController` serves `GET /api/products`, `GET /api/products/{code}/versions` (public) and `POST /api/products/{code}/versions` (`@PreAuthorize` PRODUCT_MANAGER, so CUSTOMER gets 403). Entities have no setters and are `@Immutable`; repositories extend `Repository` with only `save` and finders.

Evidence: `./mvnw verify` exits 0 with 74 tests passing (36 from TASK-001/002 baseline, 38 new). Overall line coverage 85.4%, domain 90.3% (thresholds 70/80 unchanged). Red log shows AC-02.1 to AC-02.7 failing before implementation (404s, `UnsupportedOperationException("red: …")`, and ArchUnit "no classes matched" for the absent rule-set entity/repository per J3). Green log shows all passing. The oracle reads the rule files in `backend/src/main/resources/rules/` and gives 6850.00 (AC-01.1), 15150.00 (AC-01.2), 11350.00 (AC-01.3); `premium_check.py spec-examples` passes all 9 cases. A scripted policy-version-validator checklist (JSON-string decimals, contiguous inclusive bands, unique prefixed codes, known operators, complete lifecycle) passes for all three files. `/ac-coverage` offline run lists AC-02.1 to AC-02.7 as covered (its report file was removed again, since archiving audits is outside this task).

## Files changed
Seed rule files:
- backend/src/main/resources/rules/term-life/v1.json
- backend/src/main/resources/rules/motor/v1.json
- backend/src/main/resources/rules/household/v1.json

Migrations:
- backend/src/main/resources/db/migration/V1__rule_set_version.sql
- backend/src/main/resources/db/migration/V2__audit_entry.sql

Domain (new package `domain/ruleset`, java.* only):
- backend/src/main/java/com/horizon/policyforge/domain/ruleset/{RatingModel,ConditionOperator,Band,RatingFactors,SumInsuredLimits,Lifecycle,Condition,UnderwritingRule,RuleSetVersion,RuleSetViolation,RuleSetValidator,ActiveRuleSetResolver}.java

Repository:
- backend/src/main/java/com/horizon/policyforge/repository/{RuleSetVersionEntity,RuleSetVersionRepository,AuditEntryEntity,AuditEntryRepository}.java

Service:
- backend/src/main/java/com/horizon/policyforge/service/{RuleSetService,RuleSetJsonParser,AuditService,NoActiveRuleSetException,PublishedRuleSet,ProductSummary}.java

Config and web:
- backend/src/main/java/com/horizon/policyforge/config/DomainConfig.java
- backend/src/main/java/com/horizon/policyforge/config/RuleSetSeedLoader.java
- backend/src/main/java/com/horizon/policyforge/web/ProductController.java

Tests:
- backend/src/test/java/com/horizon/policyforge/web/ProductCatalogReadIT.java (AC-02.1, AC-02.2)
- backend/src/test/java/com/horizon/policyforge/web/RuleSetPublishIT.java (AC-02.3, 02.4, 02.5, 02.7, new-product)
- backend/src/test/java/com/horizon/policyforge/domain/ruleset/{RuleSetValidatorTest,ActiveRuleSetResolverTest,RuleSets}.java (AC-02.4, AC-02.5)
- backend/src/test/java/com/horizon/policyforge/architecture/AppendOnlyArchTest.java (AC-02.6, plus audit entry checks)
- backend/src/test/java/com/horizon/policyforge/service/{RuleSetJsonParserTest,SeedRuleFilesTest}.java
- backend/src/test/java/com/horizon/policyforge/config/RuleSetSeedLoaderIT.java
- backend/src/test/java/com/horizon/policyforge/support/{MutableClock,RuleSetFixtures}.java

Evidence (sprint workspace):
- .sprint/20261028-052716/sprint-progress/TASK-003-red.log
- .sprint/20261028-052716/sprint-progress/TASK-003-green.log

## Open issues
- **Red staging.** The seed rule files and the domain value records existed before the red run (they are data, not behaviour). `RuleSetValidator`, `ActiveRuleSetResolver` and `RuleSetService` were throwing stubs, and `NoActiveRuleSetException` was a bare class so tests compile. Migrations, entities, repositories, parser, controller and seed loader were absent. The red run used `-Dmaven.test.failure.ignore=true` so failsafe still ran after surefire failures. The red log header states all of this. The extra tests added after green (parser, seed files, seed loader, audit arch checks) have no red entry.
- **Tests that write need their own context.** `RuleSetPublishIT` uses `@DirtiesContext(AFTER_METHOD)` per test and a `@Primary` `MutableClock` test bean. `ProductCatalogReadIT` and `RuleSetSeedLoaderIT` set `@TestPropertySource(policyforge.test.context=…)` for an isolated cached context. TASK-006 tests that publish MOTOR v2 (AC-01.6) must do the same, or they will change "fresh start" for any test sharing their context. `RuleSetFixtures.motorV2(effectiveFrom)` builds the AC-01.6 fixture (baseRate 0.030).
- **AC-02.6 is in `AppendOnlyArchTest` already**, written as plain JUnit tests over `ClassFileImporter` (not `@ArchTest` fields) so `@DisplayName` shows "AC-02.6: …". It covers `RuleSetVersionEntity`/`RuleSetVersionRepository` and, as an extra, `AuditEntryEntity`/`AuditEntryRepository`. TASK-004 should extend this class (seven entities plus `QuoteEntity`, negative fixtures) rather than recreate it.
- **Design decisions to review.**
  - The rule-set domain record is also the unvalidated draft: sections may be `null` and `ratingModel`/operator are strings, so the validator can report missing sections and unknown operators. Only a validated rule set is stored or used for pricing.
  - Generic `RatingFactors` (scalars, band tables, keyed lookups, all `BigDecimal`) is how TASK-005 should read factors: e.g. `rating().scalar("baseRate")`, `bandTable("vehicleAgeFactor")`, `lookup("useFactor")`. Band edges are whole numbers. `RuleSetVersion.model()` gives the `RatingModel` enum.
  - Publish ignores any `version` in the body and assigns max + 1, rewriting only the `version` field of the stored JSON. The seed files are stored verbatim, so v1 is byte-for-byte what ships.
  - `GET /api/products` lists only products with a version in force today. A product whose versions are all future-dated is omitted.
  - The validator additionally enforces product-prefixed UW codes by rating model, known UW condition fields per model, required factor tables and keys per model (e.g. `ncbDiscount` keys 0..5), money scale at most 2, and `sumInsured.min < max` (spec says min < max; equal is rejected).
  - Publish does not require `effectiveFrom` to be later than the previous version's (the skill checklist suggests it, the catalog spec does not list it). Easy to add in the validator if wanted.
- **Not done / limits.**
  - No DB trigger rejecting UPDATE/DELETE on append-only tables (needs a Java trigger class in H2, which would couple the repository layer to H2). Immutability rests on `@Immutable`, no setters, no delete methods, ArchUnit and Flyway validate-on-migrate.
  - Concurrent publishes of the same product could hit the `(product_code, version)` unique constraint and surface as a generic 500. There is no 409 mapping yet.
  - Seed loads write no audit entry (no human actor). Only API publishes are audited, as J10 describes. The broader audit API (`GET /api/admin/audit`) is deferred.
  - The Catalog Manager UI is deferred (J13). The AC audit only reports COVERED for AC-02.x. Quote ACs are for later tasks.
- **Environment.** `JAVA_HOME` was set per command as in the plan. No git add, commit or push was made. The Windows shell choked on one very large heredoc, so files were written with the Write tool instead.
