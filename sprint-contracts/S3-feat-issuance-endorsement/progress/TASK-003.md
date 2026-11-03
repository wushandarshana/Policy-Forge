# TASK-003 Progress

## Status
DONE

## What was built
Quote ownership (P-8, C10). Flyway V7 adds a nullable `quote.owner_id`. `POST /api/quotes` stays public: the controller records the caller as owner only when the request carries valid CUSTOMER credentials (anonymous, another role, or no credentials give no owner; invalid credentials get 401 from the Basic filter before the controller, so no row is stored). The owner is set only on insert (`QuoteEntity.of(..., ownerId)`, column `updatable = false`) and is never returned, so the create and read response key sets are unchanged. `ApplicationService.submit` applies U-2 step 2 right after the quote lookup: an owned quote submitted by a different customer throws the same `QUOTE_NOT_FOUND` / `quoteId` / "was not found" error as an unknown quote, before the expiry, already-applied and KYC-age checks. Claiming an anonymous quote is the application row itself; the quote is never updated. The U-14 seed quote is created as owned by `customer2`.

Tests: new `QuoteOwnershipIT` (10 tests: AC-01.12, AC-03.9, NFR-04.1) and one added `ApplicationSeedIT` test (AC-09.7, seed quote owner). The AC-03.9 test asserts the other-customer body is JSON-equal to the unknown-quote body.

Commits (on `feat/issuance-endorsement`, nothing pushed):
- `a05f186` test(red): the ownership tests (7 of 10 new tests and the seed owner test failed before V7).
- `b44053e` feat(green): V7 and the production changes.

Verification: `./mvnw verify -Dskip.frontend=true` gives BUILD SUCCESS, 223 failsafe ITs pass, ArchUnit green, JaCoCo overall (70%) and domain (80%) checks met. `ac_audit --offline --no-write` no longer lists AC-01.12 or AC-03.9 as NOT COVERED. `redact_evidence.py --check` over TASK-003-red.log and TASK-003-green.log gives total 0 (19 MockMvc Basic header values were redacted from the red log in place).

Logs: `.sprint/20261103-130317/sprint-progress/TASK-003-red.log` and `TASK-003-green.log`, each ending with an evidence summary naming AC-01.12, AC-03.9, AC-09.7 and NFR-04.1.

Notes for later tasks:
- `QuoteService.create(product, inputs)` still exists and creates an anonymous quote. `create(product, inputs, ownerId)` takes the owner.
- `ApplicationApi.createQuote` in test support is still anonymous, so existing and later tests that quote anonymously and apply as any customer keep working. To test an owned quote, post `/api/quotes` with `httpBasic(user, "password")` as `QuoteOwnershipIT.postQuote` does.
- customer2 already owns the seed quote and has the U-14 application.

## Files changed
- backend/src/main/resources/db/migration/V7__quote_owner.sql
- backend/src/main/java/com/horizon/policyforge/repository/QuoteEntity.java
- backend/src/main/java/com/horizon/policyforge/service/QuoteService.java
- backend/src/main/java/com/horizon/policyforge/service/ApplicationService.java
- backend/src/main/java/com/horizon/policyforge/web/QuoteController.java
- backend/src/main/java/com/horizon/policyforge/config/ApplicationSeedLoader.java
- backend/src/test/java/com/horizon/policyforge/web/QuoteOwnershipIT.java
- backend/src/test/java/com/horizon/policyforge/web/ApplicationSeedIT.java
- .sprint/20261103-130317/sprint-progress/TASK-003-red.log
- .sprint/20261103-130317/sprint-progress/TASK-003-green.log

## Open issues
- None blocking. The NFR-04.1 re-proof in `QuoteOwnershipIT` duplicates the existing `RoleBoundaryIT.publicEndpointsStayPublic` on purpose (the criterion asks for it to be re-proved).
- The SecurityConfig public matchers are untouched. The ownership decision lives in the controller helper `ownerOf(Authentication)`, which only maps the authenticated principal and role to an ID.
