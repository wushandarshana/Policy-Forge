> Archived from `.sprint/20261028-114922/` on 2026-11-03 · branch `feat/application-underwriting` · harness agent-harness 2.8.0. Content below is unchanged from the harness artifact.

# Sprint Plan

## Spec
specs/underwriting_spec.md — PolicyForge Sprint 2 (branch feat/application-underwriting, cut from dev). Scope: application submission with KYC stub and risk re-validation (AC-03.1–03.4); underwriting rules engine returning AUTO_BIND / MANUAL_REVIEW / DECLINE with reason codes from the active rule version (AC-04.1–04.6, including the parameterised reason-code matrix); underwriter workbench queue and decisions plus admin DECLINE override, with append-only decisions and actor-ID audit (AC-09.1–09.5, NFR-04); PII never logged (NFR-03.1); role boundary including CUSTOMER → underwriter endpoint 403 (NFR-04.1); React Apply page, application status page and Underwriter Workbench page with Vitest and Playwright (desktop + mobile) and a committed Playwright visual-snapshot directory. Build on the Sprint 1 code (catalog, quote engine, RiskInputSchema, RequestBodySizeLimitFilter, SecurityConfig). Constraints: root CLAUDE.md non-negotiables and folder CLAUDE.md files, especially the web/CLAUDE.md lessons KD-007 to KD-010 (consumes JSON on every state-changing endpoint, allow-list responses, explicit security matchers, fixed problem detail, default-deny limits on decoded paths); every test named AC-NN.M / NFR-NN.M; synthetic KYC data only; Flyway migrations append-only (next version after V3). TDD commit policy (user decision): each code task commits live on feat/application-underwriting — `test(red): AC-NN.M …` with the failing tests BEFORE the implementation, then `feat(green): …`, then `refactor: …` when there is cleanup; stage explicit paths and commit with `git commit -m "…" -- <paths>`; never push; never commit on dev or main; also save red/green test output to sprint-progress/<TASK>-red.log and <TASK>-green.log in the workspace. Environment: java is not on PATH — prefix Maven with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`; integration tests run under failsafe (selective syntax in .claude/agents/security-reviewer.md); leave port 8080 free. Use project agents and skills where they fit (test-engineer and spec-to-test-generator for red, ui-designer for pages; evaluator uses Playwright MCP).

### Interpretation (read this at the plan checkpoint)

**How to read this plan.** `specs/underwriting_spec.md` is the source of truth. Its Clarifications **U-1 to U-15** and the
ACs added in `7303fde` settle every behaviour question the earlier plan decided as a judgment call. This plan cites a
U-rule by number and does not restate or re-decide it. If a criterion below seems to disagree with a U-rule, the U-rule
wins and the criterion is a plan defect. The planner calls **P-1 to P-9** cover only what the spec leaves open:
decomposition, scheduling, build flags, test strategy, data isolation and effort.

**Readiness check (planner, 2026-10-28).** `feat/application-underwriting` is at `7303fde` with a clean tree. It is two
spec-only commits ahead of `dev` (`ddeae97`): `797a4bc` (vehicle age > 15 is a quote-time 400; UW-MO-002 is domain-level
only) and `7303fde` (U-1 to U-15, AC-03.5–03.8, AC-09.6–09.7). `main` is `8e265d6`. The branch has no remote
counterpart. **Sprint base commit: `7303fde`.** All git-history checks count commits after it.

**Scope.** The sprint brief names AC-03.1–03.4 and AC-09.1–09.5. Commit `7303fde` added AC-03.5–03.8 and AC-09.6–09.7,
and app_spec §9 stages the MANUAL_REVIEW seed application into this sprint (U-14, AC-09.7). The spec is truth, so all
of them are in scope. The sprint owns these IDs, each by at least one task:

| ID | Owned by |
|---|---|
| AC-03.1 | TASK-003 (domain), TASK-004 (API), TASK-006 (routing Vitest) |
| AC-03.2 | TASK-003, TASK-004, TASK-002 (Vitest), TASK-006 (E2E) |
| AC-03.3, AC-03.4 | TASK-004, TASK-002; AC-03.4 also TASK-003 |
| AC-03.5 | TASK-004 |
| AC-03.6, AC-03.7, AC-03.8 | TASK-004, TASK-002 |
| AC-04.1–AC-04.5 | TASK-001 (domain), TASK-004 (API); AC-04.1 and AC-04.3 also TASK-002; AC-04.1 and AC-04.2 also TASK-006 |
| AC-04.6 | TASK-001 |
| AC-09.1–AC-09.3 | TASK-005, TASK-002; AC-09.2 and AC-09.3 also TASK-006 |
| AC-09.4, AC-09.5 | TASK-003, TASK-005, TASK-002 |
| AC-09.6 | TASK-003, TASK-005, TASK-002, TASK-006 |
| AC-09.7 | TASK-004 (seed), TASK-005 (queue), TASK-002, TASK-006 |
| NFR-03.1 | TASK-004, TASK-005 |
| NFR-04.1 | TASK-005 |
| NFR-04.3, NFR-04.4 | TASK-004, TASK-005 (Sprint 1 tests already cover them; this sprint adds tests for the new endpoints) |

TASK-006 audits every row. **Not in this sprint:** everything in U-15, and the Linux snapshot baselines (Sprint 5, per
the spec's UI section).

**Harness override (user decision).** For this sprint the orchestrator overrides the harness's generic "generators must
not commit" instruction. Each code task commits live on `feat/application-underwriting`: `test(red): AC-NN.M …` with the
failing tests before the implementation, then `feat(green): …`, then `refactor: …` when there is cleanup. Generators
stage explicit paths with `git commit -m "…" -- <paths>`, never push, and never commit on `dev` or `main`. They also
save the red and green runs as `sprint-progress/<TASK>-red.log` and `<TASK>-green.log`.

**Primary deliverable.** The slice from a stored quote to an underwriting decision:
- A CUSTOMER submits an application with the U-1 body. The server runs the U-2 checks, stores the application and
  moves it SUBMITTED → UNDERWRITING. In the same request it evaluates the rule version active at application time
  (U-5), then appends a `RULES` decision row (U-9) and the U-12 audit entries.
- An UNDERWRITER works the REFERRED queue (U-7 masking) and approves or declines with a paired reason code (U-10).
  An ADMIN overrides a DECLINED application. Startup seeds one REFERRED application (U-14).
- The SPA gains the Apply, Application Status and Underwriter Workbench pages from the spec's UI section, tested with
  Vitest, with Playwright on desktop and mobile, and with committed visual snapshots.

**Who depends on it.** Policy issuance takes `APPROVED → ISSUED` next (U-15). NFR-03 and NFR-04 are judged on the
masking, the audit trail and the role boundary built here. Later rule versions reuse the rules engine unchanged.

**Done looks like:**
- A full `./mvnw verify` (SPA packaged) is green, with ArchUnit passing and the JaCoCo thresholds unchanged (80% domain,
  70% overall).
- `npm test` (lcov committed) and `npm run build` are green. The Playwright journeys pass on `desktop-chromium` and
  `mobile-chromium`, with baselines committed under `frontend/e2e/__snapshots__/`.
- `python scripts/ac_audit.py --offline --no-write` reports every ID in the Scope table as COVERED (not PARENT-ONLY or
  MISSING), and every Sprint 1 ID is still COVERED.
- After `7303fde`, every ID in the Scope table has a `test(red)` commit naming it before a `feat(green)` commit.
- Every task's red and green logs pass `scripts/redact_evidence.py --check` (KD-011).
- Nothing is pushed: `origin/feat/application-underwriting` does not exist. `dev` is still `ddeae97` and `main` is
  still `8e265d6`, locally and on `origin`.

**Settled by the spec and removed from the judgment calls.** The earlier plan's J3–J19 now live in the spec:

| Earlier call | Now |
|---|---|
| J3 | U-1 |
| J4 | U-2 |
| J5 | U-3 |
| J6 | U-4 |
| J7 (audit actions, status columns) | U-12 |
| J8, J15 | U-5 |
| J9 | U-6 |
| J10 | U-7 |
| J11 | U-8 |
| J12 | U-10. Code pairing is now **enforced** (AC-09.6). |
| J13 | U-11 |
| J14 | U-9 |
| J16 | U-13 |
| J17 | U-15. The seed is now **in scope** (U-14). |
| J18 | spec UI, "Sign-in" |
| J19 | spec UI, "Visual snapshots" |

**Planner calls. Veto any of these here. Each one is cheap to change now and expensive later.**

- **P-1 Decomposition and scheduling.** The backend tasks run one at a time (TASK-001 → TASK-003 → TASK-004 →
  TASK-005), because they share one working tree, one `backend/target`, one Flyway sequence and one branch.
  Concurrent Maven runs would corrupt each other. TASK-003 depends on TASK-001 partly by choice: both write the domain
  package, and TASK-003 also uses TASK-001's outcome type. TASK-002 touches only `frontend/`, so it runs in parallel
  with the backend chain against the contracts below. TASK-006 joins both lines. Generators commit concurrently, so a
  `.git/index.lock` error means another generator is committing: re-run the commit and never delete the lock file.
- **P-2 Build flag.** The backend tasks (TASK-001, TASK-003, TASK-004 and TASK-005) run Maven with
  `-Dskip.frontend=true`. A full build runs `npm ci`, which would delete `frontend/node_modules` while TASK-002 is
  using it. Only TASK-006 runs the full build.
- **P-3 TDD evidence shape.** A red commit compiles (production stubs are allowed), and its new tests fail on an
  assertion, not a compile error. One red commit may name several sub-criteria. A structural check, such as an ArchUnit
  existence check, gets its red by failing while the class is absent, as in Sprint 1.
- **P-4 How AC-03.1's "moved to UNDERWRITING before evaluation" is proven.** Two tests prove it. A domain test shows
  that SUBMITTED can't jump straight to an outcome. An API test shows that the U-12 `APPLICATION_SUBMITTED` entry
  (SUBMITTED→UNDERWRITING) is recorded before the `UNDERWRITING_AUTO_DECISION` entry.
- **P-5 How the seed is produced (U-14 leaves this open).** The seed goes through the same quote and application
  services as a real submission, as `customer2`, at startup. It therefore gets a `RULES` decision row (actor `system`)
  at the active version and the two U-12 audit entries, with no special-case writes. Its KYC is synthetic, and its date
  of birth matches the quote age on the injected clock. TASK-004 builds the seed so that every later task's tests run
  with it present. TASK-005 proves the queue half of AC-09.7.
- **P-6 Where the Application Status page gets its data.** app_spec §8 has no endpoint that reads a single
  application. The Status page reads `GET /api/applications/mine` (U-13) and picks the application by ID. An ID that
  isn't in the list shows a fixed not-found message. No new endpoint is added.
- **P-7 Tests that go beyond the spec's minimum:**
  - **NFR-03.1.** The log capture also checks full name and date of birth (root rule 7). It covers a rejected
    invalid-KYC submission, because framework error logging can echo the request body. Our packages run at DEBUG during
    the capture, and TASK-005 extends it to a queue read, a decision and an override. Types that carry KYC never print
    PII from their string form.
  - **AC-04.5.** The v2 rule set is a test fixture (U-15), published only in an isolated test context. Every other
    integration test still sees v1 as active, whatever order the tests run in.
  - **Seed-aware tests.** Integration tests that read the queue or `customer2`'s applications find their rows by
    application ID (U-14).
- **P-8 E2E data isolation and snapshot views.** Desktop and mobile run in parallel against one server and one
  database, which can keep earlier state, including the seed. So each test:
  - creates its own quote and application
  - acts on that application by its ID
  - never assumes a queue length or position
  - computes the date of birth from today's date, so the KYC age always matches the quote

  At minimum, snapshots cover the Apply form, the Application Status page and the Workbench case detail panel. Dynamic
  values are masked as the spec's UI section requires.
- **P-9 Effort overrides.** TASK-004 and TASK-005 are raised from `high` to `xhigh`. They hold the PII storage and
  logging, the role boundary and the audit trail, the same kind of code where the Sprint 1 security review found its
  defects (KD-007 to KD-010). If the orchestrator clamps sonnet to `high`, nothing else changes.

**Cross-task contracts.** The U-rules fix the behaviour. These contracts fix the wire shapes the spec leaves open, so
that TASK-002 can run in parallel and every task can start cold.

- **C1. `POST /api/applications`** (CUSTOMER, `application/json` only).
  - The request is the spec's example body, exactly `{quoteId, kyc}` (U-1).
  - The response is **201**: `{ applicationId ("A-<uuid>", U-5), quoteId, productCode, status
    ("APPROVED"|"REFERRED"|"DECLINED"), decision ("AUTO_BIND"|"MANUAL_REVIEW"|"DECLINE"), reasonCodes, reasons
    ([{code, description}], U-8), ruleVersion (integer), submittedAt (ISO instant) }`.
  - It has no KYC fields (U-7).
- **C2. `GET /api/applications/mine`** (CUSTOMER) returns an array of C1 objects, newest first (U-13). `status` is the
  current status. `decision`, `reasonCodes`, `reasons` and `ruleVersion` are the `RULES` result.
- **C3. Errors.** All errors are `application/problem+json`, with a fixed `detail` that never echoes input.
  - **400.** Carries `errors[]` of `{field, message}` with dotted paths (U-2). The fields are: `quoteId`,
    `kyc.fullName`, `kyc.dateOfBirth`, `kyc.aadhaar`, `kyc.pan`, `kyc.address.line1`, `kyc.address.city`,
    `kyc.address.postcode`, risk-input names, `decision`, `reasonCode` and `comment`. An unknown key is reported by its
    own dotted path (U-1, AC-03.5).
  - **Top-level `code`.**
    - 400: `QUOTE_NOT_FOUND`, `QUOTE_EXPIRED` and `KYC_AGE_MISMATCH` (U-2).
    - 409: `QUOTE_ALREADY_APPLIED` (U-2, U-3) and `INVALID_APPLICATION_STATE` (U-10). A lost optimistic-lock race (U-9)
      also returns 409 `INVALID_APPLICATION_STATE`.
  - **404.** An unknown or malformed application ID gets a 404 with a fixed detail (U-10). 401, 403, 413 and 415 keep
    their Sprint 1 shapes.
  - **Check order on the decision and override endpoints.** Body validation (400) runs first, then the application
    lookup (404), then the state check (409). This is the same shape-first order as U-2.
- **C4. `GET /api/underwriting/queue[?includeDeclined=true]`.**
  - Access: UNDERWRITER. `includeDeclined=true` needs ADMIN and returns 403 for anyone else (U-11).
  - Returns an array sorted oldest first by `submittedAt`. Each item is built from the U-7 allow-list:
    `{ applicationId, quoteId, productCode, status, submittedAt, ruleVersion, decision, reasonCodes, reasons, kyc: {
    fullName, aadhaar (masked), pan (masked) }, riskInputs: { declared inputs only } }`.
- **C5. Manual decision and override** (both `application/json` only).
  - `POST /api/underwriting/{applicationId}/decision` (UNDERWRITER) takes `{ decision: "APPROVE"|"DECLINE",
    reasonCode, comment }`. The reason code must be paired with the decision as U-10 requires.
  - `POST /api/underwriting/{applicationId}/override` (ADMIN) takes `{ reasonCode, comment }`. The reason code is
    `UW-OVR-001` or `UW-OVR-002` only (the spec's `UW-OVR-0xx` body and U-10).
  - Both return **200** `{ applicationId, previousStatus, status }`.
- **C6. Records.**
  - Decision rows follow U-9. Their outcome is AUTO_BIND, MANUAL_REVIEW or DECLINE for `RULES`, APPROVE or DECLINE
    for `MANUAL`, and APPROVE for `OVERRIDE`.
  - Audit entries follow U-12 and the spec's AC-09 field list. They use entity type `APPLICATION`, with the
    application ID as the entity ID.
  - The actor is the authenticated username (for example `customer1`, `underwriter1` or `admin1`), except on the
    automated `RULES` row and the `UNDERWRITING_AUTO_DECISION` entry, whose actor is `system`.
- **C7. Flyway.** New migrations start at V4 (U-12). Never edit V1–V3 or any migration committed earlier in this
  sprint. Fix forward with a new version.
- **C8. SPA.** Routes and sign-in follow the spec's UI section:
  - The routes are `#/apply?quoteId=…`, `#/applications/<applicationId>` and `#/workbench`.
  - Credentials are held in memory only.
  - The SPA detects an ADMIN by whether the `includeDeclined=true` request succeeds.

  The Quote page stays the default route and stays public.

**Execution notes for every generator.**

- **Maven.** Run `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`
  before any Maven command. Backend tasks add `-Dskip.frontend=true` (P-2).
- **Selective integration tests.** These run under failsafe (KD-009): `./mvnw verify -Dskip.frontend=true
  -Dit.test=<Pattern> -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`. The final green
  run of every backend task is a full `./mvnw verify -Dskip.frontend=true`, with JaCoCo enabled.
- **Commits.** Commit as the Harness override above describes. Retry on `index.lock` (P-1). Frontend green commits
  include a refreshed `frontend/coverage/lcov.info`.
- **Data.** Use synthetic data only: Aadhaar `XXXX-XXXX-1234`, PAN `ABCDE1234F`, the name "Asha Test", and addresses
  like the spec's example. Keep money as decimal strings and `BigDecimal`. Log IDs only.
- **Test names.** JUnit tests use `@Tag("AC-NN")` plus `@DisplayName("AC-NN.M: …")`, or the NFR form. Vitest and
  Playwright titles start with `AC-NN.M:`. The `ac-tag-check` hook rejects untagged test files.
- **Port.** Port 8080 must be free when a task ends. Stop any server you started.
- **Agents and skills:**

  | Task | Agents and skills |
  |---|---|
  | TASK-001 | `test-engineer` (red) with the `spec-to-test-generator` skill (AC-04 matrix, KD-004) |
  | TASK-002 | `ui-designer` (Vitest first, then pages) |
  | TASK-003 | `test-engineer` with `spec-to-test-generator` |
  | TASK-004 | `test-engineer`; `migration-coherence-agent` (migrations); `archtest-author` skill (NFR-02 existence check); `policy-version-validator` skill (v2 test fixture) |
  | TASK-005 | `test-engineer`, then a `security-reviewer`-style self-check of the role matrix with the selective integration-test command |
  | TASK-006 | `ui-designer` and `test-engineer` (E2E and snapshots) |

  The Evaluator adds a Playwright MCP walkthrough of Apply → Status → Workbench. The seeded case keeps the queue
  non-empty.

**Out-of-orchestrator scope. The user does these manually after the sprint completes:**
1. Run `/sprint-archive S2`. It archives this workspace into the committed evidence trail (`sprint-contracts/` and
   `specs/reviews/`).
2. Run `/pr`. It runs the review gate (tests, the `security-reviewer` and `test-engineer` agents, AC coverage) and
   writes `docs/prs/PR-NNN.md`. It then pushes the branch and opens a GitHub PR into `dev` with `gh pr create`. The
   user merges that PR on GitHub with "Create a merge commit" (no squash or rebase).

## Tasks

### TASK-001: Underwriting rules engine (domain): UnderwritingRuleEvaluator, condition evaluation, UnderwritingOutcome and ReasonCode
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Implements `UnderwritingRuleEvaluator`, a condition evaluator, `UnderwritingOutcome` (AUTO_BIND, MANUAL_REVIEW, DECLINE) and `ReasonCode` in the domain layer, importing only `java.*` and domain classes
  - Evaluates every underwriting rule of the rule-set version it is given against the risk inputs plus the derived `sumInsured` and `age` (U-5), with no rule code, threshold or product literal hard-coded in evaluator code
  - Supports all nine existing condition operators (EQ, NEQ, GT, GTE, LT, LTE, IN, NOT_EMPTY, IS_TRUE). It compares numbers as `BigDecimal` built from strings or integers, never floating point, and fires a rule only when all of its conditions hold
  - Raises a domain exception, and never skips the rule, when a condition names a field that is absent or can't be compared (U-6). A unit test proves this
  - Applies the spec's outcome precedence and returns every fired rule's code and description sorted by code. AUTO_BIND returns `["UW-OK"]` with the description "No underwriting rule fired" (U-8). The result also carries the evaluated rule-set version
  - Proves, against the shipped v1 rule files: "AC-04.1: …" (TERM_LIFE age 35, non-smoker, BMI 24, no declarations, SI 5,000,000.00 → AUTO_BIND `["UW-OK"]`), "AC-04.2: …" (smoker, SI 12,000,000.00 → MANUAL_REVIEW `["UW-TL-002"]`), "AC-04.3: …" (age 62, BMI 37 → DECLINE `["UW-TL-001","UW-TL-004"]`) and "AC-04.4: …" (MOTOR 3 claims → DECLINE `["UW-MO-001"]`; HOUSEHOLD TIMBER → MANUAL_REVIEW `["UW-HH-003"]`)
  - Proves "AC-04.5: …" with a TERM_LIFE v2 test fixture (U-15) whose UW-TL-001 threshold is 65: age 62 is not DECLINE, and the result references version 2
  - Implements the AC-04.6 matrix as a parameterised test with one row per v1 rule: 13 rows across TERM_LIFE, MOTOR and HOUSEHOLD, with UW-MO-002 tested at domain level only. Each row is displayed "AC-04.6: …", triggers only its own rule where the rule set allows, and asserts the outcome and the exact reason codes. The UW-TL-005 row uses BMI 46 and expects DECLINE `["UW-TL-004","UW-TL-005"]` (KD-004)
  - Shows on `feat/application-underwriting` a `test(red): AC-04.…` commit with the failing tests (stubs allowed) before the `feat(green)` commit, plus a `refactor:` commit if there was cleanup. Saves `sprint-progress/TASK-001-red.log` and `TASK-001-green.log` naming AC-04.1–AC-04.6
  - Passes `./mvnw verify -Dskip.frontend=true` with ArchUnit green and JaCoCo at or above 80% domain and 70% overall

### TASK-002: Frontend page components: Apply, Application Status and Underwriter Workbench, with a Basic-auth session context, typed API client and Vitest
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Adds a session context and a sign-in form as the spec's UI "Sign-in" section requires: credentials are held in memory only, never in localStorage, sessionStorage, cookies or URLs. Adds an API helper that attaches the Basic `Authorization` header to protected calls only
  - Adds typed API client functions whose request and response types match contracts C1–C5 field for field. Parses C3 problem responses, including `errors[]` and `code`
  - Renders an Apply page for a given quote ID with inputs for full name, date of birth, Aadhaar, PAN, address line 1, city and postcode, and posts exactly the C1 request body
  - Shows each `errors[]` entry next to the input whose dotted path matches its `field`. Shows fixed messages for `QUOTE_NOT_FOUND`, `QUOTE_EXPIRED`, `KYC_AGE_MISMATCH` and `QUOTE_ALREADY_APPLIED`
  - Renders an Application Status page that reads C2 and picks the application by ID (P-6), with a fixed not-found message for an ID that isn't in the list. It shows a status badge, the decision, the rule version, and every reason code with its description in the order the API returned them, and no KYC fields
  - Renders a Workbench page with three parts:
    - a queue table with one row per application in API order, where each row's test ID contains its application ID
    - a case detail panel showing the masked Aadhaar and PAN exactly as received, the risk inputs and the reasons
    - a decision form that posts the C5 decision body
  - Offers reason codes as U-10 pairs them, each with its spec description: APPROVE offers only `UW-MAN-001` and `UW-MAN-002`, DECLINE offers only `UW-MAN-010`, and the Override form offers only `UW-OVR-001` and `UW-OVR-002`. Changing the decision clears the selected reason code
  - Shows declined cases and the Override action only when the `includeDeclined=true` queue request returns 200. After a 403 it shows the plain queue and never renders Override (spec UI, U-11)
  - Leaves the rest of the business validation (comment length, application state) to the server. Client-side checks cover required fields only. Shows the server's 400 `errors[]` (`comment`, `reasonCode`) at the matching field and a 409 as a fixed message
  - Never unmasks, reconstructs or computes KYC or money values, and renders API strings verbatim
  - Puts a `data-testid` on every input, button, status badge, reason-code item, queue row and the Override action
  - Lays out with CSS grid/flex and the single 640px breakpoint, with no horizontal scrolling at 375px width
  - Includes Vitest tests titled:
    - "AC-03.2: …": errors appear next to PAN, Aadhaar and postcode
    - "AC-03.3: …": `QUOTE_EXPIRED` is shown
    - "AC-03.4: …": `KYC_AGE_MISMATCH` is shown at date of birth
    - "AC-03.6: …": `QUOTE_NOT_FOUND` is shown
    - "AC-03.7: …": `QUOTE_ALREADY_APPLIED` is shown
    - "AC-03.8: …": the Status page renders the matching application from the caller's list, with no KYC fields
    - "AC-04.1: …": APPROVED, AUTO_BIND and `UW-OK` with its U-8 description
    - "AC-04.3: …": DECLINE with `UW-TL-001` then `UW-TL-004`
    - "AC-09.1: …": queue rows in API order, with masked values exactly as received
    - "AC-09.2: …": approve posts the exact decision body
    - "AC-09.3: …": the admin override posts the exact override body
    - "AC-09.4: …": a 400 `comment` error is shown at the comment field
    - "AC-09.5: …": a 409 is shown
    - "AC-09.6: …": APPROVE offers only `UW-MAN-001`/`UW-MAN-002`, DECLINE offers only `UW-MAN-010`, and the decision form offers no `UW-OVR-*` code
    - "AC-09.7: …": after a 403 on `includeDeclined=true`, the plain queue is shown and no Override is rendered
  - Leaves the app shell, routing and existing Quote page behaviour unchanged (TASK-006 mounts the pages). Touches nothing outside `frontend/`, adds no npm dependencies, and keeps every existing AC-01 Vitest test passing
  - Shows a `test(red): AC-…` commit with the failing Vitest tests before the `feat(green)` commit. Saves `sprint-progress/TASK-002-red.log` and `TASK-002-green.log` naming every AC above
  - Passes `npm test` (lcov refreshed) and `npm run build` with no TypeScript errors

### TASK-003: Application domain: KycValidator, application status lifecycle, and the manual-decision and override rules
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-001]
- **acceptance_criteria**:
  - Implements `KycValidator` in the domain layer with the spec's checks and the U-4 rules. It returns every violation at once, each naming its dotted field path with a fixed message that never contains the submitted value. The checks are:
    - full name: not blank, at most 200 characters
    - date of birth: a valid ISO date, not after today
    - Aadhaar: matches the spec regex
    - PAN: matches the spec regex
    - address line 1: not blank, at most 200 characters
    - city: not blank, at most 100 characters
    - postcode: exactly 6 digits
  - Takes today's date as an argument (no clock in the domain) and computes age in completed years. Reports `KYC_AGE_MISMATCH` as U-4 defines it: TERM_LIFE against `age`, MOTOR against `driverAge`, zero tolerance, and no age check for HOUSEHOLD
  - Proves "AC-03.2: …": PAN `abcde1234f`, Aadhaar `1234-5678` and postcode `41100` give exactly three violations, naming `kyc.pan`, `kyc.aadhaar` and `kyc.address.postcode`. The synthetic KYC (`XXXX-XXXX-1234`, `ABCDE1234F`, `411001`) gives none
  - Proves "AC-03.4: …": a date of birth giving age 40 against a quote age of 35 is `KYC_AGE_MISMATCH`. It also tests the birthday boundary: the day before the 36th birthday matches, and the day of it does not
  - Models the application statuses SUBMITTED, UNDERWRITING, APPROVED, REFERRED, DECLINED and ISSUED with the spec's transitions, and any other transition raises an invalid-application-state domain exception. The transitions are:
    - SUBMITTED→UNDERWRITING
    - UNDERWRITING→APPROVED, REFERRED or DECLINED, using the spec's outcome mapping
    - REFERRED→APPROVED or DECLINED (manual decision)
    - DECLINED→APPROVED (override)
    - APPROVED→ISSUED (issuance, which has no endpoint this sprint, U-15)
  - Proves "AC-03.1: …" at domain level (P-4): an outcome is applied only to an application in UNDERWRITING, and SUBMITTED can't move directly to APPROVED, REFERRED or DECLINED
  - Implements the U-10 rules:
    - the reason-code catalogue with the spec's descriptions
    - APPROVE needs `UW-MAN-001` or `UW-MAN-002`, and DECLINE needs `UW-MAN-010`
    - an override accepts only `UW-OVR-001` or `UW-OVR-002`
    - the comment is trimmed and must be 10–2000 characters
    - decisions apply only from REFERRED (APPROVE→APPROVED, DECLINE→DECLINED), and overrides only from DECLINED to APPROVED
  - Proves "AC-09.4: …" at domain level: a missing, blank, 9-character or 2001-character comment (after trimming) is rejected naming `comment`, and a 10-character comment is accepted
  - Proves "AC-09.5: …" at domain level: an override on APPROVED, REFERRED or UNDERWRITING raises the invalid-application-state exception
  - Proves "AC-09.6: …" at domain level. Each of these is rejected naming `reasonCode`, and no transition happens:
    - APPROVE with `UW-MAN-010`
    - DECLINE with `UW-MAN-001`
    - a decision with `UW-OVR-001` or `UW-OVR-002`

    APPROVE with `UW-MAN-001` or `UW-MAN-002`, and DECLINE with `UW-MAN-010`, are accepted
  - Renders no full name, date of birth, Aadhaar or PAN in the string form of any domain type that carries KYC (P-7)
  - Imports only `java.*` and domain classes, and passes `DomainPurityArchTest` and `MoneyPrecisionArchTest`
  - Shows a `test(red): AC-…` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-003-red.log` and `TASK-003-green.log` naming AC-03.1, AC-03.2, AC-03.4, AC-09.4, AC-09.5 and AC-09.6
  - Passes `./mvnw verify -Dskip.frontend=true` with JaCoCo at or above 80% domain and 70% overall

### TASK-004: Application submission API: persistence, append-only decisions, startup seed, POST /api/applications and GET /api/applications/mine
> Effort override: high → xhigh. This task owns PII storage, PII-free logging and error responses that must not echo input (NFR-03), where the Sprint 1 security review found defects (P-9).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-003]
- **acceptance_criteria**:
  - Adds new Flyway migrations from V4 upward and leaves V1–V3 byte-identical. They create:
    - an `application` table with the application ID, a unique quote ID (U-3), customer ID, product code, status, an optimistic-lock version, KYC columns sized for the U-4 limits, and the submission time
    - an append-only `underwriting_decision` table with the application ID, source, outcome, reason codes, rule version, actor ID, comment and decision time (U-9)
    - nullable previous-status and new-status columns on the audit table (U-12)

    No column uses a floating-point type
  - Maps decisions to an immutable entity with no setters, whose repository has no delete or update methods. The application entity changes status only through one intention-revealing method that applies the domain transition, and it has no public setters. `AppendOnlyArchTest` gains an existence check for the underwriting decision entity and repository, tagged NFR-02, which failed in the red commit before they existed
  - Exposes `POST /api/applications` for CUSTOMER only, consuming `application/json` only. It returns 201 with the C1 body, built from an allow-list with no KYC field (U-7)
  - Runs the U-2 checks in order and stores nothing on any failure. On success it does all of this in one transaction:
    - stores the application as SUBMITTED and moves it to UNDERWRITING
    - evaluates the rule version of the quote's product that is active at application time (U-5)
    - maps the outcome to a status
    - appends a `RULES` decision row with actor `system` and that rule version (U-9)
    - appends the two U-12 audit entries: `APPLICATION_SUBMITTED` (actor: the customer) and `UNDERWRITING_AUTO_DECISION` (actor: `system`)
  - Proves "AC-03.1: …": customer1 applies with a valid TERM_LIFE quote. The stored application is linked to that quote ID and to customer1, and its `APPLICATION_SUBMITTED` (SUBMITTED→UNDERWRITING) audit entry is recorded before `UNDERWRITING_AUTO_DECISION` (UNDERWRITING→APPROVED) (P-4)
  - Proves "AC-03.2: …": the response is 400 with `errors[]` naming `kyc.pan`, `kyc.aadhaar` and `kyc.address.postcode`. No application row exists, and the body contains none of the submitted values
  - Proves "AC-03.3: …": once the clock passes the quote's `validUntil`, the response is 400 `QUOTE_EXPIRED` and nothing is stored. The `validUntil` day itself is accepted (U-2)
  - Proves "AC-03.4: …": the response is 400 `KYC_AGE_MISMATCH` naming `kyc.dateOfBirth`, and nothing is stored
  - Proves "AC-03.5: …": an unknown key at the top level, inside `kyc` and inside `kyc.address` each gets a 400 naming that key by its dotted path, and nothing is stored
  - Proves "AC-03.6: …": an unknown `quoteId` gets 400 `QUOTE_NOT_FOUND` naming `quoteId`, and nothing is stored
  - Proves "AC-03.7: …": a second application for the same quote gets 409 `QUOTE_ALREADY_APPLIED`, and that quote still has exactly one application
  - Exposes `GET /api/applications/mine` for CUSTOMER only and proves "AC-03.8: …": with applications from customer1 and customer2, customer1 gets only its own, newest first, in the C2 shape with no KYC fields
  - Proves through the API, in tests displayed "AC-04.1: …", "AC-04.2: …", "AC-04.3: …" and "AC-04.4: …", the statuses APPROVED / REFERRED / DECLINED, the decisions and the exact reason codes. Each has a stored `RULES` decision row at rule version 1 with actor `system`
  - Proves "AC-04.5: …" with the isolated v2 fixture (P-7): an age-62 application is not DECLINED, and its stored decision references rule version 2
  - Seeds the U-14 application at startup by the P-5 path, and proves "AC-09.7: …". After startup, customer2 has a REFERRED TERM_LIFE application with reason codes `["UW-TL-002"]`, a `RULES` decision row and the two U-12 audit entries. Running the seeding again adds no application
  - Proves "NFR-03.1: …" as P-7 describes. The capture covers a valid TERM_LIFE submission (Aadhaar `XXXX-XXXX-1234`, PAN `ABCDE1234F` and a health declaration, which gives REFERRED) and a rejected invalid-KYC submission. No captured line contains the Aadhaar, the PAN, the health declaration text, the full name or the date of birth
  - Proves "NFR-04.3: …": a `text/plain` POST to `/api/applications` gets 415 and stores nothing. Proves "NFR-04.4: …": a body over 16 KB gets 413 and stores nothing, including through a percent-encoded variant of the path
  - Logs application and quote IDs only. Request, entity and domain types that carry KYC render no PII in their string form
  - Keeps controllers free of business logic and repository injection, and passes every ArchUnit test
  - Shows a `test(red): AC-…` / `NFR-…` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-004-red.log` and `TASK-004-green.log` naming AC-03.1–03.8, AC-04.1–04.5, AC-09.7, NFR-03.1, NFR-04.3 and NFR-04.4
  - Passes `./mvnw verify -Dskip.frontend=true`

### TASK-005: Underwriter workbench and admin override API: queue with masked KYC, manual decision, override, actor-ID audit and the role boundary
> Effort override: high → xhigh. This task is the NFR-04 authorization boundary, PII masking and the audit trail for manual decisions, where the Sprint 1 security review found defects (P-9).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-004]
- **acceptance_criteria**:
  - Exposes `GET /api/underwriting/queue` for UNDERWRITER. It lists REFERRED applications oldest first by `submittedAt`. With `includeDeclined=true` it lists REFERRED and DECLINED applications for ADMIN, and returns 403 to anyone else (U-11)
  - Builds each C4 queue item from the U-7 allow-list: masked Aadhaar and PAN, no date of birth or address, and only the product's declared risk inputs
  - Proves "AC-09.1: …": two REFERRED applications submitted at different fixed-clock instants come back oldest first relative to each other, found by ID rather than by position because the seed is present. They show `aadhaar` `**********1234` and `pan` `******234F`, and the body contains neither full value
  - Proves "AC-09.7: …": underwriter1 requesting `includeDeclined=true` gets 403. In underwriter1's plain queue, the seeded customer2 application (found by its ID) is listed as REFERRED with reason codes `["UW-TL-002"]`
  - Exposes `POST /api/underwriting/{applicationId}/decision` for UNDERWRITER and `POST /api/underwriting/{applicationId}/override` for ADMIN. Both consume `application/json` only, reject unknown keys with 400, follow the C3 check order (400, then 404, then 409), delegate every rule to the TASK-003 domain rules, and return C5
  - Returns 404 with a fixed detail for an unknown or malformed application ID. Returns 409 `INVALID_APPLICATION_STATE` for a decision on a non-REFERRED application or an override on a non-DECLINED one (U-10)
  - Changes the status, appends the decision row and appends the U-12 audit entry in one transaction. The optimistic lock stops two concurrent decisions on the same application from both succeeding: the second gets 409 `INVALID_APPLICATION_STATE` (U-9, C3)
  - Proves "AC-09.2: …": underwriter1 approves a REFERRED application with `UW-MAN-001` and a comment, and the status becomes APPROVED. A `MANUAL` decision row is appended. An `UNDERWRITING_DECISION` audit entry records actorId `underwriter1`, the application ID, the reason code, the comment, previousStatus REFERRED, newStatus APPROVED and a timestamp
  - Proves "AC-09.3: …": admin1 overrides a DECLINED application with `UW-OVR-001` and the comment "Commercial exception signed off", and the status becomes APPROVED. An `OVERRIDE` row is appended while the original `RULES` DECLINE row stays present and unchanged. An `UNDERWRITING_OVERRIDE` audit entry records actorId `admin1`, the reason code, the comment, previousStatus DECLINED and newStatus APPROVED
  - Proves "AC-09.4: …": underwriter1 calling override gets 403, and nothing changes. admin1 with a missing or 9-character comment gets 400 naming `comment`, and nothing changes. A short comment on the decision endpoint also gets 400
  - Proves "AC-09.5: …": an override on an APPROVED application gets 409 `INVALID_APPLICATION_STATE`. The status is unchanged, and no decision or audit row is appended
  - Proves "AC-09.6: …": on a REFERRED application, each of these gets 400 naming `reasonCode`, with the status unchanged and no decision or audit row appended:
    - APPROVE with `UW-MAN-010`
    - DECLINE with `UW-MAN-001`
    - `UW-OVR-001` on the decision endpoint
  - Proves "NFR-04.1: …" in a parameterised test:
    - Unauthenticated requests get 401 on every non-public `/api` endpoint, including `POST /api/applications`, `GET /api/applications/mine`, the queue, decision, override and `POST /api/products/{code}/versions`.
    - The three public endpoints do not return 401.
    - A CUSTOMER calling the queue, decision or override gets 403.
    - An UNDERWRITER calling `POST /api/applications` or `GET /api/applications/mine` gets 403.
  - Extends "NFR-03.1: …" (P-7) so that the log capture also covers a queue read, a manual decision and an override. No captured line contains the Aadhaar, the PAN, health declaration text, the full name or the date of birth
  - Proves "NFR-04.3: …": `text/plain` to decision and override gets 415, and nothing changes. Proves "NFR-04.4: …": bodies over 16 KB get 413, and nothing changes
  - Keeps the security matchers explicit, with no new public rule and no wildcard public pattern. Every new endpoint declares its role at the controller, and the actor ID comes from the authenticated principal
  - Shows a `test(red): AC-…` / `NFR-…` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-005-red.log` and `TASK-005-green.log` naming AC-09.1–09.7, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4
  - Passes `./mvnw verify -Dskip.frontend=true`

### TASK-006: App shell routing, Playwright journeys (desktop + mobile), committed visual snapshots and sprint-wide verification
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-002, TASK-005]
- **acceptance_criteria**:
  - Mounts the Quote page (default route, public), Apply, Application Status and Workbench into the app shell, with the C8 routes and a sign-in gate on the three new pages. The Quote page's existing Apply link opens the Apply page with that quote ID. A successful submission navigates to `#/applications/<applicationId>`, and a navigation link reaches `#/workbench`
  - Includes a Vitest test titled "AC-03.1: …" proving that `#/apply?quoteId=Q-…` renders the Apply page for that quote
  - Resolves any mismatch between the SPA and the API in favour of the U-rules and contracts C1–C5, and lists each change in the progress file
  - Configures the Playwright `snapshotPathTemplate` so baselines are stored under `frontend/e2e/__snapshots__/`, and keeps the `desktop-chromium` and `mobile-chromium` projects
  - Adds these Playwright tests in `apply-underwrite.spec.ts` and `workbench.spec.ts`:
    - "AC-04.1: …": customer1 quotes the AC-04.1 TERM_LIFE profile, applies with synthetic KYC, and sees APPROVED with `UW-OK`.
    - "AC-04.2: …": a smoker with SI 12,000,000.00 sees REFERRED with `UW-TL-002`.
    - "AC-03.2: …": an invalid PAN, Aadhaar and postcode show field errors, and the page does not navigate.
    - "AC-09.2: …": underwriter1 approves the test's own REFERRED case with `UW-MAN-001`, and it shows APPROVED.
    - "AC-09.3: …": admin1 overrides the test's own DECLINED case (age 62, BMI 37) with `UW-OVR-001` and "Commercial exception signed off", and it shows APPROVED.
    - "AC-09.6: …": on the test's own REFERRED case, choosing DECLINE offers only `UW-MAN-010`, and choosing APPROVE offers only `UW-MAN-001` and `UW-MAN-002`.
    - "AC-09.7: …": underwriter1's Workbench shows no Override action and no DECLINED rows.
  - Uses `data-testid` selectors and web-first assertions, with no `waitForTimeout`. Follows P-8 data isolation, and every test passes on `desktop-chromium` and `mobile-chromium` running in parallel
  - Adds visual snapshot assertions for at least the Apply form, the Application Status page and the Workbench case detail panel, with IDs, dates and timestamps masked. Baselines are committed under `frontend/e2e/__snapshots__/`, platform-suffixed as the spec's UI section describes, and a second consecutive run passes without updating snapshots
  - Passes a full `./mvnw verify` (SPA packaged, frontend not skipped) with JaCoCo thresholds unchanged at 80% domain and 70% overall. Also passes `npm test` (lcov refreshed and committed) and `npm run build`
  - Reports COVERED (not PARENT-ONLY or MISSING) in `python scripts/ac_audit.py --offline --no-write` for AC-03.1–03.8, AC-04.1–04.6, AC-09.1–09.7, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4, and still for every Sprint 1 ID
  - Confirms from `git log 7303fde..feat/application-underwriting` that every ID in the previous criterion has a `test(red)` commit naming it before a `feat(green)` commit. Also confirms that `dev` is still `ddeae97` and `main` is still `8e265d6`, locally and on `origin`, and that `origin/feat/application-underwriting` does not exist (nothing pushed)
  - Confirms that `python scripts/redact_evidence.py --check` exits 0 over every `sprint-progress/` log
  - Records the commands run and their results in the progress file: verify, npm test, build, Playwright on both projects, the AC audit and the git checks. Leaves port 8080 free when it finishes
  - Shows a `test(red): AC-…` commit with the E2E and routing tests failing before the shell routes exist, then the `feat(green)` commit. Saves `sprint-progress/TASK-006-red.log` and `TASK-006-green.log` showing both Playwright projects

## Execution Schedule

### Parallel Batch (no dependencies — run simultaneously via Agent Teams)
- TASK-001
- TASK-002

### Sequential Tasks (ordered by dependency)
1. TASK-003 (depends on TASK-001)
2. TASK-004 (depends on TASK-003)
3. TASK-005 (depends on TASK-004)
4. TASK-006 (depends on TASK-002, TASK-005)
