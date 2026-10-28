> Archived from `.sprint/20261028-052716/` on 2026-10-28 · branch `feat/catalog-quote` · harness agent-harness 2.8.0 (plugin `agent-harness@agent-harness`). Content below is byte-identical to the harness artifact except where noted.

# Sprint Plan

## Spec
specs/product-catalog_spec.md specs/quote-engine_spec.md — PolicyForge Sprint 1 (branch feat/catalog-quote). Scope: Spring Boot 3 / Java 21 backend scaffold with Maven wrapper (backend/), Flyway + H2, health endpoint (NFR-07.1), JSON logs with correlation id (NFR-06.1/06.2), product catalog with versioned JSON rule files for TERM_LIFE/MOTOR/HOUSEHOLD (AC-02.1–02.7), deterministic quote engine (AC-01.1–01.8, NFR-01), public quote read without health inputs (NFR-04.2), ArchUnit architecture tests (NFR-08), JaCoCo, seed rule files; minimal React+Vite frontend Quote page + Playwright smoke. Constraints: follow root CLAUDE.md non-negotiables and folder CLAUDE.md files; strict TDD with separate `test(red)` / `feat(green)` / `refactor` commits per AC; every test named AC-NN.M; worked examples must equal premium_check.py oracle. Environment: `java` is not on PATH in this session — prefix Maven commands with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`. Use project agents where they fit (test-engineer for red, quote-engine-agent for rating, archtest-author-agent for ArchUnit, ui-designer for the page; evaluator uses Playwright MCP).

### Interpretation (read this at the plan checkpoint)

**Primary deliverable.** The first working slice of PolicyForge on `feat/catalog-quote`: a greenfield Spring Boot
backend (the `backend/` and `frontend/` folders today hold only `CLAUDE.md` files) that loads three immutable v1 product
rule sets, serves a public catalog, prices TERM_LIFE / MOTOR / HOUSEHOLD quotes deterministically with fixed-point money,
stores quotes immutably and returns them publicly without health data. A minimal React Quote page sits on top, with one
Playwright smoke test.

**Who depends on it.** Every later sprint. Underwriting (AC-03/04) reads the stored quote and the rule set's
underwriting rules, issuance reads the lifecycle block, and renewal re-quotes through the same calculator. Getting the
rule-file shape, the money arithmetic and the append-only persistence right here is the point of the sprint.

**Done looks like.** `./mvnw verify` is green, with ArchUnit and the unchanged JaCoCo thresholds (80% domain, 70%
overall). `npm test` and the Playwright smoke (desktop and mobile) are green. The offline AC audit shows AC-01.1–01.8,
AC-02.1–02.7, NFR-04.2, NFR-06.1, NFR-06.2 and NFR-07.1 as COVERED (not PARENT-ONLY). The oracle agrees with Java on
6850.00 / 15150.00 / 11350.00 / 18150.00. Each task's red/green logs in `sprint-progress/` show every one of those IDs
failing before it passes.

**Checkpoint decision (user, 2026-10-28): no git commits this sprint.** Generators and the orchestrator make no commits
and no pushes. All changes stay in the working tree, and the user commits and pushes everything after the work is done.
TDD order still applies. The evidence is now log files instead of commits: before writing implementation code for an
ID, the generator runs the new tests and saves the failing output to `sprint-progress/<TASK-ID>-red.log` in the sprint
workspace. After implementing, it saves the passing output to `sprint-progress/<TASK-ID>-green.log`. Both logs name
each AC/NFR ID they cover. This decision replaces J2 below and every git-history criterion in the original plan.

**Judgment calls. Veto any of these here, because each one is cheap to change now and expensive later.**

- **J1. Backend tasks are serialized.** All backend work shares one working tree, one build file and one Flyway version
  sequence, so concurrent generators would break each other's builds mid-edit. Only the frontend Quote page (TASK-002)
  runs in parallel with the backend scaffold (TASK-001), because they touch disjoint top-level folders. TASK-005 depends
  on TASK-004 (ArchUnit) by choice, not by data, so the quote engine is built under the architecture guardrails.
- **J2. TDD evidence (superseded by the checkpoint decision above).** There are no commits. Every AC-NN.M / NFR-NN.M a
  task covers must appear as a failing test in that task's red log before the implementation is written, and as a
  passing test in its green log. One red run may group sub-criteria of the same AC. The refactor step still happens
  (behaviour-preserving cleanup once green); the green log is taken after it.
- **J3. Structural guards (NFR-08).** ArchUnit rules written over code that already complies cannot fail first. Their
  red evidence is a negative fixture that the rule rejects, recorded in the TASK-004 red log. AC-02.6 is different: it
  gets a true red in TASK-003 (a check that fails while the rule-set entity is absent), recorded in the TASK-003 red
  log before the entity exists. TASK-004 then folds it into the full `AppendOnlyArchTest`, as the spec names that
  class.
- **J4. Rule-file `rating` schema.** The JSON factor names are the spec's factor names (`baseRatePerThousand`,
  `smokerFactor`, `termFactor`, `baseRate`, `vehicleAgeFactor`, `driverAgeFactor`, `claimLoading`, `ncbDiscount`,
  `useFactor`, `buildingRate`, `contentsRate`, `constructionFactor`, `floodFactor`, `securityFactor`, `policyFee`).
  Bands are `[{min,max,factor}]` lists, inclusive at both ends. Lookup maps are keyed by strings (`"true"`/`"false"`,
  `"10"`/`"20"`/`"30"`, enum names). Every number is a decimal string. This is the shape the `premium_check.py` oracle
  and the `policy-version-validator` skill already read, so the oracle can check the rule files in the repository directly.
- **J5. Lifecycle values not in the catalog table.** The launch table fixes grace, cooling-off and cancellation fee per
  product. For `termMonths`, `renewalOfferDaysBeforeExpiry` and `quoteValidityDays`, all three products use the spec
  example's values: 12, 30 and 30. `effectiveFrom` is `2026-01-01` for all v1 files.
- **J6. No v2 seed files.** MOTOR v2 for AC-01.6 / AC-02.3 / AC-02.4 exists only as a test fixture, or is published
  through the API inside a test. Shipping `rules/motor/v2.json` would break AC-02.1 ("active version 1 on fresh start").
- **J7. Required risk inputs.** Every input the quote spec lists for a product is required, including inputs used only
  by underwriting (`bmi`, `healthDeclarations`, `propertyAgeYears`, `hasFloodDefences`). `healthDeclarations` must be
  present but may be empty. Enumerations (`termYears` ∈ {10,20,30}, `vehicleUse`, `constructionType`, `floodZone`) and
  `noClaimBonusYears` 0–5 are validated at quote time.
- **J8. Out-of-band inputs are 400 at quote time.** TERM_LIFE age outside 18–65 and MOTOR `vehicleAgeYears` > 15
  return 400 naming the field, as `quote-engine-agent` requires (no default factor). This conflicts in spirit with the
  quote spec's note "above 15 is rejected by underwriting": with this choice, UW-MO-002 can only fire in the domain
  evaluator, not through the quote→application flow. If you want underwriting to own that rejection instead, say so
  and the spec needs a deliberate clarification first (spec is truth).
- **J9. Sum-insured error field.** AC-01.7 errors name the product's sum-insured input: `sumInsured` (TERM_LIFE),
  `vehicleValue` (MOTOR), and the derived `sumInsured` (HOUSEHOLD, building + contents).
- **J10. Publish is audited.** The catalog spec's Behaviour section says so, but no AC pins it. TASK-003 adds a
  minimal append-only audit entry (actor ID, action, entity, timestamp) written on each successful publish, so this
  sprint doesn't contradict the spec. The broader audit API (`GET /api/admin/audit`) is deferred.
- **J11. QuoteEntity is append-only in ArchUnit.** `docs/architecture.md` lists seven append-only entities, and
  `repository/CLAUDE.md` plus the quote spec ("Quotes are stored and immutable") add `QuoteEntity`. I take the stricter,
  more specific rule and include it.
- **J12. Security baseline but no NFR-04.1 claim.** TASK-001 configures HTTP Basic with the four synthetic users from
  `app_spec.md` §2 and the public paths from NFR-04.1. NFR-04.1 is **not** claimed as covered this sprint, because its
  "CUSTOMER → underwriter endpoint 403" half needs endpoints that don't exist yet. AC-02.7 (403) is covered.
- **J13. Frontend input form.** The API exposes no per-product input schema, so the Quote page defines each product's
  form fields client-side from the quote spec's input lists, and loads the product list from `GET /api/products`. The
  "Apply" call to action is rendered with the quote ID, but the Apply journey (AC-03) is a later sprint. The Catalog
  Manager page from the catalog spec's UI section is **deferred** (out of this sprint's stated scope).
- **J14. Playwright smoke means one journey.** It runs `AC-01.1: term life quote shows 6850.00` against the packaged
  app on port 8080, on the desktop-chromium and mobile-chromium projects. It has no visual snapshots this sprint, to
  avoid cross-machine pixel flakiness.
- **J15. Packaging means `./mvnw verify` needs Node.** Following `docs/architecture.md` §6, the Maven build builds the
  SPA into the backend so one process serves `/` and `/api`. The consequence is that a backend-only build needs Node
  and npm. Node 24 and npm 11 are present on this machine.
- **J16. TASK-003 is the largest task.** It covers rule files, the domain model and validator, persistence, startup
  load and the API. If you prefer, split it into "rule files + domain" and "persistence + API", which makes 8 tasks.

**Cross-task contracts.** Tasks run cold and TASK-002 runs in parallel, so these interface shapes are fixed here:

- **C1. `GET /api/products`** returns a JSON array. Each item has `productCode`, `productName`, `ratingModel` and
  `activeVersion` (integer). `GET /api/products/{code}/versions` lists stored versions (public, per `app_spec.md` §8).
- **C2. Quotes** follow the request/response example in `specs/quote-engine_spec.md` exactly. Money values are decimal
  strings at scale 2. `breakdown` is an array of `{factor, value}` with decimal-string values. `quoteId` is
  `Q-<UUIDv4>`. `validUntil` is an ISO date.
- **C3. Validation errors** are `400 application/problem+json` with `errors[]` of `{field, message}`. Other errors are
  problem+json with 401, 403, 404 or 409 as appropriate.
- **C4. Flyway:** each task adds new migration versions above the highest one already present. An existing migration
  is never edited once another task has used it (root rule 6).

**Execution notes for every generator.**

- Before any Maven command, run `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`.
- **Do not run `git commit`, `git add` or `git push`** (checkpoint decision). Leave every change in the working tree.
  Write the red and green logs described above into `sprint-progress/` in the sprint workspace.
- Use synthetic data only, keep money in `BigDecimal` from strings, keep PII and health inputs out of logs, and tag
  tests as `@Tag("AC-NN")` + `@DisplayName("AC-NN.M: …")` (or `NFR-NN` / `NFR-NN.M`), or with Vitest/Playwright titles
  starting `AC-NN.M:`. The `ac-tag-check` hook rejects untagged test files.
- Agent and skill fit:

  | Task | Agents and skills |
  |---|---|
  | TASK-001 | `test-engineer` (red) |
  | TASK-002 | `ui-designer` |
  | TASK-003 | `test-engineer`, plus the `spec-to-test-generator` and `policy-version-validator` skills |
  | TASK-004 | `archtest-author-agent` / `archtest-author` skill |
  | TASK-005 | `quote-engine-agent`, plus the `premium-calc-evaluator` skill |
  | TASK-006 | `quote-engine-agent` and `test-engineer` |
  | TASK-007 | `ui-designer` and `test-engineer` |

  The Evaluator adds a Playwright MCP walkthrough of the Quote page.

**Out-of-orchestrator scope. The user must do these manually.**

- (Orchestrator correction) The harness plugin is installed, and routing comes from the user-level
  `~/.claude/agent-harness.json` (full-access preset: planner opus/xhigh, evaluator opus/high, code sonnet/high), not
  built-in defaults.
- Committing and pushing all sprint output is done by the user after the work is complete (checkpoint decision).
- Restarting the terminal or Claude Code makes the machine `JAVA_HOME` / `PATH` stick, so later sessions don't need the
  prefix (see `docs/post-mortems/2026-10-28-jdk-path-environment.md`).

## Tasks

### TASK-001: Backend scaffold and platform NFRs (health, correlation-ID JSON logging, security and error baseline)
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Builds with a Maven wrapper present in `backend/` on Java 21 and Spring Boot 3.x, with base package `com.horizon.policyforge` and the layer packages domain, service, repository, web and config
  - Declares up front every backend dependency this sprint needs (web, validation, JPA, Flyway, H2, actuator, security, JSON log encoder, ArchUnit, JaCoCo), so that later backend tasks only change the build to package the SPA (TASK-007)
  - Enforces JaCoCo line coverage of at least 80% for the domain package and 70% overall in the verify phase
  - Applies Flyway migrations on startup against H2 with validate-on-migrate enabled and Hibernate schema validation only (no auto-DDL)
  - Provides one injectable `java.time.Clock` for all date logic, with no `LocalDate.now()` calls that bypass it
  - Returns 200 with `{"status":"UP"}` from `/actuator/health` in under 1000 ms after startup, proven by a test displayed as "NFR-07.1: …"
  - Generates an `X-Correlation-Id` response header when the request has none, and puts the same `correlationId` field in every JSON log line for that request, proven by a log-capture test displayed as "NFR-06.1: …"
  - Echoes a supplied `X-Correlation-Id: abc-123` header unchanged, proven by a test displayed as "NFR-06.2: …"
  - Configures HTTP Basic with the four synthetic users from app_spec §2, allows anonymous `GET /api/products**`, `POST /api/quotes`, `GET /api/quotes/{id}`, `/actuator/health` and the SPA's static assets, and requires authentication for every other `/api/**` path
  - Maps errors centrally to RFC 7807 problem+json, with validation failures as 400 carrying `errors[]` of `{field, message}` (contract C3)
  - Saves a red log showing the NFR-07.1, NFR-06.1 and NFR-06.2 tests failing before their implementation exists, and a green log showing them passing, in `sprint-progress/TASK-001-red.log` and `TASK-001-green.log`
  - Passes `./mvnw verify`

### TASK-002: Frontend Quote page (React 18 + Vite + TypeScript) with Vitest
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Scaffolds the frontend app with npm scripts for unit tests (Vitest, single-run) and production build, Vitest v8 coverage that writes an lcov report to `frontend/coverage/lcov.info`, and a dev-server proxy from `/api` to port 8080
  - Provides a typed API client whose request and response types match contracts C1–C3 field for field
  - Renders a Quote page with a product selector populated from `GET /api/products`, and a dynamic form showing exactly the quote spec's risk inputs for the chosen product (TERM_LIFE 6, MOTOR 6, HOUSEHOLD 7), with selects or checkboxes for enumerations and booleans
  - Submits to `POST /api/quotes` and renders `annualPremium`, `sumInsured`, `ruleVersion`, `validUntil`, `quoteId` and every breakdown factor, plus an "Apply" call to action that carries the quote ID
  - Displays money exactly as the decimal strings received, with no `Number()`, `parseFloat`, unary-plus or arithmetic on money anywhere in the source
  - Shows each `errors[]` entry from a 400 problem+json response next to the input whose name matches `field`
  - Puts a `data-testid` on every input, the submit button, the premium, the rule version, each breakdown row and the Apply call to action
  - Lays out with CSS grid/flex and a single 640px breakpoint, with no horizontal scrolling at 375px width
  - Includes Vitest tests titled "AC-01.1: …" (mocked 6850.00 shown verbatim with rule version 1), "AC-01.7: …" (per-field errors shown) and "AC-01.8: …" (every breakdown factor rendered), all passing under `npm test`
  - Passes `npm run build` with no TypeScript errors
  - Saves a red log showing the AC-01.1, AC-01.7 and AC-01.8 Vitest tests failing before the page exists, and a green log showing them passing, in `sprint-progress/TASK-002-red.log` and `TASK-002-green.log`

### TASK-003: Product catalog — seed v1 rule files, rule-set domain and validator, append-only persistence, startup load and catalog API
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-001]
- **acceptance_criteria**:
  - Adds `rules/term-life/v1.json`, `rules/motor/v1.json` and `rules/household/v1.json` at the spec's location, with version 1, effectiveFrom 2026-01-01, the quote spec's v1 factor tables and sum-insured limits, the underwriting spec's v1 rules (UW-TL-001..005, UW-MO-001..004, UW-HH-001..004) and the catalog launch table's lifecycle values (J4, J5)
  - Passes every item of the policy-version-validator checklist for all three files (decimal strings, sorted contiguous inclusive bands, unique prefixed rule codes, known operators, complete lifecycle block)
  - Yields 6850.00, 15150.00 and 11350.00 when the independent `premium_check.py` oracle's `quote` command reads the rule files in `backend/src/main/resources/rules/` with the AC-01.1, AC-01.2 and AC-01.3 inputs
  - Models a rule set in the domain layer as immutable value types with every decimal held as `BigDecimal`, with JSON parsing kept outside the domain layer
  - Reports every violation at once from `RuleSetValidator`: min greater than max, unknown operator, duplicate rule codes, missing required sections, negative decimals, unknown rating model
  - Resolves the active version as the highest version whose effectiveFrom is on or before today according to the injected Clock, and returns 404 (`NoActiveRuleSetException`) when none exists, proven by tests displayed as "AC-02.4: …" that use fixed clocks before and on a future MOTOR v2 fixture's effectiveFrom
  - Loads the seed files into the rule-set table on startup only where (productCode, version) is absent, leaving rows unchanged on restart
  - Returns exactly TERM_LIFE, MOTOR and HOUSEHOLD from `GET /api/products`, each with activeVersion 1 and a distinct ratingModel (contract C1), proven by a test displayed as "AC-02.1: …"
  - Proves in a test displayed as "AC-02.2: …" that the three v1 rule sets differ in rating factors, in underwriting code prefixes (UW-TL-, UW-MO-, UW-HH-) and in lifecycle parameters
  - Stores a PRODUCT_MANAGER publish to `POST /api/products/MOTOR/versions` as version 2, leaves v1's stored JSON byte-for-byte unchanged, and writes an append-only audit entry carrying the actor ID, proven by a test displayed as "AC-02.3: …"
  - Creates a product when version 1 is published for an unknown productCode whose ratingModel is one of the three known models
  - Returns 400 listing each violation, and stores nothing, when an invalid rule set is published, proven by a test displayed as "AC-02.5: …"
  - Returns 403 when a CUSTOMER calls `POST /api/products/MOTOR/versions`, proven by a test displayed as "AC-02.7: …"
  - Persists rule-set versions and audit entries through new Flyway migrations with NUMERIC (never floating-point) money and rate columns, using entities with no setters or mutators and repositories with no delete or update methods
  - Includes an `AppendOnlyArchTest` check displayed as "AC-02.6: …" that fails while the rule-set version entity or repository is absent or mutable, with that failure recorded in the red log before the entity exists (J3)
  - Ships no v2 or later seed rule file (J6)
  - Saves a red log showing the AC-02.1 to AC-02.7 tests failing before their implementation exists, and a green log showing them passing, in `sprint-progress/TASK-003-red.log` and `TASK-003-green.log`
  - Passes `./mvnw verify`

### TASK-004: ArchUnit architecture rules with negative fixtures (NFR-08)
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-003]
- **acceptance_criteria**:
  - Implements `LayeredArchitectureTest`, `DomainPurityArchTest`, `MoneyPrecisionArchTest`, `AppendOnlyArchTest` and `ControllerPlacementArchTest`, encoding the layer matrix and rules of docs/architecture.md §2 and importing production classes only
  - Gives every rule a `because(…)` that names the NFR it protects
  - Pairs every rule with a negative-fixture test that shows the rule rejecting a deliberately violating class, with fixtures kept out of the production import
  - Covers in the append-only rule the seven entities listed in docs/architecture.md plus `QuoteEntity` (J11), requiring no public setters, no `delete*` repository methods and no CRUD/JPA repository supertypes
  - Retains the AC-02.6 check from TASK-003 inside `AppendOnlyArchTest`, still failing when the rule-set version entity is absent or mutable
  - Tags every architecture test with NFR-08 (plus NFR-01 or NFR-02 where relevant)
  - Passes against current production code without weakening any rule. Any real violation is fixed in production code and recorded in the progress file
  - Saves a red log showing each rule failing against its negative fixture, and a green log showing all architecture tests passing against production code, in `sprint-progress/TASK-004-red.log` and `TASK-004-green.log`
  - Passes `./mvnw verify`

### TASK-005: Quote engine domain — rating models, PremiumCalculator, PremiumBreakdown and risk-input validation
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-003, TASK-004]
- **acceptance_criteria**:
  - Implements the TERM_LIFE, MOTOR and HOUSEHOLD rating models behind a `PremiumCalculator` facade that selects the model by the rule set's ratingModel
  - Reads every factor, rate, band and fee from the rule set, with none of the spec's factor literals hard-coded in rating code
  - Computes with `BigDecimal` only, multiplying in the spec's order at scale 10 HALF_UP after each multiply or divide, adding the policy fee, then rounding once to scale 2 HALF_UP
  - Applies bands inclusive at both ends, and treats an input outside every band as a validation violation rather than a default
  - Returns from `RiskInputValidator` one violation per missing or invalid field, including sum insured outside the rule set's min/max (J7, J8, J9), proven by a domain test displayed as "AC-01.7: …"
  - Proves exact premiums in tests displayed as "AC-01.1: …" (6850.00), "AC-01.2: …" (15150.00) and "AC-01.3: …" (11350.00), using the v1 rule-set file contents, and asserts both `isEqualByComparingTo` and `scale() == 2`
  - Proves in a test displayed as "AC-01.4: …" that 100 repeated quotes with identical inputs and rule version give identical premiums
  - Proves in a test displayed as "AC-01.5: …" that age 30 applies base rate 0.80 and age 31 applies 1.20, as shown in the breakdown
  - Proves in a test displayed as "AC-01.6: …" that a MOTOR rule-set fixture with baseRate 0.030 prices the AC-01.2 input at 18150.00 with no code change
  - Proves in a test displayed as "AC-01.8: …" that the breakdown lists every applied factor, including the policy fee, and that recomputing the formula from the breakdown alone reproduces the premium
  - Matches the `premium_check.py` oracle exactly for the AC-01.1, AC-01.2, AC-01.3 and AC-01.6 inputs
  - Imports only `java.*` and domain classes, and passes `DomainPurityArchTest` and `MoneyPrecisionArchTest`
  - Saves a red log showing the AC-01.1 to AC-01.8 domain tests failing before the rating code exists, and a green log showing them passing, in `sprint-progress/TASK-005-red.log` and `TASK-005-green.log`
  - Passes `./mvnw verify` with domain line coverage at or above 80%

### TASK-006: Quote persistence and public quote API (POST/GET /api/quotes, NFR-04.2)
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-005]
- **acceptance_criteria**:
  - Returns 201 from anonymous `POST /api/quotes` with the response shape of contract C2, priced by the domain calculator against the product's active rule version
  - Sets `validUntil` to the injected Clock's today plus the rule set's quoteValidityDays
  - Stores each quote immutably, with the full risk inputs kept server-side, through a new Flyway migration (NUMERIC(19,2) money), an entity with no setters and a repository with no delete or update methods
  - Proves through the API, in a test displayed as "AC-01.1: …", a premium of 6850.00 and a stored ruleVersion of 1
  - Proves in a test displayed as "AC-01.6: …" that after a MOTOR v2 (baseRate 0.030, effectiveFrom on or before the fixed clock's today) is published in test setup, the AC-01.2 input quotes at 18150.00 with ruleVersion 2
  - Returns 400 problem+json whose `errors[]` names each invalid field (sum insured out of range, a missing required input, several at once) and stores no quote, proven by a test displayed as "AC-01.7: …"
  - Returns from anonymous `GET /api/quotes/{id}` the premium, breakdown, ruleVersion and validity of a stored TERM_LIFE quote that had BMI and health declarations, with no `bmi`, `healthDeclarations` or other health input anywhere in the body, proven by a test displayed as "NFR-04.2: …"
  - Generates quote IDs matching `^Q-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$` (UUIDv4, never sequential), asserted in the NFR-04.2 test
  - Returns 404 problem+json for an unknown quote ID
  - Contains no log statement that emits risk inputs, health fields or request bodies, logging the quote ID only (NFR-03)
  - Keeps the controller free of business logic and repository injection, passing all ArchUnit tests including `AppendOnlyArchTest` with `QuoteEntity` present
  - Saves a red log showing the API-level AC-01.1, AC-01.6, AC-01.7 and NFR-04.2 tests failing before the quote API exists, and a green log showing them passing, in `sprint-progress/TASK-006-red.log` and `TASK-006-green.log`
  - Passes `./mvnw verify`

### TASK-007: SPA packaging, Playwright smoke and sprint-wide verification
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-002, TASK-006]
- **acceptance_criteria**:
  - Packages the built frontend into the backend so that `./mvnw spring-boot:run` serves the Quote page at `http://localhost:8080/` and `/api` from one process (docs/architecture.md §6, J15)
  - Configures Playwright with desktop-chromium and mobile-chromium (iPhone 13) projects and a web server that starts the backend through the Maven wrapper on port 8080
  - Adds an e2e test titled "AC-01.1: term life quote shows 6850.00", using `data-testid` selectors, web-first assertions and no `waitForTimeout`, and passes it on both projects
  - Passes `./mvnw verify` end to end with the JaCoCo thresholds unchanged (80% domain, 70% overall)
  - Passes `npm test` and `npm run build`, with the lcov report refreshed at `frontend/coverage/lcov.info`
  - Reports COVERED (not PARENT-ONLY or MISSING) in the offline AC audit for AC-01.1–01.8, AC-02.1–02.7, NFR-04.2, NFR-06.1, NFR-06.2 and NFR-07.1
  - Confirms from the TASK-001 to TASK-006 red and green logs that every ID above failed before it passed, and that no commits were made during the sprint (`git log` on `feat/catalog-quote` unchanged since the sprint started)
  - Records in the task progress file the commands run and their results (verify, npm test, Playwright, oracle spec-examples, AC audit)
  - Saves a red log showing the AC-01.1 e2e test failing before packaging is in place, and a green log showing it passing on both projects, in `sprint-progress/TASK-007-red.log` and `TASK-007-green.log`

## Execution Schedule

### Parallel Batch (no dependencies — run simultaneously via Agent Teams)
- TASK-001
- TASK-002

### Sequential Tasks (ordered by dependency)
1. TASK-003 (depends on TASK-001)
2. TASK-004 (depends on TASK-003)
3. TASK-005 (depends on TASK-003, TASK-004)
4. TASK-006 (depends on TASK-005)
5. TASK-007 (depends on TASK-002, TASK-006)
