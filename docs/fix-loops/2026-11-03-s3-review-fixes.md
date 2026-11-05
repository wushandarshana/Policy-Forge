# Fix loop: Sprint 3 review minors (cached Basic credential, unbounded key echo, mis-tagged test)

- **Detected by:** the post-sprint reviews of `feat/issuance-endorsement`. Both verdicts were APPROVE with minor findings:
  - [`specs/reviews/security-feat-issuance-endorsement-20261103.md`](../../specs/reviews/security-feat-issuance-endorsement-20261103.md)
  - [`specs/reviews/test-feat-issuance-endorsement-20261103.md`](../../specs/reviews/test-feat-issuance-endorsement-20261103.md)
- **Findings fixed in this loop:**

  | ID | Severity | Issue |
  |---|---|---|
  | SEC-MINOR-1 | MINOR | `fetch` used the default `same-origin` credentials mode. A browser-cached Basic credential (from a native auth dialog) was attached to the "anonymous" `POST /api/quotes`, so `QuoteController` recorded the wrong owner |
  | SEC-MINOR-2 | MINOR | `POST /api/applications/{id}/issue` echoed unknown request keys at full length in `errors[].field` |
  | TEST-MINOR-2 | MINOR | `EndorsePage.test.tsx` titled a not-found test `AC-10.4`, but it never exercises AC-10.4 |

The `red.log` and `green.log` linked below are local copies. `*.log` is git-ignored, as for the S1 loop. The
originals are `.sprint/20261103-130317/sprint-progress/FIX-S3-{red,green}.log`, which `/sprint-archive` preserves.
Both pass `redact_evidence.py --check`.

No spec change was needed. AC-01.13 already pins "signed out sends no credentials". The bounded echo keeps the existing
problem-detail shape, with one `errors[]` entry per unknown key and a fixed message.

## 1. Reproduce (red): `e892374`
Log: [`red.log`](2026-11-03-s3-review-fixes/red.log).

| Test | Before the fix |
|---|---|
| AC-01.13: every API request uses `credentials: 'omit'` (products, quote read, quote create signed out and signed in, applications/mine, policies/mine) | `credentials` was `undefined`, so the browser default `same-origin` applied |
| AC-01.13: signed-out quote request from the page sends `omit` and no Authorization header | `undefined` |
| AC-01.13: signed-in quote request from the page sends `omit` and only the app's Basic header | `undefined` |
| AC-01.13: a 401 on a signed-in quote request clears the session and keeps the fixed sign-in-again message | message shown, but the session stayed signed in |
| AC-01.13: a 401 on an authenticated protected request (My Policies) shows the sign-in form | the page stayed with an error banner, still signed in |
| AC-01.13: a 401 on a request without the app header leaves the session alone | passed (control; a public 401 must not sign anyone out) |
| AC-05.1: a 5000-character unknown key on issue gets 400 naming only its first 100 characters, and nothing is stored | the full 5000-character key was echoed |

## 2. Fix (green): `70206d6`
- **SEC-MINOR-1 (frontend):**
  - `frontend/src/api/http.ts`: `requestJson`, the single fetch path, now always passes `credentials: 'omit'`. The only
    credential that can travel is the explicit `Authorization` header that `requestProtected` sets for the signed-in
    user.
  - `requestProtected` runs a registered unauthorized handler when a request that carried that header gets 401.
    `SessionProvider` registers a handler that clears the in-memory session.
  - AC-01.13 behaviour is unchanged: a signed-in quote carries Basic, a signed-out one carries nothing, and product
    calls and `GET /api/quotes/{id}` carry nothing. The Quote page keeps its fixed "sign in again" message and still
    never retries anonymously. Protected pages fall back to the sign-in form.
- **SEC-MINOR-2 (backend):** `PolicyIssuanceService` cuts each unknown key to 100 characters before naming it, which
  is the bound `EndorsementRequestParser` uses (`MAX_FIELD_NAME`).
- `frontend/coverage/lcov.info` refreshed.

## 3. Retag: `b542136`
`EndorsePage.test.tsx` "shows a fixed not-found message…" is now titled `AC-06.1: the endorsement form is not offered
for a policy that cannot be found; a fixed not-found message is shown instead`. No AC pins the page-level 404 exactly.
NFR-04.1 covers only 401 and 403, so the closest fit is AC-06.1, the endorsement journey on an existing policy. The
assertions are unchanged. `EndorsementStateIT` still covers AC-10.4.

## 4. Validate
Log: [`green.log`](2026-11-03-s3-review-fixes/green.log).
- `./mvnw -o verify` (JDK 21, full build including the frontend): **BUILD SUCCESS**. 396 surefire and 322 failsafe
  tests, 0 failures, 0 errors, 0 skipped. JaCoCo checks met.
- `npm test`: 13 files, **100/100** passed. `npm run build`: OK.
- `npx playwright test --update-snapshots=none`, desktop and mobile: **28/28** passed. Port 8080 is free afterwards.
- `python scripts/redact_evidence.py --check` on the sprint progress folder and on this folder: exit 0.
- `python scripts/ac_audit.py --offline --no-write`: 65/81 covered, 0 parent-only. The 16 uncovered are AC-07, AC-08
  and AC-11, planned for later sprints.

## 5. Deferred follow-ups (not in this loop)
| Item | Source | Note |
|---|---|---|
| Suppress the `WWW-Authenticate: Basic` challenge for SPA requests (for example when `X-Requested-With` is present) | SEC-MINOR-1 | Backend `SecurityConfig` / `ProblemJsonSecurityHandler` / `GlobalExceptionHandler`. Without this, the native dialog can still appear. `credentials: 'omit'` now keeps its cached credential away from the SPA's requests |
| Shared bounded sanitiser for echoed keys (length and character class) across Application, Underwriting, Issuance and the endorsement parser | SEC-MINOR-2 | This loop only matched the 100-character length bound. Characters are still unrestricted, but React escapes them |
| Raw-path `instance` on the issue, endorse and preview routes | SEC-MINOR-3 | Use a fixed instance per problem type, or the route template |
| `frontend/coverage/lcov.info` is tracked and churns | TEST-MINOR-4 | Untrack or ignore it, and update `frontend/CLAUDE.md` |
| Snapshot baselines exist only as `-win32.png` | TEST-MINOR-3 | Generate `-linux` baselines when the Sprint 5 CI lands |
| ADMIN reads of policy detail (address, nominee names) are not audited | Security notes | Consider read-audit with the admin dashboard |
| Exhausting the policy-number sequence (999,999 per product and year) gives a generic 500 | Security notes | Availability issue. Map it to a named problem |

## Lesson
A credential the app does not hold can still be sent by the browser. Making `credentials: 'omit'` the default in the
single fetch helper closes this for every current and future API call.
