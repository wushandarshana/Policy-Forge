# Security review: feat/issuance-endorsement (Sprint 3), 2026-11-03

Reviewer: security-reviewer agent (review only, no code changes)
Scope: git diff 246e7cb..HEAD (dev base, 14 commits, 127 files). Covers quote ownership (V7, AC-01.12, AC-01.13, AC-03.9),
policy issuance and viewing (POST /api/applications/{id}/issue, GET /api/policies/mine, GET /api/policies/{number}, V8),
endorsements and preview (POST /api/policies/{number}/endorsements[/preview], V9), and the React pages My Policies,
Policy Detail, Endorse and the Issue panel.
References: specs/app_spec.md sections 2, 5 (NFR-02, NFR-03.1, NFR-04.1-04.4) and 8; specs/quote-engine_spec.md (P-8);
specs/underwriting_spec.md U-2; specs/endorsement_spec.md; backend/src/main/java/com/horizon/policyforge/web/CLAUDE.md
(KD-007..KD-010). Spec changes came first, in their own commits (819a246, 0bdda51), as non-negotiable 2 requires.

Evidence run (JDK 21): ./mvnw -q verify -Dit.test=*Security*IT,RoleBoundaryIT,Policy*IT,Endorsement*IT,QuoteOwnershipIT,ApplicationSeedIT
-Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false. All green: RoleBoundaryIT 48, SecurityBaselineIT 18,
EndorsementSecurityIT 3, QuoteOwnershipIT 10, PolicyIssuanceIT 12, PolicyNumberingIT 6, PolicyViewIT 4,
PolicyBodyLimitIT 6, PolicyLogCaptureIT 2, EndorsementIT 13, EndorsementStateIT 14, EndorsementConflictIT 2,
EndorsementAtomicityIT 1, EndorsementPreviewIT 5, EndorsementRatingIT 7, EndorsementLogCaptureIT 2,
EndorsementRuleVersionIT 1 and ApplicationSeedIT 4. No live app was started.

## Checklist

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Auth boundary (NFR-04), roles match section 8 | PASS | PolicyController.java:258-259 issue requires CUSTOMER; :268-269 /policies/mine requires CUSTOMER; :275-276 detail requires CUSTOMER or ADMIN, with the ADMIN flag taken from authorities (:279-280). EndorsementController.java:381-382 endorse and :390-391 preview require CUSTOMER. All match section 8, which gains the preview row (0bdda51). SecurityConfig is unchanged, so no new path is public. POST /api/quotes stays public. QuoteController.java:99-107 derives the owner only from an authenticated, non-anonymous token holding ROLE_CUSTOMER. Invalid credentials get 401 (QuoteOwnershipIT :181). RoleBoundaryIT (48) covers 401 and 403 on every new route. |
| 1a | KD-007 JSON-only consumes | PASS | PolicyController.java:258, EndorsementController.java:381 and :390 declare consumes = APPLICATION_JSON_VALUE. No @RequestBody String. |
| 1b | KD-010 default-deny body limits | PASS | RequestBodySizeLimitFilter is unchanged. It matches the routed path and only relaxes the publish route, so the 16 KB default covers all 3 new POST routes. PolicyBodyLimitIT tests Content-Length, chunked and percent-encoded variants of the issue, endorse and preview paths: all give 413 and store nothing. |
| 2 | Ownership | PASS | Issue: PolicyIssuanceService.java:336-346. A malformed, unknown or other customer's application all give the same 404. The policy owner is the principal (:282), not the body. Detail: PolicyQueryService.java:95-103 gives the same 404 for malformed, unknown and non-owner numbers, and ADMIN is exempt as section 8 allows. Mine: :69-70 filters by principal. Endorse and preview: EndorsementService.java:263-274 gives 404 before any product or state check (:200-205). That means another customer's policy never reaches a 409 or a product-specific 400 that would reveal it exists. Quote: ApplicationService.java:105-108 reports another customer's owned quote as QUOTE_NOT_FOUND, before the expiry, already-applied and age checks (QuoteOwnershipIT :240, :256). Body-shape 400s run before the lookup on issue and endorse, so they show the same result for any ID and reveal nothing. Policy numbers are sequential (PF-TL-2026-000001) and therefore guessable. The ownership check is the control, and its 404 body is fixed (see Notes). |
| 3 | Audit (NFR-04) | PASS | Issuance is audited with the customer as actor, action APPLICATION_ISSUED and previous/new status, in the same transaction (PolicyIssuanceService.java:299-301). Endorsements are customer mutations, so section 5 requires no audit entry. They still record actor_id on the endorsement row (EndorsementService.java:141-143) and on the ENDORSED transition (:151-152). No new UNDERWRITER, ADMIN or PRODUCT_MANAGER mutation was added. ADMIN reads of policy detail are not audited; this was not required (see Notes). |
| 4 | PII (NFR-03) | PASS | There are 2 new log calls, and they log IDs, type and versions only (PolicyIssuanceService.java:303-304, EndorsementService.java:162-163). toString is redacted on every new PII carrier: Address, Nominee, EndorsementRequest, EndorsementCommand, EndorseCommand, EndorsementRequestBody, IssueRequest, VersionResponse, EndorsementResponse, PolicyDetailResponse, EndorsementCreatedResponse, PolicyVersionEntity and EndorsementEntity. Responses are built from allow-lists. The detail riskInputs uses schema.publicView, which drops health inputs (PolicyQueryService.java:148-149). changes is rebuilt per type and never copied from stored JSON (EndorsementViews.java:403-421). Policy responses carry no KYC (name, DOB, Aadhaar, PAN). GET /api/quotes/{id} carries no owner (QuoteOwnershipIT :154). The address and nominee names go only to the owner or an ADMIN. Column sizes match the parser limits: address 200/100/6 in V4, V8 and the parser (EndorsementRequestParser.java:53-55). So an H2 value-too-long error, which would carry the value into the 500 log, cannot occur. PolicyLogCaptureIT and EndorsementLogCaptureIT are green. Frontend: no localStorage, sessionStorage, cookies, console or dangerouslySetInnerHTML in frontend/src. URLs carry only applicationId and policyNumber (App.tsx:31-34). |
| 5 | Injection | PASS | Spring Data derived queries only. No @Query, native SQL, JdbcTemplate or string-built JPQL anywhere in the diff. |
| 6 | Append-only (NFR-02) and concurrency | PASS | PolicyVersionEntity, EndorsementEntity and PolicyStatusTransitionEntity are @Immutable, with updatable=false columns and no setters. Their repositories extend Repository and expose save and finders only. PolicyEntity changes only through applyTransition under @Version, with identity columns updatable=false. PolicyTermEntity changes only its paid flag (markPaid), as V8 documents. AppendOnlyArchTest is extended. EndorsementController has no PUT, PATCH or DELETE. V1-V6 are untouched; V7-V9 are new. Policy numbers come from a PESSIMISTIC_WRITE counter row inside the issuing transaction (PolicyNumberSequenceRepository.java:17-18, PolicyNumberGenerator.java:386-391). A first-of-year race is handled in a REQUIRES_NEW insert (PolicyNumberSequenceInitializer.java:432-435). A second issue of one application loses on the application @Version or on uq_policy_application_id and gets 409 (PolicyIssuanceService.java:269-291). Concurrent endorsements lose on uq_endorsement_policy_sequence, uq_policy_version_policy_version or the policy @Version and get 409 POLICY_UPDATE_CONFLICT, with nothing kept (EndorsementService.java:153-160; EndorsementConflictIT, EndorsementAtomicityIT). The preview transaction is readOnly and writes nothing (:176-183). |
| 7 | Secrets and data | PASS | Only the synthetic users from section 2, plus synthetic test names and addresses (for example Ravi Nominee, 12 Old Street, 99 New Road). No new Aadhaar or PAN values outside the XXXX-XXXX-nnnn and ABCDEnnnnF shapes. No tokens or keys. |
| 7a | Frontend credentials and money | PASS (see MINOR-1) | AC-01.13 is met. createQuote sends Basic only when credentials are non-null (frontend/src/api/quotes.ts:9-11, http.ts:56-60). getQuote and products still use requestJson with no header. On a 401 the Quote page never retries anonymously (QuotePage.tsx:76-80). There is no money arithmetic in the browser: amounts are sent as typed strings and shown as server strings. The only parseInt is sharePercent, which is not money (EndorsePage.tsx:189). |
| 8 | Error leakage | PASS (see MINOR-2, MINOR-3) | The new InvalidPolicyStateException handler has a fixed detail and code (GlobalExceptionHandler.java:147-152). POLICY_UPDATE_CONFLICT has fixed text (:192-193). 404s use the fixed instance. Validator and parser messages are fixed text. The only templated one, "must be at most N characters", carries a constant, not input. The 500 response is fixed, and IllegalStateException messages carry only the policy or application number, to the log. |

## Findings

No BLOCKER. No MAJOR.

### MINOR-1 - Basic challenge caching (S2 MINOR-1) got worse: a cached credential now sets quote ownership
frontend/src/api/http.ts:30-34 calls fetch with the default credentials mode (same-origin) on every request, including the
"anonymous" POST /api/quotes (quotes.ts:9-11 with credentials = null). ProblemJsonSecurityHandler.java and
GlobalExceptionHandler.java:170-176 still send WWW-Authenticate: Basic on 401. AC-01.13 adds a new way to reach that 401
from the public Quote page: sign in with a mistyped password, then request a quote. The response is 401 with a Basic
challenge, and the browser may show its native dialog.
Failure scenario: on a shared machine, customer1 types the right password into that native dialog, then uses Sign out in
the SPA. The browser keeps the credential in its HTTP auth cache. A second person who is not signed in requests a quote.
The SPA sends no Authorization header, but the browser attaches the cached customer1 credential. QuoteController.java:99-107
then records customer1 as owner. When the second person signs in as customer2 and applies, they get 400 QUOTE_NOT_FOUND
(the AC-03.9 path), and customer1 now owns a quote with someone else's risk inputs. Before Sprint 3, the cached credential
only affected protected calls. Now it silently changes stored data on a public endpoint. Impact is still low: the attacker
needs the native dialog to have been used, and nothing leaks.
Recommendation: send credentials omit on fetches that carry no Authorization header. Suppress the Basic challenge for SPA
requests, for example when X-Requested-With is present. Clear the session on any 401.

### MINOR-2 - Unknown-key echo (S2 MINOR-2) continues on the issue endpoint without a bound
PolicyController.java:64-67 collects every unknown key, and PolicyIssuanceService.java:314 returns each one as errors[].field
with no length or character bound. A 16 KB key is therefore reflected in full. The endorsement parser improves on S2: it
truncates echoed keys to 100 characters (EndorsementRequestParser.java:56, 181-184). It still does not restrict characters.
Net change: not worse in kind, and one more unbounded surface. The response is problem+json and React escapes it.
Recommendation: share one bounded sanitiser for echoed keys (for example 64 characters of [A-Za-z0-9_.-]) across
ApplicationService, UnderwritingService, PolicyIssuanceService and EndorsementRequestParser.

### MINOR-3 - Raw-path instance (S2 MINOR-3) now covers 3 more routes
GlobalExceptionHandler.java:212-213 (withInstance) still uses request.getRequestURI(). Issue, endorse and preview validate
the body before the path ID (P-5). So POST /api/applications/ANY-TEXT/issue with body {"x":1} returns 400 with the
arbitrary segment in instance, and the same happens for /api/policies/ANY-TEXT/endorsements[/preview]. The new
InvalidPolicyStateException handler (:147-152) also uses withInstance, but it is only reached after the number has
matched PF-(TL|MO|HH)-YYYY-NNNNNN and ownership has passed, so it reflects nothing arbitrary. Not worse in kind; the
surface is wider.
Recommendation: as in S2, use a fixed instance per problem type or the route template.

### Resolved from S2
- S2 MINOR-4 (unowned quotes) is addressed by P-8 / V7. A quote created with valid CUSTOMER credentials is bound to that
  customer, and another customer gets a QUOTE_NOT_FOUND identical to an unknown quote (ApplicationService.java:105-108,
  QuoteOwnershipIT). Anonymous quotes keep first-applicant-claims; quote-engine_spec records this as accepted.

### Notes (no action)
- Policy numbers are sequential and guessable by design (AC-05.2). Unknown, malformed and non-owner numbers all get the
  same fixed 404, so enumeration reveals nothing.
- ADMIN reads of a policy, including the insured address and nominee names, are not audited. Section 5 only requires
  audit for mutations. Consider read-audit when the admin dashboard arrives.
- At 999,999 policies per product and year, ck_policy_number_sequence_last_value fails and the issue gets a generic 500.
  This is availability, not security.
- frontend/coverage/lcov.info is still committed. This is hygiene, not security.

VERDICT: APPROVE


## Resolution log (orchestrator, 2026-11-03)
- **Minor 1 (cached Basic credentials):** fixed on the client in `e892374` (red) → `70206d6` (green). Every fetch uses
  `credentials: 'omit'`, and a 401 on an authenticated request clears the session. Server-side challenge suppression
  is deferred to Sprint 4.
- **Minor 2 (unknown-key echo):** the issue endpoint now caps echoed keys at 100 characters, matching the endorsement
  parser (same commits). A shared sanitiser that also restricts characters is deferred to Sprint 4.
- **Minor 3 (raw-path `instance`):** deferred to Sprint 4.
- Trace: `docs/fix-loops/2026-11-03-s3-review-fixes.md`.
