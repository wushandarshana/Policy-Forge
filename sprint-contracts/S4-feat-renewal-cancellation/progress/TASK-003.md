# TASK-003 Progress

## Status
DONE

## What was built
Four security carry-overs from the Sprint 2/3 reviews, all in the backend, with no new endpoints.

- **No Basic challenge.** No 401 carries `WWW-Authenticate` any more: the security entry point (anonymous and the failed Basic filter share it, including a bad password on the public `POST /api/quotes`) and the exception handler no longer set it. The `CHALLENGE` constant is gone. Valid preemptive Basic still authenticates. The three old `startsWith("Basic")` assertions (SecurityBaselineIT, RoleBoundaryIT, EndorsementSecurityIT) now assert the header is absent.
- **Fixed problem `instance`.** New package-private `web/ProblemInstances` builds `urn:policyforge:problem:<slug>` from the last segment of the problem `type`, or from the hyphenated lower-case reason phrase for `about:blank` framework problems (C7). `GlobalExceptionHandler` (all handlers plus an override of `handleExceptionInternal`, which also covers bean-validation 400s that Spring used to stamp with the request path), `ProblemJsonSecurityHandler` and `RequestBodySizeLimitFilter` all use it. Found beyond the listed components: Spring Boot's own `/error` dispatch answers a firewall-refused path (`;`, `%2e%2e`, `//`) with `{"path": <raw path>}`. A new `web/ProblemErrorController` (the `ErrorController` bean, so Boot's `BasicErrorController` backs off) answers with the same fixed problem+json, no path. This was proved red first through the real embedded server (second red commit).
- **Shared key sanitiser (P-14).** `EchoedKeySanitiser.sanitise` is wired into every place that names a caller-supplied key in `errors[].field`: quote risk inputs (`RiskInputSchema.unknownKeys`), the application body (`ApplicationController.toCommand`, keys sanitised then joined to the `kyc.` and `kyc.address.` prefixes, so the prefix is kept and the key part is at most 64 characters), the decision and override (`UnderwritingService.validateBody`), issue (`PolicyIssuanceService`, `boundedKey` removed) and endorse and preview (`EndorsementRequestParser`, `MAX_FIELD_NAME` removed). Keys that sanitise alike are named once. The Sprint 3 100-character assertion in PolicyIssuanceIT is now 64.
- **Exhausted policy-number sequence.** `PolicyNumberFormat.nextSequence(lastValue)` throws the new domain `PolicyNumberSequenceExhaustedException` at 999999; `PolicyNumberSequenceEntity.allocateNext` uses it, so the counter is never moved past the limit. `GlobalExceptionHandler` maps it to 503 problem+json, type `/problems/policy-number-sequence-exhausted`, code `POLICY_NUMBER_SEQUENCE_EXHAUSTED`, fixed detail "No further policy numbers can be issued for this product at the moment." The exception rolls the issuing transaction back, so the application stays APPROVED and no policy, version, term, transition or audit row is written.

**G-6 VETOED (recorded as the criterion requires).** The user vetoed G-6 at the checkpoint, so SG-1 (sum-insured endorsements do not re-run underwriting) stays an accepted v1 limitation, as the committed spec amendment `7d95f67` says. This task implements no underwriting re-check for `CHANGE_SUM_INSURED`: there is no AC-06.8 test and no `SUM_INSURED_REQUIRES_UNDERWRITING` code anywhere (checked with grep). The amendment commit `7d95f67` exists in `12f95c3..HEAD` and precedes every code commit of this task; no spec file was touched here.

Tests added (all AC-tagged): `SecurityChallengeIT` (NFR-04.1: anonymous, bad credentials, bad credentials on the public quote POST, exception-handler 401, preemptive Basic still works), `ProblemInstanceIT` (NFR-04.1: 19 problem types each with its own URN across the exception handler, security chain, body filter and framework errors 400/401/403/404/405/413/415/500/503, plus a parameterised marker test: a marker path segment and query value sent to the issue, endorse, preview, application-status, decision and override routes, for 400/401/403/404/405/413/415, appears in no header or body and every instance matches `^urn:policyforge:problem:[a-z-]+$`), `ProblemJsonSecurityHandlerTest`, `ErrorPathEndToEndIT` (real server), a sanitiser pair per endpoint (5000-character key and a `<`, `>`, quote and space key) titled with that endpoint's existing AC (AC-05.1 issue, AC-03.5 application, AC-09.4 decision and override, AC-01.9 quote, AC-06.1 endorse and preview, each also at domain level for the parser and schema), AC-05.2 for the exhausted sequence in `PolicyNumberingIT` (counter of the unused year 2040 set with one JDBC insert; 503 twice, nothing written, counter unchanged, MOTOR in 2040 and TERM_LIFE in 2041 still issue) and domain tests for `PolicyNumberFormat.nextSequence`.

Commits (branch `feat/renewal-cancellation`, local only, nothing pushed):
- `09ac8b0` test(red): NFR-04.1 AC-05.1 AC-05.2 AC-06.1 AC-03.5 AC-09.4 AC-01.9 (stubs: `nextSequence` without the limit, the exception class not yet thrown or mapped; 102 tests failed on assertions, 0 errors)
- `fe7d6a4` test(red): NFR-04.1 container error dispatch (`ErrorPathEndToEndIT`, 4 failures on assertions)
- `af24545` feat(green): the implementation (full `./mvnw verify -Dskip.frontend=true`: 448 unit and 423 integration tests green, ArchUnit green, JaCoCo 80% domain and 70% overall met)

Evidence (workspace, `.sprint/` is git-ignored): `sprint-progress/TASK-003-red.log` and `sprint-progress/TASK-003-green.log`, both passing `scripts/redact_evidence.py --check` (total 0). `python scripts/ac_audit.py --offline --no-write` now reports 80 of 81 covered; the only gap is AC-07.3 (TASK-004). Port 8080 is free (no server was left running).

## Files changed
- backend/src/main/java/com/horizon/policyforge/web/ProblemInstances.java (new)
- backend/src/main/java/com/horizon/policyforge/web/ProblemErrorController.java (new)
- backend/src/main/java/com/horizon/policyforge/web/ProblemJsonSecurityHandler.java
- backend/src/main/java/com/horizon/policyforge/web/GlobalExceptionHandler.java
- backend/src/main/java/com/horizon/policyforge/web/RequestBodySizeLimitFilter.java
- backend/src/main/java/com/horizon/policyforge/web/ApplicationController.java
- backend/src/main/java/com/horizon/policyforge/service/PolicyIssuanceService.java
- backend/src/main/java/com/horizon/policyforge/service/UnderwritingService.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/PolicyNumberFormat.java
- backend/src/main/java/com/horizon/policyforge/domain/policy/PolicyNumberSequenceExhaustedException.java (new)
- backend/src/main/java/com/horizon/policyforge/domain/rating/RiskInputSchema.java
- backend/src/main/java/com/horizon/policyforge/domain/endorsement/EndorsementRequestParser.java
- backend/src/main/java/com/horizon/policyforge/repository/PolicyNumberSequenceEntity.java
- backend/src/test/java/com/horizon/policyforge/web/{SecurityChallengeIT,ProblemInstanceIT,ProblemJsonSecurityHandlerTest,ProblemProbeController,ErrorPathEndToEndIT}.java (new)
- backend/src/test/java/com/horizon/policyforge/web/{SecurityBaselineIT,RoleBoundaryIT,EndorsementSecurityIT,PolicyIssuanceIT,PolicyNumberingIT,ApplicationSubmissionIT,UnderwritingDecisionIT,UnderwritingOverrideIT,QuoteApiIT,EndorsementIT}.java
- backend/src/test/java/com/horizon/policyforge/domain/{policy/PolicyNumberFormatTest,rating/RiskInputSchemaTest,endorsement/EndorsementRequestParserTest}.java

## Open issues
- **Interpretation of "application-status" route** (NFR-04.1 marker test): no such route exists in app_spec section 8. The marker test covers `GET /api/applications/mine` (401, 403, 405) and `GET /api/applications/{marker}` (404, no mapping) for it. If a real application-status endpoint is added later, add it to `ProblemInstanceIT.markerProbes`.
- **Interpretation of the echoed-key length** (P-14, C7): the 64-character bound applies to the key part. A key under `kyc.` or `kyc.address.` is named as the prefix plus at most 64 characters, so such a field can be up to 77 characters. Say so if the evaluator reads the bound as 64 for the whole field.
- **A 406 has no body** when the request's `Accept` cannot be satisfied (Spring cannot write problem+json to it), so there is no `instance` to set; `ProblemInstanceIT` asserts the status, that nothing of the request path is sent, and that a body, if one ever appears, carries `urn:policyforge:problem:not-acceptable`.
- **Commit trailer.** These commits end with `Co-Authored-By: Claude Sonnet 5.5`, following the Claude Code attribution directive for this session. TASK-001 and TASK-002 commits carry `Claude Opus 5.5` (the text in the task's commit override). The trailer does not affect any check.
- **Substrate not touched (human edits, root rule 1):** `web/CLAUDE.md` could record the three new rules (no 401 challenge, fixed URN instance via `ProblemInstances`, `ProblemErrorController` owns `/error`), and `docs/knowledge-deposits.md` could get an entry for the Boot `/error` path echo. `domain/CLAUDE.md` still does not list the `renewal`, `cancellation` and `portfolio` packages (carried from TASK-001).
- SG-1 stays an accepted limitation (G-6 vetoed). The same sanitiser is not applied to the rule-set publish API, because its error fields are fixed JSON-path names, not caller-supplied keys.
- Git prints LF to CRLF warnings on commit (autocrlf); the files are not affected.
