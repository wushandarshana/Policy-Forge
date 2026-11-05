# TASK-005 Progress

## Status
DONE

## What was built
The underwriter side of an application. `GET /api/underwriting/queue` (UNDERWRITER) lists REFERRED applications oldest first (ID breaks ties). With `includeDeclined=true` it adds DECLINED ones for ADMIN and returns 403 to anyone else, never a silently plain queue (U-11). A value that is neither `true` nor `false` is a 400 naming `includeDeclined`. Each C4 item comes from an allow-list: full name, Aadhaar and PAN masked by `PiiMasker`, the product's declared risk inputs through `RiskInputSchema.reviewView`, and the stored `RULES` result. It carries no date of birth and no address. `POST /api/underwriting/{id}/decision` (UNDERWRITER) and `POST /api/underwriting/{id}/override` (ADMIN) both consume `application/json` only, return C5 `{applicationId, previousStatus, status}`, take the actor ID from the authenticated principal, and delegate every rule to the TASK-003 `ManualDecisionRules`.

The C3 check order is explicit in `UnderwritingService`: the body first (400, unknown keys reported by name together with the domain's `comment`, `reasonCode` and `decision` violations), then the application lookup (404 for an unknown or malformed ID, without a database lookup for a malformed one), then the state (409 `INVALID_APPLICATION_STATE`). The status change, the appended `MANUAL` or `OVERRIDE` decision row and the `UNDERWRITING_DECISION` or `UNDERWRITING_OVERRIDE` audit entry (actor, application ID, reason code, comment, previous and new status) share one transaction. The status is flushed first, so a decision that loses the `@Version` race (U-9) fails as the same 409 before any row is appended. The original `RULES` row is never touched by an override, and a manual row carries the rule version of the application's `RULES` row (the column is required).

Request bodies capture unknown keys (names only, never values) with `@JsonAnySetter` and print `[redacted]`. Commands, queue entries and queue DTOs print only the application ID, so Spring's DEBUG body and response logging cannot print a comment, a name or a risk input. `SecurityConfig` is unchanged: no new public rule and no wildcard. Each new endpoint declares its role with `@PreAuthorize` at the controller.

## Tests (TDD)
- `a985d4a test(red)` and `c6db2d7 test` (earlier in this task, tests plus the `PiiMasker` and `RiskInputSchema.reviewView` stubs). Red log: `TASK-005-red.log` (queue, decision and override endpoints answered 404, the stubs threw).
- `1a77eb4 feat(green)`: the implementation, on `feat/application-underwriting`. Green log: `TASK-005-green.log` (463 PASS lines, no FAIL or SKIP), naming AC-09.1 to AC-09.7, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4. No `refactor:` commit, because no cleanup was needed.
- Test classes (from the red commit, now green): `UnderwritingQueueIT` (AC-09.1, AC-09.7, U-7 allow-list, U-11), `UnderwritingDecisionIT` (AC-09.2, AC-09.4 to AC-09.6, check order, NFR-04.3, a real two-thread race for U-9), `UnderwritingOverrideIT` (AC-09.3 to AC-09.6, original `RULES` row unchanged, stale version), `RoleBoundaryIT` (NFR-04.1 matrix: 401 on every non-public endpoint, three public groups not 401, CUSTOMER 403 on queue, decision and override, UNDERWRITER 403 on `POST /api/applications` and `GET /api/applications/mine`), `UnderwritingBodyLimitIT` (NFR-04.4, including percent-encoded paths), `ApplicationLogCaptureIT` (NFR-03.1 extended to a queue read, a manual decision and an override), `PiiMaskerTest`, `RiskInputSchemaReviewViewTest`.

Verification: `./mvnw verify -Dskip.frontend=true` (JaCoCo on), exit 0, BUILD SUCCESS. 251 unit tests and 212 integration tests, 0 failures, 0 errors, 0 skipped. All ArchUnit tests green. JaCoCo checks met: line coverage 91.2% overall and 95.6% domain (limits 70% and 80%). `python scripts/redact_evidence.py --check .sprint/20261028-114922/sprint-progress` reports 0 and exits 0. Port 8080 is free. `dev` is still `ddeae97` and `main` is `8e265d6`, there is no `origin/feat/application-underwriting`, and nothing was pushed. The working tree is clean after the commit.

## Files changed
- backend/src/main/java/com/horizon/policyforge/domain/PiiMasker.java
- backend/src/main/java/com/horizon/policyforge/domain/rating/RiskInputSchema.java (`reviewView`)
- backend/src/main/java/com/horizon/policyforge/repository/ApplicationRepository.java (`findByStatusInOrderBySubmittedAtAscIdAsc`, `saveAndFlush` doc)
- backend/src/main/java/com/horizon/policyforge/repository/QuoteRepository.java (`findByQuoteIdIn`)
- backend/src/main/java/com/horizon/policyforge/service/UnderwritingService.java
- backend/src/main/java/com/horizon/policyforge/service/DecideCommand.java, OverrideCommand.java, DecisionResult.java, QueueEntry.java, ReasonsJson.java
- backend/src/main/java/com/horizon/policyforge/web/UnderwritingController.java
- Tests (red commits): backend/src/test/java/com/horizon/policyforge/web/UnderwritingQueueIT.java, UnderwritingDecisionIT.java, UnderwritingOverrideIT.java, UnderwritingBodyLimitIT.java, RoleBoundaryIT.java, ApplicationLogCaptureIT.java; domain/PiiMaskerTest.java, domain/rating/RiskInputSchemaReviewViewTest.java; support/ApplicationApi.java, ApplicationRows.java
- .sprint/20261028-114922/sprint-progress/TASK-005-red.log, TASK-005-green.log (workspace, gitignored)

## Open issues
- The commit trailer is `Co-Authored-By: Claude Sonnet 5.5`, following the attribution line in this session's system instructions and matching the TASK-004 and TASK-005 red commits. The task text named `Claude Opus 5.5` (the TASK-001 to TASK-003 trailer). Say so if one trailer is wanted throughout.
- This run found TASK-005's production code already written but uncommitted in the working tree (left by an interrupted earlier run). It was reviewed against C3 to C5, U-7, U-9 to U-12 and the web/CLAUDE.md rules (KD-007 to KD-010), proved by the full green run, and then committed unchanged.
- A failure to read stored quote data or a decision row (`IllegalStateException`) surfaces as a generic 500. That cannot occur for data this application wrote, and it fails closed.
- Seed awareness (U-14): the queue always contains the seeded `customer2` REFERRED application. Tests and the TASK-006 E2E journeys must find rows by application ID, never by position or count.
- For TASK-006: the SPA detects an ADMIN by whether `includeDeclined=true` returns 200, and the API gives 403 otherwise (U-11). The queue, decision and override endpoints are now in commit `1a77eb4`, so the Apply, Status and Workbench journeys can run against them.
- A Windows warning appears on commit ("LF will be replaced by CRLF"); the build is unaffected.
