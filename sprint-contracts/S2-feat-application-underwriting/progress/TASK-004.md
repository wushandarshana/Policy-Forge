# TASK-004 Progress

## Status
DONE

## What was built
The slice from a stored quote to an automated underwriting decision. `POST /api/applications` (CUSTOMER only, `application/json` only) runs the U-2 checks in order and stores nothing on any failure: request shape and every KYC format error together, then quote exists (400 `QUOTE_NOT_FOUND`), within `validUntil` with that day valid (400 `QUOTE_EXPIRED`), no existing application (409 `QUOTE_ALREADY_APPLIED`, backed by the unique `quote_id` and a flushed insert for the race), KYC age (400 `KYC_AGE_MISMATCH` at `kyc.dateOfBirth`), then risk re-validation against the rule version active now (U-5). On success, in one transaction, it stores the application as SUBMITTED and moves it to UNDERWRITING, evaluates the active rule version, maps the outcome to a status, appends a `RULES` decision row (actor `system`, rule version, sorted codes plus descriptions) and the two U-12 audit entries (`APPLICATION_SUBMITTED` by the customer, `UNDERWRITING_AUTO_DECISION` by `system`, each with previous and new status). The 201 body is the C1 allow-list with no KYC field. `GET /api/applications/mine` returns the caller's own applications newest first (ties broken by ID) in the same shape.

Unknown keys are captured, not ignored: the request classes keep the key names only (never values) through `@JsonAnySetter` and the service reports each by its dotted path (`bonus`, `kyc.nationality`, `kyc.address.landmark`) together with any KYC format error. Every request class renders `[redacted]` as its string form, so Spring's DEBUG line "Read application/json to [...]" cannot print PII. The same fix was applied to the Sprint 1 `QuoteController.CreateQuoteRequest`, whose risk inputs hold BMI and health declarations: with `org.springframework.web` at DEBUG it would have logged them.

Persistence: three new migrations, V4 `application`, V5 `underwriting_decision`, V6 two nullable status columns on `audit_entry`. V1 to V3 are byte-identical, and no column uses a floating-point type. `ApplicationEntity` is a mutable aggregate with an `@Version` lock, no setters, and one status method (`applyTransition`, which applies the domain transition table). `UnderwritingDecisionEntity` is `@Immutable` with no setters, and its repository extends `Repository` with `save` and finders only. The status and source columns are mapped as text, not Hibernate enums, to avoid enum-type validation differences on H2.

Startup seed (U-14, P-5): `ApplicationSeedLoader` quotes a smoker with SI 12,000,000.00 and applies as `customer2` through the real services. It gets a `RULES` row and both audit entries, giving REFERRED with `UW-TL-002`. It is idempotent through its synthetic Aadhaar marker, and it calls `RuleSetSeedLoader.load()` first, so runner order does not matter.

## Tests (TDD)
- `4632739 test(red)`: 43 new integration tests and 4 ArchUnit existence checks, tests only (no production stubs). The red run failed 24 tests and errored 12 (missing endpoint, tables and classes). Log: `TASK-004-red.log`.
- `3001b7b feat(green)`: all of them pass. Log: `TASK-004-green.log` names AC-03.1 to 03.8, AC-04.1 to 04.5, AC-09.7, NFR-03.1, NFR-04.3 and NFR-04.4. No `refactor:` commit, because no cleanup was needed.
- New classes: `ApplicationSubmissionIT` (AC-03.1 to 03.8, NFR-04.1, NFR-04.3), `ApplicationUnderwritingIT` (AC-04.1 to 04.4 with the stored `RULES` row), `ApplicationRuleVersionIT` (AC-04.5, own context with the v2 test fixture, discarded after the class), `ApplicationSeedIT` (AC-09.7 seed, own context, idempotency), `ApplicationLogCaptureIT` (NFR-03.1, `com.horizon.policyforge` and `org.springframework.web` at DEBUG, covering quote creation, a valid referred submission with a health declaration, and a rejected invalid-KYC one), `ApplicationBodyLimitIT` (NFR-04.4 through a real server, including `/api/%61pplications`, `/api/application%73` and a chunked body, with a complete valid body padded past 16 KB so only the cap can refuse it).
- `AppendOnlyArchTest` gains NFR-02 existence checks for `UnderwritingDecisionEntity` and `UnderwritingDecisionRepository` (they failed in the red commit while the classes were absent), plus checks that `ApplicationEntity` has no public setters and exposes exactly `submit` and `applyTransition` as non-getter public methods, and that `ApplicationRepository` has no delete or update methods.
- Support: `FixedClockConfig`, `ApplicationFixtures`, `ApplicationApi` (reusable by TASK-005) and `RuleSetFixtures.termLifeV2AgeThreshold65`. `RuleSetPublishIT` now filters its audit assertions by `entity_type = 'RuleSetVersion'`, because the startup seed adds two `APPLICATION` audit rows (U-14: tests never assume counts).

Verification: `./mvnw verify -Dskip.frontend=true` is green: 237 unit tests, 111 integration tests, all ArchUnit tests, JaCoCo checks met (line coverage 90.8% overall, 95.5% domain; limits 70% and 80%). `scripts/redact_evidence.py --check` exits 0 on both logs. Port 8080 is free. `dev` is still `ddeae97` and `main` is `8e265d6`, and there is no remote branch.

## Files changed
- backend/src/main/resources/db/migration/V4__application.sql
- backend/src/main/resources/db/migration/V5__underwriting_decision.sql
- backend/src/main/resources/db/migration/V6__audit_entry_status_columns.sql
- backend/src/main/java/com/horizon/policyforge/domain/application/DecisionSource.java
- backend/src/main/java/com/horizon/policyforge/repository/ApplicationEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/ApplicationRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/UnderwritingDecisionEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/UnderwritingDecisionRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/AuditEntryEntity.java (status columns, new factory overload)
- backend/src/main/java/com/horizon/policyforge/service/ApplicationService.java
- backend/src/main/java/com/horizon/policyforge/service/ApplicationResult.java
- backend/src/main/java/com/horizon/policyforge/service/SubmitApplicationCommand.java
- backend/src/main/java/com/horizon/policyforge/service/ConflictException.java
- backend/src/main/java/com/horizon/policyforge/service/AuditService.java (`recordStatusChange`)
- backend/src/main/java/com/horizon/policyforge/service/ValidationFailedException.java (optional `code`)
- backend/src/main/java/com/horizon/policyforge/web/ApplicationController.java
- backend/src/main/java/com/horizon/policyforge/web/GlobalExceptionHandler.java (`code` property, 409 `ConflictException`)
- backend/src/main/java/com/horizon/policyforge/web/QuoteController.java (redacted `CreateQuoteRequest.toString`)
- backend/src/main/java/com/horizon/policyforge/config/DomainConfig.java (`KycValidator`, `RiskInputValidator`, `UnderwritingRuleEvaluator` beans)
- backend/src/main/java/com/horizon/policyforge/config/ApplicationSeedLoader.java
- backend/src/test/java/com/horizon/policyforge/web/ApplicationSubmissionIT.java, ApplicationUnderwritingIT.java, ApplicationRuleVersionIT.java, ApplicationSeedIT.java, ApplicationLogCaptureIT.java, ApplicationBodyLimitIT.java
- backend/src/test/java/com/horizon/policyforge/support/FixedClockConfig.java, ApplicationFixtures.java, ApplicationApi.java, RuleSetFixtures.java
- backend/src/test/java/com/horizon/policyforge/architecture/AppendOnlyArchTest.java
- backend/src/test/java/com/horizon/policyforge/web/RuleSetPublishIT.java
- .sprint/20261028-114922/sprint-progress/TASK-004-red.log, TASK-004-green.log (workspace, gitignored)

## Open issues
- Correction to the TASK-001 note: the quote engine requires `bmi` and `healthDeclarations` on a TERM_LIFE quote (400 "is required"), so they are never absent at application time. No defaulting was added. If a future rule version names a field the quote lacks, the evaluator still fails closed (U-6): `UnderwritingEvaluationException` rolls the transaction back and surfaces as a generic 500. It is not skipped. A 400 naming the field would be a follow-up if wanted.
- For TASK-005 (all reusable as is): `ApplicationRepository.findByApplicationId`, `ApplicationEntity.applyTransition` plus `@Version` (a lost race raises `ObjectOptimisticLockingFailureException` at commit, which TASK-005 must map to 409 `INVALID_APPLICATION_STATE`), `UnderwritingDecisionEntity.of(.., DecisionSource.MANUAL|OVERRIDE, outcome "APPROVE"|"DECLINE", ..)`, `AuditService.recordStatusChange(actor, "UNDERWRITING_DECISION"|"UNDERWRITING_OVERRIDE", "APPLICATION", applicationId, reasonCode, comment, previous, new)`, and `ConflictException.INVALID_APPLICATION_STATE`, which `GlobalExceptionHandler` already maps to 409 with a fixed detail. `InvalidApplicationStateException` (domain) and `InvalidDecisionException` still need handlers or a translation in the new service. Manual decision rows should store the application's `RULES` rule version (the column is NOT NULL).
- TASK-005 DTO warning: with `org.springframework.web` at DEBUG, Spring prints response objects through `toString()` (as it did the request). The queue item carries `fullName` and masked values, so its record must override `toString()`.
- Row format of `underwriting_decision.reason_codes` is the sorted codes joined by `,`. `reasons_json` holds `[{code, description}]` in the same order.
- The seed marker is customer2 plus Aadhaar `XXXX-XXXX-0002`. Tests must not use that Aadhaar. On a file-profile database from an older run the seed adds one quote and application on first start only.
- Tests that count rows in `quote`, `application`, `underwriting_decision` or `audit_entry` must account for the seed (U-14), as `RuleSetPublishIT` now does.
- A Windows warning appears on commit ("LF will be replaced by CRLF"); the build is unaffected.
- The commit trailer on this task's two commits is `Co-Authored-By: Claude Sonnet 5.5`, following the attribution line in this session's system instructions. The earlier TASK-001 to TASK-003 commits carry `Claude Opus 5.5`, as the task text directed. Say so if one trailer is wanted throughout.
