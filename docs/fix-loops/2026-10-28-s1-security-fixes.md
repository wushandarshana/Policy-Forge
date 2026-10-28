# Fix loop: Sprint 1 security findings (CSRF via Basic auth, deny-list leak)

- **Detected by:** the `security-reviewer` agent's post-sprint review of `feat/catalog-quote`, verdict
  CHANGES_REQUESTED ([`specs/reviews/security-feat-catalog-quote-20261028.md`](../../specs/reviews/security-feat-catalog-quote-20261028.md)).
  The harness evaluator had passed the sprint (all contracted ACs green), but the contract didn't cover these
  failure modes.
- **Findings:**

  | ID | Severity | Issue |
  |---|---|---|
  | F-1 | MAJOR | Cross-site `text/plain` POST could publish a rule set using browser-cached Basic credentials |
  | F-2 | MAJOR | The public quote read used a deny-list, so any submitted key was stored and echoed back |
  | F-3 | MINOR | Unbounded anonymous body size |
  | F-4 | MINOR | 404 `detail` repeated the request input |
  | F-5 | MINOR | Synthetic Basic auth headers in the archived logs |
  | F-6 | MINOR | Public wildcard rule under `/api/products/**` |

## 1. Spec first (spec is truth)
- `specs/app_spec.md`: NFR-04.2 now requires an allow-list. New **NFR-04.3** returns 415 for non-JSON state-changing
  requests, which is the CSRF defence.
- `specs/quote-engine_spec.md`: new **AC-01.9**. Unknown `riskInputs` keys get 400 naming each key; a body over 16 KB
  gets 413. Nothing is stored in either case.
- Rules encoded so later sprints inherit them: `web/CLAUDE.md` (JSON-only `consumes`, allow-lists, explicit public
  matchers, fixed problem detail) and knowledge deposits KD-007, KD-008 and KD-009.

## 2. Reproduce (red)
The `test-engineer` agent wrote failing tests only, with no production changes. Log: [`red.log`](2026-10-28-s1-security-fixes/red.log).

| Test | Before the fix |
|---|---|
| NFR-04.3.1: publish as `text/plain` → 415 | **201**, and v2 was created |
| AC-01.9: unknown keys `BMI`, `aadhaar` → 400 | **201** |
| AC-01.9: body over 16 KB → 413 | **201** |
| F-4: 404 detail doesn't echo `ZZQINJECT` | detail was "No product ZZQINJECT" |
| F-6: anonymous `/api/products/MOTOR/internal` → 401 | **404** (publicly reachable) |
| NFR-04.3.2: quote as `text/plain` → 415 | already 415. Kept as a regression guard. |
| NFR-04.2: exact allow-list on the public read | passed. **Weak test**, see below. |

**Weak-test catch.** The first NFR-04.2 test stored only declared keys, so a deny-list and an allow-list produce the
same output and the test proved nothing. The orchestrator sent it back. The green agent first strengthened it to store
a quote containing `BMI`, `aadhaar` and `medicalHistory` directly through the repository. It then confirmed the old
code returned those keys (a real red, appended to `red.log`) before implementing.

## 3. Fix (green)
A `general-purpose` agent made these changes. No existing test was weakened and the JaCoCo thresholds are unchanged.

- **F-1:** `consumes = application/json` on the publish and quote POST endpoints. CORS is left unconfigured, so
  cross-origin JSON fails preflight.
- **F-2:**
  - A new domain `RiskInputSchema` is the single source of each rating model's accepted inputs.
  - Undeclared keys get 400, with one `errors[]` entry per key.
  - The public GET builds `riskInputs` from the declared inputs minus health inputs. The old deny-list was removed.
- **F-3:** a new `QuoteBodySizeLimitFilter` returns 413 above 16 KB, whether or not `Content-Length` is present
  (chunked bodies are capped while reading).
- **F-4:**
  - Fixed `detail` text for each error type, including Spring's own 415, 405 and unreadable-body errors.
  - 404 `instance` is set to `urn:policyforge:problem:not-found`, because Spring would otherwise fill it with the
    request path.
- **F-6:** the anonymous rules are now exactly `GET /api/products` and `GET /api/products/*/versions`.
- **F-5:** fixed in the archive by the orchestrator (10 synthetic `Authorization` values redacted).
- **Test-review m1:** health timing test warms the endpoint first.

## 4. Validate
- `./mvnw verify` (green agent): **BUILD SUCCESS**, 99 surefire + 63 failsafe tests, 0 failures. JaCoCo domain ≥ 80%
  and overall ≥ 70% met. Log: [`green.log`](2026-10-28-s1-security-fixes/green.log).
- `npm test` (orchestrator, independent): 9/9 passed in 3 files, 96.46% line coverage.
- `npx playwright test` (orchestrator, independent): 2/2 passed (desktop and mobile Chromium) against the fixed
  backend. This confirms the Quote page only sends declared keys.
- `premium_check.py spec-examples`: all PASS. `test_hooks.py`: ALL PASSED.

## 5. Close
- Re-review: sent to `security-reviewer`. Verdict recorded in the review file's resolution log.
- PR: none. By user decision, nothing is committed during this phase, and the user commits and pushes when the work is
  complete.
- Knowledge deposits: KD-007, KD-008, KD-009.

## Lesson
A harness PASS means "meets the contract". The security failure modes weren't in the contract, so the separate
adversarial review caught what the evaluator was never asked to check. The fix put them into the spec (NFR-04.3,
AC-01.9) and into `web/CLAUDE.md`, so the next sprint's planner and evaluator will check them.

---

# Round 2: re-review findings R-1 to R-3

The security re-review (section "Re-review" in the review file) confirmed that F-1, F-2, F-4 and F-6 were fixed. It
found that the F-3 fix could be bypassed:

- **R-1 (MAJOR).** `QuoteBodySizeLimitFilter` compared the raw `getRequestURI()`, while Spring routes on the decoded
  path. A live `POST /api/%71uotes` with a 5 MB body was stored.
- **R-2 (minor).** `healthDeclarations` accepted any list contents.
- **R-3 (minor).** One synthetic Basic header appeared in the new `red.log`.

## Spec first
- **NFR-04.4.** A default-deny body limit on every path: 16 KB, decided on the decoded path. The 64 KB allowance for
  publish was later reworded to match the implementation (it applies when an `Authorization` header is present and is
  enforced before authentication).
- **AC-01.10.** `healthDeclarations` must be at most 10 codes matching `^[A-Z][A-Z0-9_]{0,63}$`.
- **AC-01.11.** The Quote page normalizes typed declarations to codes.
- **KD-010:** match on the routed path and default-deny. **KD-011:** redaction must be a tool. Both are encoded in
  `web/CLAUDE.md`, `scripts/redact_evidence.py` and `/sprint-archive`.

## R-3: redaction tooling
- Added `scripts/redact_evidence.py` with a `--check` gate.
- The first version also rewrote prose such as "Basic credentials". It was tightened to redact only base64 that decodes
  to `user:password`, then verified against a mix of real secrets and prose before use.
- The repo check is now clean.

## Red: [`red-round2.log`](2026-10-28-s1-security-fixes/red-round2.log) (test-engineer)
The tests run through a **real embedded server** (`BodySizeLimitEndToEndIT`), because MockMvc doesn't decode URLs the
way Tomcat does.

| Test | Before |
|---|---|
| NFR-04.4.1: `/api/%71uotes` and `/api/quote%73`, 20 KB | 201 |
| NFR-04.4.2: chunked upload to the encoded path | 201 |
| NFR-04.4.3: publish over 64 KB | 400 at first, from the wrong product. After the orchestrator sent it back, it was corrected to MOTOR and recorded as 201, the right reason. |
| NFR-04.4.4: anonymous oversized POST to an unknown path | 401 (no default limit) |
| AC-01.10: 5 illegal `healthDeclarations` shapes | 201 |

## Green: [`green-round2.log`](2026-10-28-s1-security-fixes/green-round2.log) (general-purpose agent)
- **R-1.** `RequestBodySizeLimitFilter` (replaces the quote-only filter):
  - It is ordered before Spring Security and parses the path with `ServletRequestPathUtils`.
  - It matches routes with a `PathPattern`.
  - It caps both a declared `Content-Length` and chunked bodies.
  - The 413 response uses a fixed `instance` (`urn:policyforge:problem:payload-too-large`).
- **R-2.** A domain `InputReader.codeList(...)` rule applies to TERM_LIFE `healthDeclarations`.
- **Test changes:**
  - The QuoteApiIT size test was re-padded with whitespace.
  - The fixture `HEALTH_TEXT` was changed from hyphens to underscores so it conforms to AC-01.10. Its no-leak meaning
    is unchanged.
- **Result.** `./mvnw verify` passed: 106 unit + 75 integration tests, 0 failures, JaCoCo thresholds unchanged.

## UI follow-up for AC-01.11: [`ui-red.log`](2026-10-28-s1-security-fixes/ui-red.log), [`ui-green.log`](2026-10-28-s1-security-fixes/ui-green.log) (ui-designer)
- AC-01.10 would have rejected normal typing such as `diabetes`, so the browser now normalizes entries to codes. The
  hint states the format.
- **Partial red evidence.** The red run failed on the missing hint text before reaching the normalization assertion.
  The old code sent the raw lower-case text, so the normalization assertion would also have failed, but the log
  doesn't show it.

## Independent validation (orchestrator)
- `npm test`: 11/11.
- Playwright: 2/2 on desktop and mobile against the packaged app.
- Port 8080 free. `redact_evidence.py --check`: 0. HEAD unchanged (no commits).

## Known residual (accepted, recorded in NFR-04.4)
The 64 KB publish allowance is decided before authentication, from whether an `Authorization` header is present. A
request with invalid credentials can therefore send up to 64 KB before getting 401, and nothing is stored.
