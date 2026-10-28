# Test review: feat/catalog-quote (Sprint 1), 2026-10-28

Reviewer: test-engineer (Mode 2). Scope: working tree (nothing committed). Prod/test code not modified.

## Observed results
- `./mvnw verify` (java 21, frontend built): BUILD SUCCESS. Surefire 91 pass, Failsafe 56 pass, 0 failed/skipped. JaCoCo overall and domain checks met.
- `scripts/ac_audit.py --offline --no-write`: 20/67 ACs covered, all 20 are sprint-scope; the 47 NOT COVERED are Sprint 2+ ACs (AC-03..AC-11, NFR-03.1), none in scope. The one PARENT-ONLY item is outside this sprint's list.
- Vitest 9 tests (4 files). Playwright: 1 test x 2 projects (desktop, mobile chromium) = the "2" reported.
- Port 8080 free at end.

## AC trace (sprint scope)
All pass the "proves the Then clause with the spec literal" check:
- AC-01.1/.2/.3: PremiumCalculatorTest (6850.00, 15150.00, 11350.00 with isEqualByComparingTo + scale()==2 via assertMoney, PremiumCalculatorTest:25) and QuoteApiIT:129-182 (API, DB row, ruleVersion 1, validUntil 2026-11-27 from a fixed clock). Playwright quote.spec.ts:4 covers AC-01.1 in the UI.
- AC-01.4 (100 quotes, HashSet of BigDecimal, scale-sensitive), AC-01.5 (0.80 / 1.20 plus every band edge), AC-01.6 (18150.00 domain + QuoteRuleVersionIT:72 with ruleVersion 2), AC-01.7 (validator, domain and API with field names, nothing stored), AC-01.8 (recompute from breakdown, all three products, plus Vitest).
- AC-02.1/.2 (ProductCatalogReadIT, SeedRuleFilesTest, RuleSetSeedLoaderIT), AC-02.3 (byte-for-byte v1 plus audit row), AC-02.4 (resolver unit plus clock-driven IT), AC-02.5 (400 listing each violation, nothing stored), AC-02.6 (ArchUnit, as the spec itself defines), AC-02.7 (403 and nothing stored).
- NFR-04.2 (no bmi/healthDeclarations/value/text in raw body; Q-UUIDv4; 404s), NFR-06.1/.2 (CorrelationIdIT), NFR-07.1 (HealthEndpointIT), NFR-08 (ArchUnit set).

## Quality checks
- Money: domain tests use isEqualByComparingTo + scale()==2. API/DB checks compare decimal strings ("6850.00") or `toString()` of the H2 column; this is stricter than the numeric form (it pins scale), so acceptable. No double/float in tests apart from the intentional negative fixtures.
- Time: all date logic uses MutableClock/fixed clock (QuoteApiIT:51, RuleSetPublishIT:53, QuoteRuleVersionIT:41). No Thread.sleep, waitForTimeout, @Disabled, `.skip` found (grep). No assertion-free tests seen.
- ArchUnit: every rule has a negative-fixture test using `assertRuleRejects` against `com.horizon.archfixtures.*` (clean fixture also asserted to pass for append-only and money). ArchSupport:34 production scan uses DoNotIncludeTests and fixtures live outside `com.horizon.policyforge`, so they are excluded from the production scan and from Spring/JPA scanning.
- JaCoCo: thresholds 0.80 domain / 0.70 overall (pom.xml:26-27, rules at ~232/256); no `<exclude>` entries. pom is untracked, so there is no main baseline to diff, only the current values can be confirmed.
- Isolation: embedded H2 gets a unique name per Spring context (application.yml:4), so DirtiesContext ITs do not share state. QuoteApiIT shares one context and uses before/after counts, not absolutes.

## Findings
No BLOCKERs.

MAJOR
- M1 (process, not test quality): the sprint is entirely uncommitted, so there is no `test(red)` / `feat(green)` history and `git diff main...HEAD` is empty. CLAUDE.md workflow item 2 and the PR gate need commits before `/pr`. Commit on feat/catalog-quote (separate red/green/refactor where feasible).

MINOR
- m1 HealthEndpointIT.java:34-40: the 1000 ms bound is measured on the first HTTP request through a fresh java.net client on a loaded runner; this is the likeliest flake in the suite. Warm the endpoint (one untimed request) or keep the bound but retry the measurement once.
- m2 AC-01.4 determinism is proven only in the domain (PremiumCalculatorTest:74); there is no service/API repeat. Acceptable per spec wording, noted for completeness.
- m3 RuleSetPublishIT (6 methods) and QuoteRuleVersionIT each rebuild a Spring context via DirtiesContext, about 5 s for the class. Not a correctness issue; consider a shared context with transaction cleanup if the IT time grows.
- m4 ErrorMappingIT:134 deliberately triggers a 500 and the full stack trace (with "secret-internal-detail") prints to the build log; harmless noise, consider muting that logger in the test.
- m5 NFR-06.1 test asserts "every JSON line on the current thread"; it is sound for MockMvc but does not exercise a real servlet thread. Acceptable.
- m6 Playwright covers only AC-01.1; AC-01.2/01.3/01.7/01.8 and the catalog UI have Vitest/IT coverage only. Within Sprint 1 scope per the spec, no AC lacks a test.

VERDICT: APPROVE
