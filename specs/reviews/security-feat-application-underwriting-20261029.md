# Security review: feat/application-underwriting (Sprint 2), 2026-10-29

Reviewer: security-reviewer agent (review only, no code changes)
Scope: git diff dev...feat/application-underwriting (17 commits, 797a4bc..b941f95): application + KYC stub (AC-03),
RULES underwriting decisions (AC-04), underwriter queue / manual decision / ADMIN override with audit (AC-09), migrations
V4-V6, React app shell (Apply, Application Status, Workbench, sign-in gate).
References: specs/app_spec.md sections 2, 5 (NFR-03.1, NFR-04.1-04.4), 8; specs/underwriting_spec.md U-1..U-15;
backend/src/main/java/com/horizon/policyforge/web/CLAUDE.md (KD-007..KD-010).

Evidence run (JDK 21): ./mvnw -q verify -Dit.test=*Security*IT,RoleBoundaryIT,Application*IT,Underwriting*IT
-Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false. All green, including RoleBoundaryIT 28,
UnderwritingDecisionIT 31, UnderwritingOverrideIT 24, UnderwritingQueueIT 10, ApplicationSubmissionIT 20,
ApplicationLogCaptureIT 4, ApplicationBodyLimitIT 3, UnderwritingBodyLimitIT 6 and SecurityBaselineIT 18.
No live app was started.

## Checklist

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Auth boundary (NFR-04), roles match section 8 | PASS | ApplicationController.java:176-177 POST /api/applications requires role CUSTOMER; :184-185 GET /mine requires CUSTOMER. UnderwritingController.java:166-167 queue requires UNDERWRITER; :179-180 decision requires UNDERWRITER; :189-190 override requires ADMIN. underwriter1 has no ADMIN role, so it gets 403. admin1 holds UNDERWRITER explicitly (section 2). includeDeclined=true from a non-ADMIN raises AccessDeniedException, which returns 403 (:172-174, isAdmin :218-219, U-11). SecurityConfig is unchanged: public matchers are exact and /api/** requires authentication (SecurityConfig.java:47-52), so no new endpoint is public. RoleBoundaryIT asserts @PreAuthorize on every /api/underwriting handler and that the actor never comes from the request. |
| 1a | KD-007 JSON-only consumes | PASS | ApplicationController.java:176, UnderwritingController.java:179 and :189 declare consumes = APPLICATION_JSON_VALUE. No @RequestBody String. |
| 1b | KD-010 default-deny body limits | PASS | RequestBodySizeLimitFilter is unchanged, so the 16 KB default covers the new paths. ApplicationBodyLimitIT and UnderwritingBodyLimitIT are green (413, nothing stored). |
| 2 | Ownership | PASS | /mine filters by Authentication#getName() (ApplicationController.java:187, which calls ApplicationService.java:161-162 findByCustomerId). There is no per-ID customer read endpoint. The SPA Status page selects from /mine (frontend/src/api/applications.ts:9-11), so applications of other customers are never returned. The customer ID on submit comes from the principal (ApplicationController.java:180), not from the body. Application IDs are A-UUIDv4 (ApplicationService.java:126). Malformed IDs get 404 without a lookup (UnderwritingService.java:62-63, 191-197). See MINOR-4. |
| 3 | Audit (NFR-04, U-12) | PASS | Decision and override each append a decision row with actorId, reasonCode and comment (UnderwritingService.java:170-172). In the same transaction they call audit.recordStatusChange with actor, action, reasonCode, comment and previous/new status (:173-174). The actor comes from authentication.getName() (UnderwritingController.java:183, 193). Submit is audited with the customer as actor and the auto decision with system (ApplicationService.java:139-140, 149-150). The status columns are added by the new migration V6. |
| 4 | PII (NFR-03) | PASS | The 3 new log calls log IDs and status only (ApplicationService.java:152-154, UnderwritingService.java:176-177, ApplicationSeedLoader.java:66, 75-76). toString is redacted on every PII carrier: KycInput, KycAddress, the Submit/Kyc/Address request classes (ApplicationController.java:58, 98, 128), Decision/OverrideRequest, KycView, QueueItemResponse, QueueEntry, Decide/OverrideCommand, ApplicationEntity and CreateQuoteRequest. Owner responses contain no KYC fields (allow-list). The non-owner queue masks Aadhaar and PAN in the service (UnderwritingService.java:238-239) and carries no DOB or address. Full name and health inputs reach underwriters only, as U-7 allows. Validation messages are fixed text and contain no values. Column sizes match the validator limits, so an H2 value-too-long error, which would carry the value, cannot occur. ApplicationLogCaptureIT is green. In the SPA, credentials and KYC live in React state only: there is no localStorage, sessionStorage, cookie or console use in frontend/src. URLs carry only quoteId and applicationId (App.tsx:16-27, 81). |
| 5 | Injection | PASS | Spring Data derived queries only. No @Query, native SQL or string-built JPQL in the diff. |
| 6 | Append-only (NFR-02) | PASS | UnderwritingDecisionEntity is @Immutable, with updatable=false columns and no setters. Its repository has save and finders only (AppendOnlyArchTest :194-195). ApplicationEntity has no setters; status changes only through applyTransition under a @Version lock. ApplicationRepository has no delete or update (:217). A lost race throws ConcurrencyFailureException, which maps to 409 INVALID_APPLICATION_STATE before anything is appended (UnderwritingService.java:160-165). It is tested in UnderwritingDecisionIT and UnderwritingOverrideIT. No existing migration was edited. |
| 7 | Secrets and data | PASS | Only the synthetic users from section 2. Aadhaar and PAN use the XXXX-XXXX-nnnn and ABCDEnnnnF shapes. The seed uses XXXX-XXXX-0002 and Demo Customer Two. No tokens or keys. |
| 8 | Error leakage | PASS (see MINOR-2, MINOR-3) | 409, 400 and 404 details are fixed. The 500 response is fixed text, with the stack trace logged only. Domain exception messages carry field names only. |

## Findings

No BLOCKER. No MAJOR.

### MINOR-1 - Basic challenge lets the browser cache credentials that Sign out cannot clear
ProblemJsonSecurityHandler.java:34 sends WWW-Authenticate: Basic on every 401, and GlobalExceptionHandler does the same for
AuthenticationException. The SPA correctly holds credentials in memory only (SessionContext.tsx:25-27), but there are two
gaps. SignInForm.tsx:122 accepts any username and password without checking them. No page clears the session on a 401.
So a mistyped password leads to a 401 with a Basic challenge, and the browser can then show its native login dialog.
Credentials typed into that dialog go into the browser HTTP auth cache, which the SPA Sign out cannot clear. They stay
attached to same-origin requests until the browser closes. The KD-007 JSON-only CSRF defence still holds; this is
session hygiene.
Recommendation: suppress the Basic challenge for SPA requests (for example those carrying X-Requested-With). Check
credentials at sign-in and sign out on any 401.

### MINOR-2 - Unknown request keys are echoed in errors[].field without a length or character bound
ApplicationController.java:53, 93, 123, the unknownKey setters of the UnderwritingController request classes,
ApplicationService.java:183 and UnderwritingService.java:202 return client-supplied key names (up to about 16 KB) as
field. U-1 and AC-03.5 require the key to be named. Still, this is the only place submitted text is reflected. Risk is
low: the response is problem+json and React escapes it.
Recommendation: truncate the echoed key (for example to 64 chars) and restrict it to [A-Za-z0-9_.-], or replace it with
a fixed placeholder.

### MINOR-3 - Problem instance echoes the raw request URI
GlobalExceptionHandler.java:196 (withInstance, unchanged since Sprint 1) sets instance to request.getRequestURI().
Decide and override validate the body before the ID (C3 order). As a result, an arbitrary path segment in
/api/underwriting/ID/decision is reflected in 400, 409 and 403 bodies. This goes against the rule that responses never
echo input. 404 already uses a fixed instance.
Recommendation: use a fixed instance per problem type, or the route template.

### MINOR-4 - Quotes are unowned, so any CUSTOMER holding a quote ID can claim it
ApplicationService.java:103-110: quotes are anonymous, and the first customer to submit gets the application. The
rightful customer then gets 409 QUOTE_ALREADY_APPLIED. IDs are Q-UUIDv4 and only leak if shared, for example through the
#/apply?quoteId= link. This matches the current spec (section 8, public quotes).
Recommendation: settle this in the issuance spec before Sprint 3 depends on application ownership. Either bind the quote
to its first authenticated user or record the accepted risk.

### Notes (no action)
- The queue shows full name and declared health inputs to every UNDERWRITER. U-7 permits this.
- ApplicationSeedLoader runs in all profiles. It is synthetic and idempotent.
- frontend/coverage/lcov.info is committed. This is hygiene, not security.

VERDICT: APPROVE
