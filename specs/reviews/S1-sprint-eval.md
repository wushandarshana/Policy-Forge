> Archived from `.sprint/20261028-052716/` on 2026-10-28 · branch `feat/catalog-quote` · harness agent-harness 2.8.0 (plugin `agent-harness@agent-harness`). Content below is byte-identical to the harness artifact except where noted.

# Sprint Evaluation — Iteration 2

Evaluator runs (2026-10-28, iteration 2, after the TASK-002 / TASK-006 / TASK-007 retries):
`./mvnw -B -ntp clean verify` (exit 0, BUILD SUCCESS; surefire 91 and failsafe 56 tests, 0 failures, 0 errors, 0 skipped; "All coverage checks have been met" twice);
`npm test` run 3 times on Node 24.20.0 (9 passed each time, Lines 96.46% (382/396) each time, `lcov.info` rewritten each time with identical per-file LF 31/4/4/11/0/50/71/137/88);
`npm run build` (exit 0);
`npx playwright test` (2 passed: desktop-chromium and mobile-chromium, backend started by the Maven-wrapper webServer and stopped afterwards; port 8080 free after);
`premium_check.py spec-examples` (AC-01.1 6850.00, AC-01.2 15150.00, AC-01.3 11350.00, AC-01.6 18150.00, all PASS);
`ac_audit.py --offline --no-write` (20 of 67 covered; none of the 19 sprint IDs is NOT COVERED or PARENT-ONLY);
a per-ID grep of every TASK-001..006 red and green log; a cross-check of the new TASK-006 red/green headers against the raw Maven output and the test sources' method-to-@DisplayName mapping;
and `git log -1` / `git diff --cached`.
A `find -newer` check shows no file under `frontend/src`, `frontend/e2e`, `backend/src`, `frontend/playwright.config.ts` or `backend/pom.xml` changed since the iteration-1 evaluation. The iteration-1 live results (curl checks and the Playwright MCP walkthrough at 375px) therefore still hold, and the Playwright e2e run above re-exercised the packaged UI in a real browser this iteration. Playwright MCP was not re-run this iteration, because the UI source is unchanged.

## Results

| Task | Criterion | Status | Reason |
|------|-----------|--------|--------|
| TASK-001 | Builds with Maven wrapper on Java 21 / Spring Boot 3.x, base package and layer packages | PASS | Wrapper present; evaluator clean verify BUILD SUCCESS; layer packages exist |
| TASK-001 | Declares every sprint dependency up front | PASS | Unchanged since iteration 1 (pom has no change since then); the only later pom change is SPA packaging (TASK-007) |
| TASK-001 | Enforces JaCoCo 80% domain / 70% overall in verify | PASS | "All coverage checks have been met" twice in evaluator verify |
| TASK-001 | Flyway on startup, validate-on-migrate, Hibernate validate only | PASS | Unchanged config; verify ITs boot against V1 to V3 |
| TASK-001 | One injectable Clock, no clock-less now() | PASS | No backend source change since the iteration-1 grep; ClockConfigTest passes |
| TASK-001 | NFR-07.1 health 200 `{"status":"UP"}` < 1000 ms | PASS | The "NFR-07.1:" test passes in verify; the iteration-1 live check gave 0.009 s; the e2e webServer health wait succeeded |
| TASK-001 | NFR-06.1 generated X-Correlation-Id and correlationId in JSON logs | PASS | Test passes; the e2e run's server log shows a JSON `correlationId` on every request line |
| TASK-001 | NFR-06.2 echoes `abc-123` | PASS | The "NFR-06.2:" test passes in verify |
| TASK-001 | HTTP Basic, four synthetic users, public paths, other /api authenticated | PASS | SecurityBaselineIT passes; anonymous POST /api/quotes gave 201 in the e2e run |
| TASK-001 | Central RFC 7807 errors, 400 with `errors[]` of {field,message} | PASS | AC-01.7 / AC-02.5 ITs pass; iteration-1 live check unchanged |
| TASK-001 | Red/green logs for NFR-07.1, 06.1, 06.2 | PASS | All three IDs are in both TASK-001-red.log and TASK-001-green.log |
| TASK-001 | Passes `./mvnw verify` | PASS | Evaluator clean verify exit 0 |
| TASK-002 | Scaffold: Vitest single-run, build, v8 coverage writing lcov to `frontend/coverage/lcov.info`, /api proxy to 8080 | PASS | Fixed. `vite.config.ts` now uses `pool: 'forks'` with `singleFork`. In 3 evaluator `npm test` runs, each printed Lines 96.46% (382/396) and rewrote `lcov.info` (mtimes 15:10:58, 15:11:05, 15:11:12) with identical real per-file LF 31/4/4/11/0/50/71/137/88. FN/BRDA entries are spread across real lines, not collapsed to line 1. The proxy and scripts are unchanged and correct. |
| TASK-002 | Typed API client matching C1 to C3 | PASS | No change to `src/` since iteration 1, when the types were verified against live payloads |
| TASK-002 | Product selector from API, 6/6/7 inputs, selects/checkboxes | PASS | `src/` unchanged since the iteration-1 Playwright MCP verification; the e2e run used `product-select` from the live API |
| TASK-002 | Submits and renders premium, sumInsured, ruleVersion, validUntil, quoteId, breakdown, Apply with quote ID | PASS | The e2e run asserts the premium 6850.00, rule version 1, the quote ID shape, the Apply link and the policyFee row on both projects |
| TASK-002 | Money verbatim, no Number()/parseFloat/unary plus | PASS | `src/` unchanged; the NFR-01 source-scan Vitest passes |
| TASK-002 | Shows errors[] next to matching input | PASS | The "AC-01.7:" Vitest passes; the iteration-1 MCP check is still valid (no `src/` change) |
| TASK-002 | data-testid on inputs, submit, premium, rule version, breakdown rows, Apply | PASS | `src/` unchanged; the e2e test uses these testids successfully |
| TASK-002 | Grid/flex, single 640px breakpoint, no horizontal scroll at 375px | PASS | `src/` unchanged since the iteration-1 375px MCP measurement (0 overflowing elements); the mobile-chromium e2e run passes |
| TASK-002 | Vitest AC-01.1, AC-01.7, AC-01.8 pass | PASS | All three titled tests pass in each of the 3 evaluator runs (9/9) |
| TASK-002 | `npm run build` no TS errors | PASS | Evaluator build exit 0 |
| TASK-002 | Red/green logs | PASS | The red log names AC-01.1, AC-01.7 and AC-01.8 failing; the green log lists all three passing and now records the iteration-2 lcov stabilisation |
| TASK-003 | Seed v1 rule files at spec location with spec values | PASS | Unchanged since iteration 1; oracle and verify agree |
| TASK-003 | Passes policy-version-validator checklist | PASS | Rule files unchanged since the iteration-1 scripted check |
| TASK-003 | Oracle yields 6850.00 / 15150.00 / 11350.00 from repo rule files | PASS | Evaluator spec-examples all PASS |
| TASK-003 | Immutable domain rule-set types with BigDecimal, JSON parsing outside domain | PASS | DomainPurityArchTest passes in verify |
| TASK-003 | RuleSetValidator reports every violation at once | PASS | RuleSetValidatorTest and the AC-02.5 tests pass |
| TASK-003 | Active version by Clock, 404 when none (AC-02.4) | PASS | AC-02.4 tests pass in verify |
| TASK-003 | Seed load only where absent, unchanged on restart | PASS | RuleSetSeedLoaderIT passes |
| TASK-003 | AC-02.1 GET /api/products three products, activeVersion 1, distinct ratingModel | PASS | AC-02.1 tests pass; the e2e run gets 3 products from the live API |
| TASK-003 | AC-02.2 rule sets differ | PASS | AC-02.2 tests pass |
| TASK-003 | AC-02.3 PM publish v2, v1 unchanged, audit with actor | PASS | AC-02.3 tests pass |
| TASK-003 | Creates product on v1 publish of unknown code | PASS | RuleSetPublishIT passes |
| TASK-003 | AC-02.5 invalid publish 400, nothing stored | PASS | AC-02.5 tests pass |
| TASK-003 | AC-02.7 CUSTOMER publish 403 | PASS | The AC-02.7 test passes |
| TASK-003 | Flyway migrations NUMERIC, no setters, no delete/update repo methods | PASS | Migrations unchanged; AppendOnlyArchTest passes (15 tests) |
| TASK-003 | AC-02.6 AppendOnlyArchTest red before entity exists | PASS | TASK-003-red.log shows the AC-02.6 failure |
| TASK-003 | No v2+ seed file | PASS | Unchanged |
| TASK-003 | Red/green logs AC-02.1 to 02.7 | PASS | Per-ID grep: all seven IDs are in both TASK-003 logs |
| TASK-003 | Passes `./mvnw verify` | PASS | Evaluator verify exit 0 |
| TASK-004 | Five ArchUnit classes encoding §2, production import only | PASS | All five run and pass in verify |
| TASK-004 | Every rule has because(…) naming an NFR | PASS | Unchanged since the iteration-1 check |
| TASK-004 | Negative fixture per rule, fixtures outside production import | PASS | Unchanged; red log shows rule-vs-fixture failures |
| TASK-004 | Append-only covers 7 entities + QuoteEntity | PASS | Unchanged; passes with QuoteEntity present |
| TASK-004 | Retains AC-02.6 check | PASS | AC-02.6 appears in both TASK-004 logs and passes in verify |
| TASK-004 | Tags NFR-08 (+NFR-01/02) | PASS | Unchanged since the iteration-1 check |
| TASK-004 | Passes against production without weakening | PASS | All arch tests pass in evaluator verify |
| TASK-004 | Red/green logs | PASS | Unchanged and valid |
| TASK-004 | Passes `./mvnw verify` | PASS | Evaluator verify exit 0 |
| TASK-005 | PremiumCalculator facade selects model by ratingModel | PASS | PremiumCalculatorTest 12/0 in verify |
| TASK-005 | No factor literals in rating code | PASS | No backend source change since iteration 1; AC-01.6 prices v2 with no code change |
| TASK-005 | BigDecimal only, scale 10 intermediate, one scale-2 rounding | PASS | MoneyPrecisionArchTest passes; oracle agrees |
| TASK-005 | Inclusive bands, out-of-band is violation | PASS | AC-01.5 tests pass |
| TASK-005 | RiskInputValidator one violation per field (AC-01.7) | PASS | AC-01.7 tests pass |
| TASK-005 | AC-01.1/01.2/01.3 exact premiums, scale 2 | PASS | Tests pass; oracle agrees |
| TASK-005 | AC-01.4 determinism | PASS | The test passes |
| TASK-005 | AC-01.5 age 30/31 base rates | PASS | Tests pass |
| TASK-005 | AC-01.6 baseRate 0.030 gives 18150.00 | PASS | The test passes; oracle 18150.00 |
| TASK-005 | AC-01.8 breakdown complete and recomputable | PASS | Tests pass |
| TASK-005 | Matches oracle for 01.1/01.2/01.3/01.6 | PASS | Evaluator spec-examples all PASS |
| TASK-005 | java.* + domain only, purity/precision arch tests pass | PASS | Both pass in verify |
| TASK-005 | Red/green logs AC-01.1 to 01.8 | PASS | The red log names all eight IDs. The green log header reads "AC-01.1 to AC-01.8 domain tests PASSING" and gives a per-ID list (".4", ".5 x2" and so on). |
| TASK-005 | verify with domain coverage ≥ 80% | PASS | check-domain met in evaluator verify |
| TASK-006 | 201 anonymous POST /api/quotes in C2 shape | PASS | The e2e run shows anonymous POST /api/quotes giving 201; QuoteApiIT passes |
| TASK-006 | validUntil = Clock today + quoteValidityDays | PASS | QuoteApiIT asserts 2026-11-27 with a fixed clock; iteration-1 live check unchanged |
| TASK-006 | Immutable quote storage, V3 NUMERIC(19,2), no setters, no delete/update | PASS | Unchanged; AppendOnlyArchTest passes |
| TASK-006 | API AC-01.1 6850.00 and ruleVersion 1 | PASS | The test passes in verify; the e2e run shows 6850.00 |
| TASK-006 | AC-01.6 v2 published in setup gives 18150.00, ruleVersion 2 | PASS | QuoteRuleVersionIT passes in verify |
| TASK-006 | AC-01.7 400 naming each field, nothing stored | PASS | Three AC-01.7 QuoteApiIT tests pass |
| TASK-006 | NFR-04.2 GET omits health inputs | PASS | Three NFR-04.2 tests pass; iteration-1 live check unchanged |
| TASK-006 | UUIDv4 quote IDs | PASS | The e2e server log shows IDs such as Q-465a1c71-dab1-4913-aed6-…, matching the v4 shape; the NFR-04.2 regex test passes |
| TASK-006 | 404 problem+json for unknown quote | PASS | The NFR-04.2 unknown-ID test passes |
| TASK-006 | No logging of risk inputs or bodies | PASS | The e2e server log line is "Quote created quoteId=… productCode=TERM_LIFE ruleVersion=1" only; the NFR-03 log-capture test passes |
| TASK-006 | Controller free of business logic and repositories; all ArchUnit pass | PASS | Unchanged; arch tests pass |
| TASK-006 | Red log fails and green log passes for AC-01.1, 01.6, 01.7, NFR-04.2 | PASS | Fixed. TASK-006-green.log now opens with a per-test section naming AC-01.1 (1 PASS), AC-01.6 (1 PASS), AC-01.7 (3 PASS) and NFR-04.2 (3 PASS) by full display name, followed by the raw verify output (QuoteApiIT 10/0, QuoteRuleVersionIT 1/0). The new TASK-006-red.log header maps each failing method to its ID. The evaluator cross-checked it against the raw `<<< ERROR!/FAILURE!` lines in the same log and the test sources' method-to-@DisplayName mapping, and all match. Each required ID has at least one failing test before the implementation existed. The NFR-04.2 unknown-ID 404 test passed pre-implementation, as disclosed, but the other two NFR-04.2 tests failed. |
| TASK-006 | Passes `./mvnw verify` | PASS | Evaluator verify exit 0 |
| TASK-007 | `spring-boot:run` serves SPA at / and /api from one process | PASS | The e2e webServer (Maven wrapper) served `/`, `/assets/*.js` and `/api/products` from one 8080 process, all 200 |
| TASK-007 | Playwright desktop-chromium + mobile-chromium (iPhone 13), webServer via wrapper on 8080 | PASS | Unchanged config; both projects ran |
| TASK-007 | e2e "AC-01.1: term life quote shows 6850.00", testids, web-first, no waitForTimeout, passes both | PASS | Evaluator `npx playwright test`: 2 passed (desktop-chromium and mobile-chromium) |
| TASK-007 | verify with unchanged JaCoCo thresholds | PASS | pom unchanged since iteration 1 (thresholds 0.80/0.70); verify exit 0 with coverage checks met |
| TASK-007 | npm test and build pass, lcov refreshed | PASS | 9 passed in 3 runs, build OK, and `lcov.info` rewritten with correct content each run |
| TASK-007 | AC audit COVERED for listed IDs | PASS | Offline audit 20/67; none of the 19 IDs is in the NOT COVERED list (only out-of-scope IDs and NFR-03.1 are) |
| TASK-007 | Confirms from TASK-001..006 red and green logs that every ID failed before it passed; no commits | PASS | Fixed. The new TASK-007-id-evidence.log lists, for all 19 IDs, the XML pass counts and the red and green logs that name each ID, with 0 gaps. The evaluator's independent grep of the TASK-001..006 logs agrees: every ID is in at least one red log and one green log. NFR-04.2 is now in TASK-006-green.log. AC-01.4 and AC-01.5 appear in green logs only through the TASK-005 green header ("AC-01.1 to AC-01.8 … PASSING", with ".4", ".5 x2"). No commits: HEAD is still 50a5f62 and `git diff --cached` is empty. |
| TASK-007 | Commands and results recorded | PASS | Listed in TASK-007.md, including the iteration-2 re-runs |
| TASK-007 | Red/green e2e logs | PASS | Unchanged: the red log shows both projects failing, and the green log shows 2 passed |

## Retry Tasks
(none)

## Overall
PASS
