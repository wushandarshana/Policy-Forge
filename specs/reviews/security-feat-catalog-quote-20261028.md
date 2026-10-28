# Security Review: `feat/catalog-quote` (Sprint 1)

**Reviewer:** `security-reviewer` agent · **Date:** 2026-10-28 · **Scope:** working tree on `feat/catalog-quote`
(uncommitted), covering `backend/`, `frontend/` and `sprint-contracts/S1-feat-catalog-quote/`.

> The reviewer returned this report in its hand-back message instead of writing the file. The orchestrator saved it
> here, editing the text only to fit the report format. Verdict and findings are the reviewer's.

**Test-run note (reviewer):** `./mvnw -q test -Dtest=*Security*,*Audit*,*Pii*` matched no tests, because the relevant
classes are `*IT` and run under failsafe. Pass evidence was taken from the archived
`sprint-contracts/S1-feat-catalog-quote/evidence/TASK-007-verify.log`: SecurityBaselineIT 16/16, ErrorMappingIT 12/12,
QuoteApiIT 10/10, RuleSetPublishIT 6/6, 56 total and 0 failures.

## Checklist

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Auth boundary (NFR-04) | PASS (MINOR F-6) | `SecurityConfig.java:45-52`: public routes are GET health, GET `/api/products/**`, POST `/api/quotes`, GET `/api/quotes/*`; the rest of `/api/**` and `/actuator/**` needs login. This matches app_spec §8 and NFR-04.1. `ProductController.java:80` has `@PreAuthorize("hasRole('PRODUCT_MANAGER')")`. Only health is exposed, with `show-details: never`. No H2 console or devtools. `/error` has `include-message: never` and `include-stacktrace: never`. |
| 2 | Ownership | N/A | No customer-owned resources in S1. |
| 3 | Audit | PASS | `ProductController.java:84` passes `authentication.getName()` to `RuleSetService.java:132`, which writes `audit.record(actorId, "RULE_SET_PUBLISHED", …)` in the same transaction. AC-02.3 (`RuleSetPublishIT.java:111`). |
| 4 | PII (NFR-03 / NFR-04.2) | FAIL (MAJOR F-2) | Logs are clean (IDs, path and status only), and the log-capture test is at `QuoteApiIT.java:287`. The public quote read uses a deny-list (F-2). |
| 5 | Injection | PASS | Derived queries only; no `@Query`, `nativeQuery` or `createQuery`. |
| 6 | Append-only | PASS | Repositories extend `Repository` with save and finders only. No `set*` in main. `AppendOnlyArchTest` exists. |
| 7 | Secrets and data | PASS (MINOR F-5) | The synthetic `password` comes from app_spec §2. Archived logs hold no real PII, tokens or keys. |
| 8 | Error leakage | PASS (MINOR F-4) | Generic 500 (`GlobalExceptionHandler.java:110-116`). `ErrorMappingIT.java:132-138` asserts `secret-internal-detail` is absent from the response. |

## Findings

| ID | Severity | Location | Issue | Suggested fix |
|---|---|---|---|---|
| F-1 | MAJOR | `SecurityConfig.java:37-39`; `ProductController.java:79-82` | CSRF is disabled ("no cookies, so no CSRF surface"), but browsers cache HTTP Basic credentials after the native login prompt (`WWW-Authenticate: Basic`) and attach them to cross-site requests. `publish` takes `@RequestBody String` with no `consumes`, so a cross-site `<form enctype="text/plain">` POST, which needs no preflight, can submit valid JSON and publish a rule set that takes effect today, recorded under the victim's actor ID. Every future authenticated POST has the same exposure. | Add `consumes = application/json` to state-changing endpoints and keep CORS unconfigured, so cross-origin JSON needs a preflight that fails. Optionally check `Origin`. Add a test that a `text/plain` POST returns 415. |
| F-2 | MAJOR | `QuoteService.java:40,105,125-126`; `QuoteController.java:27,53-55` | The raw `riskInputs` map is stored as submitted (unknown keys accepted). The public GET removes only `bmi` and `healthDeclarations`, so `BMI`, `health_declarations`, `medicalHistory`, `aadhaar`, `fullName` or `dateOfBirth` are stored and returned to anyone with the quote ID. NFR-04.2 says "no … other health inputs". | Use an allow-list: store and return only the rating model's declared inputs, minus health fields. Reject unknown keys with 400 (or drop them). Add a test that an unexpected key never appears in the GET response. |
| F-3 | MINOR | `QuoteService.java:105`; `V3__quote.sql` (`risk_inputs_json CLOB`) | Anonymous POST `/api/quotes` can store a payload of any size. Tomcat `maxPostSize` doesn't cover JSON. | The F-2 allow-list mostly fixes this; also add a request-size limit. |
| F-4 | MINOR | `GlobalExceptionHandler.java:92`; `RuleSetService.java:63,95` | 404 `detail` reflects the user-supplied product code and the server date. | Use a fixed detail text. |
| F-5 | MINOR | `TASK-001-red.log`, `TASK-003-red.log` (MockMvc header dumps) | Archived logs contain Basic `Authorization` headers (synthetic `customer1:password`, `admin1:password`). | Redact or strip header dumps from evidence logs. |
| F-6 | MINOR | `SecurityConfig.java:46` | `GET /api/products/**` is a public wildcard, so any future GET under it becomes public by default. | List the two §8 paths explicitly. |
| F-7 | MINOR (info) | `GlobalExceptionHandler.java:113`; `application-file.yml:4-6` | A 500 logs the full exception, so future messages must stay input-free. The H2 `sa`/blank password applies to the local dev profile only. The frontend is clean: verbatim money strings, decimals sent as strings, guarded by `noMoneyArithmetic.test.ts`, no `innerHTML`, quote ID URL-encoded. | Keep exception messages free of input. |

VERDICT: CHANGES_REQUESTED

---

## Resolution log

- **F-5:** fixed by the orchestrator in the archive. Basic `Authorization` header values in
  `sprint-contracts/S1-feat-catalog-quote/evidence/*.log` were replaced with `Basic <redacted-synthetic-credentials>`.
  The archive README records this.
- **F-1 to F-4, F-6:** fixed through the recorded fix loop
  [`docs/fix-loops/2026-10-28-s1-security-fixes.md`](../../docs/fix-loops/2026-10-28-s1-security-fixes.md). Specs were
  updated first (NFR-04.2 allow-list, NFR-04.3, AC-01.9). Failing tests were written and logged before the fix. Then:
  `./mvnw verify` 99 + 63 tests green, `npm test` 9/9, Playwright 2/2.
- **F-7:** information only; no change.
- Awaiting re-review by `security-reviewer`.

---

## Re-review (reviewer, 2026-10-28)

**Method:** I checked the working tree independently rather than relying on the fix-loop trace.
- Read the changed code: `SecurityConfig`, `QuoteController`, `ProductController`, `GlobalExceptionHandler`, `QuoteBodySizeLimitFilter`, `RiskInputSchema`, the three rating models and `QuoteService`.
- Ran the failsafe ITs: `./mvnw verify -Dit.test='SecurityBaselineIT,QuoteApiIT,RuleSetPublishIT,ErrorMappingIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false` with the Adoptium JDK 21. Result: **51/51 passed, BUILD SUCCESS**.
- Probed the running app with curl on port 18081, then stopped it. Ports 8080 and 18081 were left free.

### Original findings

| ID | Status | Evidence (live probe unless noted) |
|---|---|---|
| F-1 | **Fixed** | Both POST endpoints (`QuoteController.java` `@PostMapping(consumes = APPLICATION_JSON_VALUE)`, `ProductController.java:83`) return **415** for `text/plain;charset=utf-8`, `application/x-www-form-urlencoded`, multipart, an empty `Content-Type`, and `application/json;charset=x`. `application/json;charset=UTF-16` is accepted (still JSON, so this is correct). A cross-origin `OPTIONS` preflight with `Origin: https://evil.example` gets 401 with no `Access-Control-*` headers, so the preflight fails. |
| F-2 | **Fixed** | `RiskInputSchema` allow-list. `Age` gets 400 `errors[{field:"Age"}]`. Nested objects or arrays as values of declared scalar keys get 400. A top-level extra key (`"aadhaar"` beside `riskInputs`) is ignored by Jackson and not stored. The public read is built with `publicView`, which iterates over the declared keys. |
| F-3 | **Still open, bypassable** (see R-1) | `/api/quotes` gets 413 for Content-Length, chunked and authenticated requests. `?x=1` and `/api/./quotes` also get 413. `/api/quotes;x` and `/api//quotes` get 400 from the firewall. **But `/api/%71uotes` and `/api/quote%73` return 201.** |
| F-4 | **Fixed** | 404 detail and instance are fixed text for an unknown quote, an unknown product (POST and GET) and an unknown `/api` path. 400, 405 and 415 details are fixed. The app log holds only method, path and status. |
| F-5 | **Fixed in the S1 archive; recurred in the new fix-loop log** (see R-3) | `sprint-contracts/**` has 10 `Basic <redacted-synthetic-credentials>` and no raw values. |
| F-6 | **Fixed** | `SecurityConfig.java` lists the public matchers exactly. An anonymous GET `/api/products/MOTOR/internal` gets 401. |

The 404 `instance` value `urn:policyforge:problem:not-found` is **acceptable**. RFC 9457/7807 `instance` is any URI reference, and a URN is valid. A fixed value avoids reflecting the requested ID or code and is consistent across the advice and framework 404 paths.

### New or still-open findings

| ID | Severity | Location | Issue | Suggested fix |
|---|---|---|---|---|
| R-1 | **MAJOR** | `backend/src/main/java/com/horizon/policyforge/web/QuoteBodySizeLimitFilter.java:44-46` | `shouldNotFilter` compares the **raw** `getRequestURI()` with `"/api/quotes"`. Spring Security's matchers and Spring MVC both match on the **decoded** path, so a percent-encoded unreserved character passes the firewall, the `permitAll` rule and the controller mapping, but skips the cap. Live result: anonymous `POST /api/%71uotes` and `POST /api/quote%73` with a 40,316-byte body and a **5,020,156-byte** body returned **201** and stored the quote (padding was carried in `healthDeclarations`). This breaks AC-01.9 (body > 16 KB → 413, nothing stored) and reopens F-3, an unauthenticated memory and storage DoS on in-memory H2. The IT only uses the canonical path. | Don't match on the raw URI. Either (a) apply the cap to every request body on `/api/**` regardless of path (or at the connector level), or (b) match on the decoded, normalised lookup path (`UrlPathHelper.defaultInstance.getLookupPathForRequest(request)` or `ServletRequestPathUtils`). Add an AC-01.9 test that posts > 16 KB to `/api/%71uotes` and expects 413 or 404, never 201. |
| R-2 | MINOR | `backend/src/main/java/com/horizon/policyforge/domain/rating/TermLifeRatingModel.java:87`; `InputReader.list` | `healthDeclarations` accepts any list content. `[{"aadhaar":"XXXX-XXXX-1234"}]` raises no violation (live probe: no `healthDeclarations` error; `InputReader.list` checks only `instanceof List`), so it would be stored, so the allow-list stops at the key level. It isn't exposed publicly (health input), but arbitrary PII-bearing objects reach storage and will flow into later underwriting code. This is also what makes R-1 a storage DoS. | Validate elements: a list of strings (or known codes), with a maximum count and maximum length per item. |
| R-3 | MINOR | `docs/fix-loops/2026-10-28-s1-security-fixes/red.log:959` | One synthetic `Authorization: Basic …` header value (`admin1:password`) was dumped by MockMvc into the new fix-loop log. It's synthetic, but it's the F-5 pattern recurring. | Redact it the same way, and consider a hook or check that blocks `Authorization:"Basic [A-Za-z0-9+/=]{8,}` in committed logs. |
| R-4 | INFO | `GlobalExceptionHandler.java:172-175`; `RiskInputSchema.unknownKeys` | `instance` echoes the request path on 400/401/403/405/415, and `errors[].field` echoes an unknown key name (for example `<script>ZZQ`). Both are the caller's own input, returned as `application/problem+json` with `nosniff`. Key naming is required by AC-01.9. The app log records method, path and status only. No action needed beyond rendering `field` as text in the UI (React does this already). | None. |

VERDICT: CHANGES_REQUESTED

### Resolution log (round 2)
- **R-1:** `RequestBodySizeLimitFilter` is default-deny, matches on the decoded path, and runs before Spring Security.
  **R-2:** AC-01.10 enforced in the domain. **R-3:** `scripts/redact_evidence.py`, and `--check` is clean across
  `docs/fix-loops`, `sprint-contracts` and `specs/reviews`. **R-4:** information only; no change.
- Trace: `docs/fix-loops/2026-10-28-s1-security-fixes.md` § Round 2. Awaiting final verdict.

---

## Final verification (reviewer, 2026-10-28)

**Method:** I checked the working tree independently.
- Read `RequestBodySizeLimitFilter.java`, `InputReader.codeList` and `TermLifeRatingModel.java:27-31,94`, plus the Round 2 trace.
- Ran the failsafe ITs `SecurityBaselineIT`, `QuoteApiIT`, `RuleSetPublishIT`, `ErrorMappingIT` and `BodySizeLimitEndToEndIT` with JDK 21: **57/57 passed, BUILD SUCCESS**.
- Probed the live app with curl (`--path-as-is` where it matters) on port 18082, then stopped it. Ports 8080 and 18082 are free.

### R-1 (body size bypass): fixed
- **Encoded and odd paths.** I sent a 20 KB valid quote body to 17 path variants: the canonical path, `%71`, `%73`, the double-encoded `%2571`, the mixed-case `%51`, `%2e`, `%2E%2E`, `/quotes/`, `;x`, `//`, a leading `//`, `/./`, `%20`, `?a=b` and `/x/../`. Each was sent anonymously, chunked, with a bogus Basic header and as `customer1`. Every one got **413**. The overlong UTF-8 `%C1%B1`, `%00` and `%2F` variants got 400 before reaching any handler. A 15 KB control body got 201 on both `/api/quotes` and `/api/%71uotes`.
- **64 KB allowance.** It applies only when the decoded, normalised path matches `POST /api/products/{code}/versions` (including `version%73` and `%4DOTOR`) and an Authorization header is present.
  - A 50 KB body gets 401 with a bogus header (plain or chunked), 403 as `customer1`, and 400 as `admin1`, where it reaches validation. Nothing is stored.
  - Anonymous requests get 413.
  - A 70 KB body gets 413 even for admin.
  - With any credentials, a 50 KB body gets **413** on every other path I tried: trailing `/`, `;x`, `//`, `.`, `%2e%2e`, dot-segment traversal out of `/versions`, `/api/quotes`, `/api/%71uotes`, `/api/products`, `/api/applications` and `/`.
  - PUT, PATCH and GET-with-body over the cap also get 413.
- **413 body.** It is a fixed problem+json: `instance` is `urn:policyforge:problem:payload-too-large`, with no path, query or input echoed. The only echoed value is `X-Correlation-Id`, which already passes the safe-token check.
- **Residual.** A bogus `Authorization` header buys at most 64 KB of pre-auth read on the publish route only. This is accepted and documented in NFR-04.4.

### R-2 (healthDeclarations contents): fixed
Each of these gets **400** with a message that names the rule, not the value:
- nested lists, objects (including `{"aadhaar":…}`), numbers, `null` or boolean items
- 11 items, and a 3000-item array
- a 65-character code
- lower case, a space, a leading digit, a trailing `\n`
- a non-list string or object
- Unicode letters sent as exact UTF-8 bytes: `Ä`, fullwidth `Ａ`, `İ`, Kelvin `K`, a combining accent, and mathematical bold `A`

10 items and a 64-character code are accepted. The public GET returns `age`, `smoker`, `sumInsured` and `termYears` only. The app log has no declaration text.

The shell's code-page conversion can make some of the Unicode cases look accepted when typed inline, so those cases must be re-tested with exact UTF-8 bytes.

### R-3 (credentials in logs): fixed
`python -I scripts/redact_evidence.py --check docs/fix-loops sprint-contracts specs/reviews` reported `total: 0`, exit 0. My own grep for `Basic <base64>` found only `<redacted-synthetic-credentials>` and prose.

### New findings
No new BLOCKER, MAJOR or pre-Sprint-2 MINOR findings.

**Info only:**
- The 413 response is written before the Spring Security chain runs, so it lacks the security headers (`X-Content-Type-Options` and so on). The body is fixed text, so the risk is negligible.
- The UI's `toUpperCase()` normalisation (`frontend/src/pages/riskFields.ts:146`) is only a convenience. The server's ASCII-only pattern is the control.
- Still in place from the previous round (R-4): `instance` and `errors[].field` echo the caller's own path or keys on 400/401/403/405/415.

VERDICT: APPROVE
