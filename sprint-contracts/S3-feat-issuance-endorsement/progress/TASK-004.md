# TASK-004 Progress

## Status
DONE

## What was built
The policy issuance API and the append-only policy persistence under it. Flyway V8 adds `policy`, `policy_version`, `policy_term`, `policy_status_transition` and `policy_number_sequence` (no floating-point column, V1 to V7 untouched). `POST /api/applications/{id}/issue` (CUSTOMER, JSON only) follows the P-5 order (shape and date window 400, then lookup and ownership 404, then state 409). In one transaction it moves the application APPROVED to ISSUED, allocates the number, and appends the policy (ACTIVE, version 1), PolicyVersion v1 (copied from the quote and the KYC address, no nominees), PolicyTerm #1 (paid), the `null -> ACTIVE` transition (reason ISSUANCE, actor the customer) and the `APPLICATION_ISSUED` audit entry. `GET /api/policies/mine` (CUSTOMER) and `GET /api/policies/{number}` (owning CUSTOMER or ADMIN) return allow-listed C2 and C3 bodies: no KYC field, no health input, `refunds: []`, `endorsements: []` for now.

`PolicyNumberGenerator` runs inside the issuing transaction (`Propagation.MANDATORY`). It reads the per-(product, issue year) counter under a pessimistic lock and formats the number with the domain `PolicyNumberFormat`, using the issue date's year on the injected clock. A rolled-back issue gives its number back. The first issue of a product-year creates the counter row in a separate `REQUIRES_NEW` transaction (`PolicyNumberSequenceInitializer`) so two racing first issues cannot poison the issuing transaction. The application row is flushed before the number is allocated, so the loser of a same-application race gets 409 without using up a number.

Tests (all new, all in the red commit unless noted): `PolicyIssuanceIT` (12), `PolicyNumberingIT` (6, including 50 concurrent issues on a pool of 8 with the exact set 1..50), `PolicyViewIT` (4), `PolicyLogCaptureIT` (2), `PolicyBodyLimitIT` (3, real server, encoded path and chunked), the extended `RoleBoundaryIT` (policy endpoints added to the 401 and role tests, plus two new tests), 8 new NFR-02 checks in `AppendOnlyArchTest`, and `PolicyEntityTest` (6 unit tests, added with the green commit).

Commits (all on `feat/issuance-endorsement`, nothing pushed):
- `48e21e7` test(red): 25 failures and 8 errors out of 67 selected tests (404 and 403 answers, missing tables, absent classes for the ArchUnit existence checks). No stubs were needed, because the tests use only HTTP, JDBC and test support classes.
- `53a2f9b` feat(green): V8 and the production code.

Verification: `./mvnw -o -Dskip.frontend=true verify` gives BUILD SUCCESS. 349 surefire tests and 262 failsafe ITs pass. All five ArchUnit classes are green, and JaCoCo check-overall (70%) and check-domain (80%) are met. `ac_audit --offline --no-write` no longer lists AC-05.1 to AC-05.4 or AC-10.1 to AC-10.4 as NOT COVERED (AC-06.3 and AC-06.7 remain for TASK-005). `redact_evidence.py --check` over both logs gives `total: 0`. Port 8080 is free.

Logs: `.sprint/20261103-130317/sprint-progress/TASK-004-red.log` and `TASK-004-green.log`. Each keeps only the Maven `[INFO]`/`[ERROR]` lines (the red run produced 1.2 MB of Spring DEBUG output from the log-capture tests) and ends with an evidence summary naming AC-05.1 to AC-05.4, NFR-02, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4.

Interfaces for TASK-005 (cold-start notes):
- Entities and repositories are in `com.horizon.policyforge.repository`. `PolicyEntity.applyTransition(PolicyStatus target, int versionNo)` applies the state machine and sets `currentVersionNo` in one call; it is the only mutator besides `issue`. `PolicyVersionEntity.of(policyNumber, versionNo, sumInsured, premium, riskInputsJson, Address, nomineesJson, ruleVersion, effectiveDate, expiryDate, createdAt)` and `PolicyStatusTransitionEntity.of(policyNumber, from, to, reason, actorId, at)` are the append factories. `PolicyRepository.saveAndFlush` surfaces optimistic-lock failures (`ConcurrencyFailureException`) for the `POLICY_UPDATE_CONFLICT` path. `PolicyVersionRepository.findByPolicyNumberOrderByVersionNoAsc` and `PolicyTermRepository.findByPolicyNumberOrderByTermNoAsc` return the history.
- `PolicyHeader.of(policy, currentVersion, currentTerm, endorsementCount)` builds the C1 header. Today `PolicyQueryService.header(...)` passes `0` as the endorsement count, with a comment. TASK-005 must replace that `0` (and the `0` in `PolicyIssuanceService.issue`, which is correct there) with a count from the endorsement repository, and fill `endorsements` in `PolicyDetail` and `PolicyController.PolicyDetailResponse` (currently `List.of()`).
- Header `effectiveDate` and `expiryDate` are the **current term's** start and end (so they are stable under endorsements), and `nextPremiumDueDate` is the term end + 1 day. Version rows carry their own effective and expiry dates. The validator snapshot's term start and end come from the latest `PolicyTermEntity`.
- `PolicyQueryService.detail(number, actorId, admin)` is the 404-or-load path (malformed number, unknown number and another customer's policy all give `ResourceNotFoundException`). TASK-005 can reuse the same pattern for the owner check on endorse and preview.
- `InvalidPolicyStateException` is not yet mapped in `GlobalExceptionHandler`; TASK-005 adds the 409 `/problems/invalid-policy-state` mapping.
- Test support: `PolicyApi` (issue, mine, detail, `HEADER_FIELDS`, `sequenceOf`) and `PolicyRows` (JDBC reads of policy, versions, terms, transitions, row-count `Snapshot`). The numbering tests use clock years 2030 to 2036, so TASK-005 tests should avoid those years for numbering assertions.

## Files changed
- backend/src/main/resources/db/migration/V8__policy.sql
- backend/src/main/java/com/horizon/policyforge/repository/PolicyEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyVersionEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyTermEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyStatusTransitionEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyNumberSequenceEntity.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyVersionRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyTermRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyStatusTransitionRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyNumberSequenceRepository.java
- backend/src/main/java/com/horizon/policyforge/repository/ApplicationEntity.java (three address getters)
- backend/src/main/java/com/horizon/policyforge/service/PolicyIssuanceService.java
- backend/src/main/java/com/horizon/policyforge/service/PolicyQueryService.java
- backend/src/main/java/com/horizon/policyforge/service/PolicyNumberGenerator.java
- backend/src/main/java/com/horizon/policyforge/service/PolicyNumberSequenceInitializer.java
- backend/src/main/java/com/horizon/policyforge/service/PolicyHeader.java
- backend/src/main/java/com/horizon/policyforge/service/PolicySummary.java
- backend/src/main/java/com/horizon/policyforge/service/PolicyDetail.java
- backend/src/main/java/com/horizon/policyforge/service/IssuePolicyCommand.java
- backend/src/main/java/com/horizon/policyforge/service/UnderwritingService.java (the `APPLICATION_ID` pattern is now package-private so issuance reuses it)
- backend/src/main/java/com/horizon/policyforge/web/PolicyController.java
- backend/src/test/java/com/horizon/policyforge/web/PolicyIssuanceIT.java
- backend/src/test/java/com/horizon/policyforge/web/PolicyNumberingIT.java
- backend/src/test/java/com/horizon/policyforge/web/PolicyViewIT.java
- backend/src/test/java/com/horizon/policyforge/web/PolicyLogCaptureIT.java
- backend/src/test/java/com/horizon/policyforge/web/PolicyBodyLimitIT.java
- backend/src/test/java/com/horizon/policyforge/web/RoleBoundaryIT.java
- backend/src/test/java/com/horizon/policyforge/architecture/AppendOnlyArchTest.java
- backend/src/test/java/com/horizon/policyforge/repository/PolicyEntityTest.java
- backend/src/test/java/com/horizon/policyforge/support/PolicyApi.java
- backend/src/test/java/com/horizon/policyforge/support/PolicyRows.java
- .sprint/20261103-130317/sprint-progress/TASK-004-red.log
- .sprint/20261103-130317/sprint-progress/TASK-004-green.log

## Open issues
- The criterion list says the policy-version and transition repositories have "only `save` and finders". `PolicyRepository` and `PolicyNumberSequenceRepository` also expose `saveAndFlush`, as `ApplicationRepository` does, because the service needs the unique-key and optimistic-lock failures inside its transaction. Neither is an append-only repository, and the AppendOnlyArchTest checks (no delete, update, remove or `@Modifying`, not a CRUD repository) pass for both.
- Two ArchUnit tests I wrote in the red commit were fixed in the green commit: the helper that lists a class's public non-getter methods first ignored `is*` getters by prefix, which also hid `issue`; it now matches `(get|is)[A-Z].*`. The test intent is unchanged.
- First-issue counter creation takes a second database connection while the issuing transaction holds the first. This happens only on the very first issue of a product in a year. With the default Hikari pool of 10, 10 simultaneous first issues of one product-year could wait on each other until the connection timeout. Normal issues never take a second connection. Pre-creating the counters at startup (or a larger pool) would remove it; not done because the spec does not call for it.
- `GET /api/policies/{number}` returns `endorsements: []` and an endorsement count of 0 until TASK-005 adds the endorsement table (see the cold-start notes). `refunds` is always `[]` (P-9, SG-3).
- A request with a missing or empty body gets 400 (unreadable body), not an implicit `{}`. The SPA posts `{}` when the date is blank.
- The co-author trailer on the commits is `Claude Sonnet 5.5`, taken from the session's attribution instruction. The task text named `Claude Opus 5.5`, and the earlier commits on this branch use that name.
