# TASK-005 Progress

## Status
DONE

## What was built
The endorsement API and the append-only endorsement persistence under it. Flyway V9 adds the `endorsement` table (unique on policy and sequence number, money as NUMERIC(19,2), no floating-point column, V1 to V8 untouched). `POST /api/policies/{number}/endorsements` (C4) and `POST /api/policies/{number}/endorsements/preview` (C5, plan G-1) are CUSTOMER only and consume `application/json` only. They check in the P-5 order: request shape (400, no code), policy lookup and ownership (404), the product's sum-insured keys (400, no code), the policy state (409 `/problems/invalid-policy-state`, code `INVALID_POLICY_STATE`), then the business rules E2 to E10 (400, all collected, first code as `code`). Every rule is the domain's: a new pure `EndorsementRequestParser` (shape), `EndorsementValidator`, the existing `PremiumCalculator` run with the policy's own rule version, and `EndorsementPremiumAdjuster`.

`EndorsementService.endorse` writes in one transaction, in order: the endorsement (sequence n+1, before version = current, after = current + 1), `PolicyVersion` n+1 (effective date of the endorsement, expiry kept, nominees accumulated, risk inputs replaced for a sum-insured change), then `policy.applyTransition(ENDORSED, n+1)` with `saveAndFlush`, and the `-> ENDORSED` transition row (reason: the type, actor: the customer). A lost race (unique key on the endorsement or version, or the policy's optimistic lock) answers 409 `POLICY_UPDATE_CONFLICT` with a fixed detail and nothing written. `preview` runs the same preparation in a read-only transaction. The C3 detail now carries `endorsements` (with `changes` built from a per-type allow-list, never the stored JSON), and the C2 list and every header take `endorsementCount` from the endorsement table; sum insured and premium already came from the current version.

Tests (all new, red commit unless noted): `EndorsementRequestParserTest` (44), `EndorsementIT` (13), `EndorsementRatingIT` (7), `EndorsementRuleVersionIT` (1, isolated context with a v2 TERM_LIFE fixture), `EndorsementAtomicityIT` (1, failure injected by a `@Primary` test wrapper around `PolicyVersionRepository` after the endorsement insert), `EndorsementConflictIT` (2: a deterministic race using a test-only wrapper that holds one named thread before its endorsement insert, and eight simultaneous requests), `EndorsementStateIT` (14: CANCELLED and LAPSED for endorse and preview, in-force states, eight AC-10.4 sequences), `EndorsementPreviewIT` (5), `EndorsementSecurityIT` (3), `EndorsementLogCaptureIT` (2), three new `PolicyBodyLimitIT` tests (endorse and preview, encoded paths, chunked), `RoleBoundaryIT` extended with endorse and preview, and three AC-06.7 existence checks in `AppendOnlyArchTest`. Support classes: `EndorsementApi`, `EndorsementRows`, `PolicyStates` (P-6 set-up through `applyTransition` plus an appended transition row, no raw SQL), `EndorsementTestSupport`, and `RuleSetFixtures.termLifeV2PolicyFee1000`.

Commits (all on `feat/issuance-endorsement`, nothing pushed):
- `834bc25` test(red): surefire 79 selected, 39 failures and 7 errors; failsafe 102 selected, 35 failures and 19 errors (404 and 403 answers, missing `endorsement` table, absent `EndorsementEntity` and `EndorsementRepository` for the ArchUnit checks, the parser stub accepting nothing). The only production file is the compiling parser stub.
- `f5f8171` feat(green): V9 and the production code. Two red-commit assertions were corrected in it (they expected the problem `instance`, which is the request path, to be absent from a body; they now check the `detail` and the submitted values), and the conflict wrapper now skips the repository's factory bean.
- G-1: the spec commit `0bdda51` exists in `819a246..HEAD` and precedes the first preview commit `834bc25`. No further spec commit was made.

Verification: `./mvnw -o -Dskip.frontend=true verify` gives BUILD SUCCESS. 396 surefire tests and 321 failsafe ITs pass (before this task: 349 and 262). All five ArchUnit classes pass, and JaCoCo check-overall (70%) and check-domain (80%) are met. `python scripts/ac_audit.py --offline --no-write` lists only AC-07, AC-08 and AC-11 as NOT COVERED (65 of 81 covered); AC-06.1 to AC-06.7 are covered. `redact_evidence.py --check` over `sprint-progress/` gives `total: 0`. A mutation check (removing `@Transactional` from `endorse`) makes `EndorsementAtomicityIT` fail on "no endorsement row"; the file was restored. Port 8080 is free.

Logs: `.sprint/20261103-130317/sprint-progress/TASK-005-red.log` and `TASK-005-green.log`. Each keeps only the Maven `[INFO]`/`[ERROR]` lines and ends with an evidence summary naming AC-06.1 to AC-06.7, AC-10.2, AC-10.4, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4.

Interfaces for TASK-006 and later (cold-start notes):
- Wire shapes. C4 success is 201 `{ endorsement: {sequenceNo, type, changes, beforeVersionNo, afterVersionNo, newAnnualPremium, adjustment, effectiveDate, actorId, createdAt}, policy: PolicyHeader }`. C5 success is 200 `{ type, effectiveDate, currentAnnualPremium, newAnnualPremium, adjustment, unexpiredDays, termDays }`; `unexpiredDays` and `termDays` are JSON numbers, money is a decimal string. `changes` is `{address:{line1,city,postcode}}`, `{nominee:{name,relationship,sharePercent}}`, `{newSumInsured}` or `{newBuildingSumInsured?, newContentsSumInsured?}`.
- Errors. Shape 400 carries `errors[]` with dotted fields and no `code`. Business 400 carries `code` (first in E-order) and `errors[]` with every violation, sorted by field then message. 409 state: `type` `/problems/invalid-policy-state`, `code` `INVALID_POLICY_STATE`, detail "The policy is not in a state that allows this action." 409 race: `type` `/problems/conflict`, `code` `POLICY_UPDATE_CONFLICT`. The 404 shape is the fixed one used elsewhere.
- Shape rules the SPA will meet: decimals must be JSON strings with at most 2 dp (a JSON number is a 400 on that field); `sharePercent` must be a JSON integer of at least 1 (a larger-than-100 value is a business error, `NOMINEE_SHARE_EXCEEDED`); `name`, `line1` and `city` must be non-blank (maximum 100, 200 and 100 characters); `postcode` and `relationship` only have to be text, because their format is E10 and E7; a `newSumInsured` on a HOUSEHOLD policy, or a building or contents key on a TERM_LIFE or MOTOR policy, is a 400 naming that key (after the ownership check); an explicit JSON `null` for an optional or payload key counts as absent.
- Default effective date is today on the injected clock. It must be inside the current term, so for a policy whose term has not started yet the caller has to send a date (E2).
- Test support for TASK-006: nothing new is needed for the browser tests. The unit and integration tests use clock years 2026 (issue day 2026-11-01) for endorsement numbers; the numbering tests keep using 2030 to 2036.

## Files changed
- backend/src/main/resources/db/migration/V9__endorsement.sql
- backend/src/main/java/com/horizon/policyforge/repository/EndorsementEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/EndorsementRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyVersionRepository.java (one finder, `findByPolicyNumberAndVersionNo`)
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementRequestParser.java
- backend/src/main/java/com/horizon/policyforge/service/EndorsementService.java
- backend/src/main/java/com/horizon/policyforge/service/EndorsementViews.java
- backend/src/main/java/com/horizon/policyforge/service/EndorseCommand.java
- backend/src/main/java/com/horizon/policyforge/service/EndorsementResult.java
- backend/src/main/java/com/horizon/policyforge/service/EndorsementPreview.java
- backend/src/main/java/com/horizon/policyforge/service/ConflictException.java (`POLICY_UPDATE_CONFLICT`)
- backend/src/main/java/com/horizon/policyforge/service/PolicyDetail.java (`endorsements`, `EndorsementView`)
- backend/src/main/java/com/horizon/policyforge/service/PolicyQueryService.java (endorsement count and history; the policy-number pattern is package-private for reuse)
- backend/src/main/java/com/horizon/policyforge/config/DomainConfig.java (validator, adjuster and parser beans)
- backend/src/main/java/com/horizon/policyforge/web/EndorsementController.java
- backend/src/main/java/com/horizon/policyforge/web/PolicyController.java (`EndorsementResponse`, detail `endorsements`)
- backend/src/main/java/com/horizon/policyforge/web/GlobalExceptionHandler.java (invalid-policy-state 409, conflict detail)
- backend/src/test/java/com/horizon/policyforge/domain/endorsement/EndorsementRequestParserTest.java
- backend/src/test/java/com/horizon/policyforge/architecture/AppendOnlyArchTest.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementTestSupport.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementRatingIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementRuleVersionIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementAtomicityIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementConflictIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementStateIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementPreviewIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementSecurityIT.java
- backend/src/test/java/com/horizon/policyforge/web/EndorsementLogCaptureIT.java
- backend/src/test/java/com/horizon/policyforge/web/PolicyBodyLimitIT.java
- backend/src/test/java/com/horizon/policyforge/web/RoleBoundaryIT.java
- backend/src/test/java/com/horizon/policyforge/support/EndorsementApi.java
- backend/src/test/java/com/horizon/policyforge/support/EndorsementRows.java
- backend/src/test/java/com/horizon/policyforge/support/PolicyStates.java
- backend/src/test/java/com/horizon/policyforge/support/RuleSetFixtures.java
- .sprint/20261103-130317/sprint-progress/TASK-005-red.log
- .sprint/20261103-130317/sprint-progress/TASK-005-green.log

## Open issues
- The criterion says the whole request shape is checked before the policy lookup. One part cannot be: whether a sum-insured key belongs to the policy's product needs the product. `EndorsementRequestParser.parse` checks everything it can before the lookup (including "at least one sum-insured key"), and `checkProduct` checks the product-specific keys after the ownership check and before the state check. So a foreign-product key on another customer's policy is a 404, not a 400, which leaks nothing.
- `InvalidPolicyStateException` is mapped with `instance` set to the request path (as the other 409s do), so the policy number appears in `instance`. The `detail` is fixed and never echoes input. A test written for the contract should check `detail`, not the whole body.
- The `POLICY_UPDATE_CONFLICT` path is proved deterministically for the unique-key collision (the only way two overlapping endorsements can meet, because both compute the same next sequence). The policy row's optimistic lock is the backstop behind it and is mapped to the same 409, but no test can reach it first, so it is covered by code only.
- A race loser is not retried by the service; the client sees 409 and must reload. The SPA task shows a fixed message for it.
- Sum-insured changes do not re-run underwriting (SG-1, unchanged), a backdated endorsement within the term is allowed (SG-2), and `refunds` stays `[]` (SG-3). AC-10.3's API half is the Sprint 4 carry-over (P-6).
- The co-author trailer on the commits is `Claude Sonnet 5.5`, taken from the session's attribution instruction. The task text named `Claude Opus 5.5`; this matches TASK-004.
- The H2 in-memory test database is shared per Spring context, so the three contexts added here (atomicity, conflict, rule-version) each boot once; the failsafe run is about 20 seconds longer than before.
