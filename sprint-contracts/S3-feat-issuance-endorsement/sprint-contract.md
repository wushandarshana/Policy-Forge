> Archived from `.sprint/20261103-130317/` on 2026-11-03 · branch `feat/issuance-endorsement` · harness agent-harness 2.8.0. Content below is unchanged from the harness artifact.

# Sprint Plan

## Spec
specs/policy-issuance_spec.md specs/endorsement_spec.md — PolicyForge Sprint 3 (branch feat/issuance-endorsement, cut from dev @ 246e7cb). Scope: policy issuance with unique policy numbers and ACTIVE status (AC-05.1–05.4), immutable endorsements with atomic version + transition writes and sum-insured re-rating (AC-06.1–06.7), the policy state machine with InvalidPolicyStateException (AC-10.1–10.4), plus quote ownership (AC-01.12, AC-01.13, AC-03.9 — spec commit 819a246); frontend: My Policies list, Policy Detail (versions, endorsements, status history) and Endorse page with Playwright journeys (desktop + mobile) and committed visual snapshots. Constraints: root CLAUDE.md non-negotiables and folder CLAUDE.md files, incl. web/CLAUDE.md rules KD-007..KD-010 (JSON-only consumes, allow-list responses, explicit public matchers, fixed problem detail, default-deny body limit on the routed path); money BigDecimal and endorsement adjustment must equal premium_check.py oracle (AC-06.2 = 1330.85); append-only policy versions/endorsements/transitions; every test named AC-NN.M. Commits: generators commit locally on feat/issuance-endorsement only, TDD order (`test(red)` before `feat(green)`), explicit paths, never push (same policy as Sprint 2). Hard rule KD-014: run long commands (mvnw verify, npm test, Playwright webServer) in the FOREGROUND, never background-and-wait. Environment: prefix Maven with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`. Use project agents where they fit (test-engineer for red, endorsement-agent, policy-state-machine-generator skill, ui-designer; evaluator uses Playwright MCP).

### Interpretation (read this at the plan checkpoint)

**How to read this plan.** The specs are the source of truth: `policy-issuance_spec.md` (AC-05, AC-10),
`endorsement_spec.md` (AC-06), and the ownership rules from `819a246` in `quote-engine_spec.md` (AC-01.12, AC-01.13)
and `underwriting_spec.md` (U-2 step 2, AC-03.9). The error codes E1–E11 come from the project's `endorsement-validator`
skill. This plan cites those sources and does not restate them. **G-1** is the one decision the user must make at this
checkpoint. **P-1 to P-14** are planner calls on what the spec leaves open. **SG-1 to SG-3** are spec gaps this
sprint does not fill. Contracts **C1 to C10** fix the wire shapes, so the frontend task can run in parallel and every
task can start cold. If a criterion seems to disagree with a spec rule, the spec wins and the criterion is a plan
defect.

**Readiness check (planner, 2026-11-03).**
- `feat/issuance-endorsement` is at `819a246` with a clean tree. It is one spec-only commit ahead of `dev`.
- `dev` = `origin/dev` = `246e7cb`, and `main` = `origin/main` = `8e265d6` (checked with `git ls-remote`). No
  `origin/feat/issuance-endorsement` exists.
- Flyway is at V6. No PUT, PATCH or DELETE mapping exists anywhere in `backend/src/main`.
- `premium_check.py spec-examples` passes every case. For AC-06.2 it gives new premium 9490.00 and adjustment 1330.85.
  The oracle also gives −1330.85 for the reverse change, 2640.00 when the change is effective on the term start and
  7.23 when it is effective on the term end. For MOTOR it gives 15150.00 at vehicleValue 800000.00 and 17025.00 at
  900000.00.
- `ac_audit.py --offline` covers 47 of 81 IDs. Every ID in this sprint's scope is NOT COVERED.

**Sprint base commit: `819a246`.** All git-history checks count commits after it.

**Primary deliverable.** The slice from an approved application to an endorsed policy:
- A CUSTOMER issues their APPROVED application. The policy gets a unique `PF-<TL|MO|HH>-<YYYY>-<NNNNNN>` number and
  status ACTIVE. Issuance writes version v1, term #1 (paid) and the `null → ACTIVE` transition.
- The customer views their policies, then applies immutable endorsements. Each endorsement appends an endorsement row,
  version n+1 and an ENDORSED transition in one transaction. A sum-insured change re-rates with the policy's own rule
  version.
- The pure `PolicyStateMachine` enforces the AC-10 table everywhere.
- Quotes record their owner when the requester is signed in, which closes Sprint 2 security minor #4.
- The SPA gains My Policies, Policy Detail (tabs Versions, Endorsements, Status, Refunds) and Endorse pages, plus an
  Issue action on the Application Status page. These are tested with Vitest, with Playwright on desktop and mobile, and
  with committed snapshots.

**Who depends on it.** Renewal, lapse and cancellation (Sprint 4) add transitions through the same state machine and
add terms and refund records to the same policy aggregate. The dashboard (AC-11) counts these policies.
NFR-02 is judged on the append-only version, endorsement and transition records built here.

**Done looks like:**
- A full `./mvnw verify` (SPA packaged) is green, with ArchUnit passing and the JaCoCo thresholds unchanged (80%
  domain, 70% overall).
- `npm test` (lcov committed) and `npm run build` are green.
- The Playwright journeys pass on `desktop-chromium` and `mobile-chromium` twice in a row with no snapshot drift.
  Baselines are committed under `frontend/e2e/__snapshots__/`.
- `python scripts/ac_audit.py --offline --no-write` reports every ID in the Scope table as COVERED, and every Sprint 1
  and Sprint 2 ID as still COVERED.
- After `819a246`, every Scope ID has a `test(red)` commit naming it before a `feat(green)` commit.
- Every `sprint-progress/` log passes `scripts/redact_evidence.py --check` (KD-011).
- Nothing is pushed: `dev` and `main` are unchanged locally and on `origin`.

**Scope.** Each ID below is owned by at least one task:

| ID | Owned by |
|---|---|
| AC-01.12 | TASK-003 |
| AC-01.13 | TASK-002 (Vitest), TASK-006 (E2E) |
| AC-03.9 | TASK-003 |
| AC-05.1 | TASK-001 (number format, dates), TASK-004 (API), TASK-002 (Vitest), TASK-006 (routing, E2E) |
| AC-05.2, AC-05.4 | TASK-004 |
| AC-05.3 | TASK-004, TASK-002 |
| AC-06.1, AC-06.2, AC-06.5, AC-06.6 | TASK-001 (domain), TASK-005 (API), TASK-002 (Vitest), TASK-006 (E2E) |
| AC-06.3, AC-06.7 | TASK-005 |
| AC-06.4 | TASK-001, TASK-005, TASK-002 |
| AC-10.1 | TASK-001 |
| AC-10.2 | TASK-001 (E1 row), TASK-005, TASK-002 |
| AC-10.3 | TASK-001 (domain only; P-6) |
| AC-10.4 | TASK-005, TASK-002 |
| NFR-03.1, NFR-04.1, NFR-04.3, NFR-04.4 | TASK-004, TASK-005 (both extend the existing tests to the new endpoints). TASK-003 also re-proves NFR-04.1 for the public quote endpoint |

**Not in this sprint:**
- renewal, lapse, cancellation, refunds and their endpoints (AC-07, AC-08)
- `GET /api/admin/audit`
- the dashboard
- the sample-policy seed (P-11)
- Linux snapshot baselines (Sprint 5)
- accepted Sprint 2 minors 1–3: the Basic challenge, echoed unknown keys and the raw-path `instance`. The new endpoints
  follow the existing behaviour.

**Checkpoint decision G-1: an endorsement preview endpoint. The user must approve or veto it.**
- **The gap.** The endorsement spec's UI section says the Endorse page "previews the new premium and adjustment before
  confirming". app_spec §8 has no preview endpoint, and `frontend/CLAUDE.md` forbids money arithmetic in the browser.
  No spec-compliant preview is possible without a new read-only endpoint.
- **Planner default:** add `POST /api/policies/{number}/endorsements/preview` (contract C5). Because the spec comes
  first (root rule 2), the amendment below is committed as `spec: endorsement preview endpoint (plan G-1, approved at
  the Sprint 3 checkpoint)` before any preview code. It touches only `specs/endorsement_spec.md` and
  `specs/app_spec.md`.
- **Who commits it.** Approving this plan approves the amendment. The main session commits it at the checkpoint,
  before it launches the generators. If the commit is missing when TASK-002 starts, TASK-002 makes it as its first
  commit, using exactly the text below.
  - `endorsement_spec.md`, Request section, a new paragraph after the effective-date paragraph: "**Preview.**
    `POST /api/policies/{number}/endorsements/preview` (owner) takes the same body as the endorsement request and runs
    the same checks in the same order, but writes nothing. It returns 200 with `type`, `effectiveDate`,
    `currentAnnualPremium`, `newAnnualPremium`, `adjustment`, `unexpiredDays` and `termDays`. For a type that does not
    re-rate, `newAnnualPremium` equals the current premium and `adjustment` is `0.00`. The Endorse page calls it before
    confirming a sum-insured change."
  - app_spec §8: add the row `| POST | /api/policies/{number}/endorsements/preview | owner | endorsement |` after the
    endorsements row.
  - No new AC. Preview tests are titled with the AC whose numbers or rule they check (AC-06.2, AC-10.2).
- **If vetoed:** delete C5, remove every criterion that mentions the preview, and make the Endorse page show the new
  premium and adjustment from the confirmed endorsement's 201 response. The UI line then stays unmet and is recorded as
  an open issue.

**Planner calls. Veto any of these here.**

- **P-1 Decomposition and scheduling.** The exec workflow runs the parallel batch to completion, then the sequential
  tasks one at a time in listed order.
  - The parallel batch pairs the longest backend task with the frontend: TASK-001 (pure domain) and TASK-002
    (`frontend/` only).
  - The backend tasks then run one at a time (TASK-003 → TASK-004 → TASK-005), because they share one working tree, one
    `backend/target`, one Flyway sequence and one branch. TASK-003 depends on TASK-001 only for scheduling.
  - TASK-006 joins both lines.
  - Generators commit concurrently in the parallel batch. A `.git/index.lock` error means another generator is
    committing: retry the commit and never delete the lock.
- **P-2 Build flag.** TASK-001, TASK-003, TASK-004 and TASK-005 run Maven with `-Dskip.frontend=true`. A full build runs
  `npm ci`, which would delete `frontend/node_modules` while TASK-002 uses it. Only TASK-006 runs the full build.
- **P-3 TDD evidence shape (same as Sprint 2).** A red commit compiles (production stubs are allowed), and its new tests
  fail on an assertion, not a compile error. One red commit may name several sub-criteria. An ArchUnit existence check
  gets its red by failing while the class is absent.
- **P-4 Test names for rules without their own sub-criterion.** Root rule 8 requires `AC-NN.M` in every test name. The
  `endorsement-validator` skill's template tags only `AC-06`, which is not enough. Map each matrix row to the nearest
  sub-criterion:
  - E1 → AC-10.2
  - E2, E3 (effective-date window) → AC-06.6
  - E4 → AC-06.4
  - E5, E6, E7 (nominee rules) → AC-06.5
  - E8, E9 (sum insured) → AC-06.2
  - E10, E11 (address, valid request) → AC-06.1
  - issuance effective-date window tests → AC-05.1
- **P-5 Check order.**
  - **Issue:** request shape, date format and window (400), then application lookup and ownership (404), then state
    (409).
  - **Endorse and preview:** request shape (400, no `code`), then policy lookup and ownership (404), then state E1
    (409 invalid-policy-state, short-circuits), then the business rules E2–E10 (400, all collected).
  - A shape error means one of these: unknown keys, a missing or unknown `type`, missing payload keys, wrong JSON
    types, a malformed date or decimal, a non-integer `sharePercent`, or a blank or over-long name, line1 or city.
    Relationship and postcode-format errors are business codes (E7, E10). This keeps Sprint 2's shape-first order and
    the skill's "E1 short-circuits".
- **P-6 States that have no endpoint yet.** Sprint 4 adds cancellation and lapse, so this sprint has no way to reach
  them through the API. For AC-10.2 and AC-10.4, test setup moves a policy to CANCELLED, LAPSED or RENEWED through the
  same path the services use: the policy aggregate's transition method, backed by the state machine, plus an appended
  transition row. Setup never uses raw SQL or a test-only production endpoint.
  **AC-10.3 is proven at domain level only** (LAPSED → CANCELLED throws). Its API half needs the cancel endpoint and
  refund records: a LAPSED policy's cancel request gets 409 and writes no refund record. That half is a **Sprint 4
  carry-over**, which TASK-006 records in its progress file. `ac_audit` will show AC-10.3 as COVERED from the domain
  test.
- **P-7 How a customer issues a policy in the SPA.** The spec names no page for this, but `docs/architecture.md` §3
  shows "Issue policy" in the UI. For an APPROVED application, the Application Status page gets an Issue panel with an
  optional effective date. After issuing, it navigates to the policy detail.
- **P-8 Quote ownership details.**
  - The owner is recorded only when the valid credentials belong to a CUSTOMER. A quote from another role (for example
    `underwriter1`) is stored with no owner, as the spec's "valid customer credentials" wording suggests.
  - "Claiming" an anonymous quote is the application row itself (unique quote ID, U-3). The stored quote is never
    updated, because quotes are append-only.
  - The U-14 seed quote is created as owned by `customer2`.
  - The SPA attaches credentials to `POST /api/quotes` whenever someone is signed in. The SPA does not know roles, and
    the server decides ownership. Product calls and `GET /api/quotes/{id}` stay credential-free.
  - A 401 on quote creation shows a fixed "sign in again" message. The SPA never retries the request anonymously, which
    would defeat "invalid credentials never produce an anonymous quote".
- **P-9 Policy detail allow-list (KD-008).**
  - Version `riskInputs` return only the product's declared inputs minus health inputs (`bmi`, `healthDeclarations`),
    the same rule as NFR-04.2, because ADMIN also reads this view. The stored snapshot keeps the full inputs for
    re-rating.
  - Policy responses carry no KYC fields: no full name, date of birth, Aadhaar or PAN.
  - Endorsement payloads are returned as a per-type allow-listed `changes` object, never as the raw stored JSON.
  - `refunds` is always `[]` this sprint, and the Refunds tab shows an empty state.
- **P-10 Audit.**
  - U-12 says "each status change appends an audit entry". Issuance moves the application APPROVED → ISSUED, so it
    appends `APPLICATION_ISSUED` (entity type `APPLICATION`, previousStatus APPROVED, newStatus ISSUED, actor: the
    customer).
  - Policy status changes are recorded by the append-only `PolicyStatusTransition` rows (actor, reason, time). They add
    no AuditEntry rows: no spec rule asks for them, and service/CLAUDE.md requires audit only for underwriter, admin and
    product-manager actions.
- **P-11 Sample-policy seed deferred to Sprint 4.** app_spec §9 stages the sample policies across Sprints 3–4: at least
  2 per product, one near renewal and one past expiry within grace. Two of them need renewal state, so the whole seed
  is built once in Sprint 4, through the issuance and renewal services. Veto this to pull the 6 plain policies into
  TASK-004.
- **P-12 Deterministic E2E money.** The packaged app runs on the real clock, so E2E can't use AC-06.2's dates. The E2E
  journey issues a policy with today's effective date. It then changes the sum insured to 7000000.00, effective on the
  policy's term start date as read from the detail page. That gives new premium 9490.00 and adjustment exactly 2640.00
  (oracle), even if midnight passes during the test. The exact 1330.85 is proven by the domain test (TASK-001) and the
  fixed-clock API tests (TASK-005).
- **P-13 Endorsement semantics the spec leaves open.**
  - Version n+1 takes the endorsement's effective date and keeps the version's expiry date.
  - Term #1 is never changed by an endorsement. The adjustment is stored on the endorsement only.
  - Types that don't re-rate store adjustment `0.00` and keep the current annual premium.
  - For HOUSEHOLD, the building + contents total is checked against min/max. `SUM_INSURED_UNCHANGED` applies only
    when every supplied component equals its current value. At least one component is required.
  - `sharePercent` is an integer from 1 to 100. A total of exactly 100 is allowed.
  - Transition reasons: `ISSUANCE` for `null → ACTIVE`, and the endorsement type for `→ ENDORSED`.
  - The policy number's year is the issue date's year on the injected clock, not the effective date's year.
  - Backdating an endorsement within the current term is allowed: the spec forbids only dates outside the term and
    dates before the latest endorsement.
  - The UI offers all three endorsement types for every product and shows the server's errors, including
    `NOMINEE_NOT_SUPPORTED`. The Endorse link appears only for in-force statuses.
- **P-14 Effort overrides.** TASK-004 and TASK-005 are raised from `high` to `xhigh`. They hold the ownership 404s, the
  role boundary, concurrency (unique policy numbers, endorsement races) and atomicity. This is the same kind of code
  where the Sprint 1 security review found its defects (KD-007 to KD-010). If the orchestrator clamps sonnet to
  `high`, nothing else changes.

**Spec gaps. Not implemented; flagged for a future spec decision:**
- **SG-1.** A sum-insured endorsement does not re-run underwriting. A smoker could raise their sum insured above
  10,000,000 without the UW-TL-002 referral. The spec is silent, so nothing is added.
- **SG-2.** The spec allows backdated endorsements within the term (P-13).
- **SG-3.** The detail API's `refunds` field stays empty until Sprint 4.

**Cross-task contracts.** Money is a decimal string, as in Sprints 1 and 2. Dates are ISO `YYYY-MM-DD`. Instants are
ISO-8601.

- **C1. `POST /api/applications/{applicationId}/issue`** (CUSTOMER, owner, `application/json` only).
  - The body is `{}` or `{ "effectiveDate": "YYYY-MM-DD" }`. An absent date defaults to today. Any other key gets 400
    naming it.
  - A malformed date gets 400 naming `effectiveDate`. A date outside today to today+60 (inclusive) gets 400 naming
    `effectiveDate`, with `code` `EFFECTIVE_DATE_OUT_OF_RANGE`.
  - An unknown or malformed ID, or another customer's application, gets 404 with the fixed detail.
  - A non-APPROVED application, including an already ISSUED one, gets 409 `INVALID_APPLICATION_STATE`.
  - Success is **201** with a `PolicyHeader`: `{ policyNumber, applicationId, productCode, status, currentVersionNo,
    ruleVersion, effectiveDate, expiryDate, sumInsured, annualPremium, nextPremiumDueDate, endorsementCount }`.
- **C2. `GET /api/policies/mine`** (CUSTOMER) returns an array of exactly `{ policyNumber, productCode, status,
  sumInsured, annualPremium, nextPremiumDueDate, endorsementCount }` for the caller's policies, newest first by issue
  time.
  - `sumInsured` and `annualPremium` come from the current version.
  - `nextPremiumDueDate` is the current term end + 1 day.
- **C3. `GET /api/policies/{policyNumber}`** (owner CUSTOMER, or ADMIN).
  - The response is `{ policy: PolicyHeader, versions[], endorsements[], transitions[], terms[], refunds: [] }`.
  - **Version:** `{ versionNo, effectiveDate, expiryDate, sumInsured, annualPremium, ruleVersion, riskInputs (P-9),
    address: { line1, city, postcode }, nominees: [{ name, relationship, sharePercent }], createdAt }`, ascending by
    `versionNo`.
  - **Endorsement:** `{ sequenceNo, type, changes, beforeVersionNo, afterVersionNo, newAnnualPremium, adjustment,
    effectiveDate, actorId, createdAt }`, ascending by `sequenceNo`. `changes` is `{address}`, `{nominee}`,
    `{newSumInsured}` or `{newBuildingSumInsured?, newContentsSumInsured?}`.
  - **Transition:** `{ fromStatus (null for issuance), toStatus, reason, actorId, at }`, in append order.
  - **Term:** `{ termNo, startDate, endDate, premium, paid }`.
  - Access: another customer, an unknown number or a malformed number gets 404 with the fixed detail. An UNDERWRITER
    without ADMIN gets 403.
- **C4. `POST /api/policies/{policyNumber}/endorsements`** (CUSTOMER owner, `application/json` only).
  - The body is flat: `{ type, effectiveDate?, ...payload }`. The payload keys are exactly the spec table's: `address`
    `{line1, city, postcode}` for CHANGE_ADDRESS; `nominee` `{name, relationship, sharePercent}` for ADD_NOMINEE; and for
    CHANGE_SUM_INSURED, `newSumInsured` (TERM_LIFE, MOTOR) or `newBuildingSumInsured` and/or `newContentsSumInsured`
    (HOUSEHOLD).
  - Any key outside that type's set gets 400 naming it, and so does another product's sum-insured key.
  - Decimals are strings of up to 2 dp.
  - Success is **201** `{ endorsement: Endorsement (C3 shape), policy: PolicyHeader }`.
  - Errors follow C6. A lost race gets 409 `code` `POLICY_UPDATE_CONFLICT`, with a fixed detail and nothing written.
- **C5. `POST /api/policies/{policyNumber}/endorsements/preview`** (G-1). It takes the same role, body and checks as C4
  and writes nothing. It returns **200** `{ type, effectiveDate, currentAnnualPremium, newAnnualPremium, adjustment,
  unexpiredDays, termDays }`.
- **C6. Errors.** All errors are `application/problem+json` with a fixed `detail` that never echoes input.
  - **400:** carries `errors[]` of `{field, message}` with dotted paths (`effectiveDate`, `address.postcode`,
    `nominee.relationship`, `nominee.sharePercent`, `newSumInsured`, and so on). Business failures also carry a
    top-level `code`:
    - C1: `EFFECTIVE_DATE_OUT_OF_RANGE`.
    - C4 and C5: the skill's E2–E10 codes, which are `EFFECTIVE_DATE_OUT_OF_TERM`,
      `EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT`, `NOMINEE_NOT_SUPPORTED`, `NOMINEE_LIMIT_REACHED`,
      `NOMINEE_SHARE_EXCEEDED`, `NOMINEE_RELATIONSHIP_INVALID`, `SUM_INSURED_OUT_OF_RANGE`, `SUM_INSURED_UNCHANGED` and
      `POSTCODE_INVALID`. When several are collected, `code` is the first in E-order, and `errors[]` lists every one.
  - **409 `InvalidPolicyStateException`:** `type` `/problems/invalid-policy-state`, `code` `INVALID_POLICY_STATE`.
  - 401, 403, 404, 413 and 415 keep their current shapes.
- **C7. Records.**
  - Policy: number unique; one policy per application; status always equals the latest transition's `to`.
  - Version v1 is copied from the quote (sum insured, premium, risk inputs, rule version) and from the application's
    KYC address. It has no nominees, and its expiry is effectiveDate + `termMonths` − 1 day.
  - Term #1 runs from the effective date to the expiry date, with the premium and `paid = true`.
  - Transitions and reasons follow P-13. The `APPLICATION_ISSUED` audit entry follows P-10.
  - Endorsement: `sequenceNo` is unique per policy. `beforeVersionNo` is the current version and `afterVersionNo` is
    current + 1. The payload is stored normalised.
  - The actor is the authenticated username.
- **C8. Flyway.** V7 adds the quote owner (TASK-003). V8 adds the policy, version, term, transition and number-sequence
  tables (TASK-004). V9 adds the endorsement table (TASK-005). Never edit V1–V6 or any migration committed earlier in
  this sprint. Fix forward with the next free number.
- **C9. SPA.**
  - **Routes:** `#/policies` (My Policies), `#/policies/<policyNumber>` (detail) and
    `#/policies/<policyNumber>/endorse` (Endorse). All three sit behind the existing sign-in gate. The nav gains a
    "My Policies" link.
  - **Navigation:** issuing navigates to the new policy's detail page, and a confirmed endorsement navigates back to
    the detail page.
  - Credentials stay in memory only.
  - Page components take callbacks for navigation, and TASK-006 wires them into the shell.
- **C10. Quote ownership** follows `quote-engine_spec.md` (ownership paragraph), U-2 step 2 and P-8.
  `POST /api/quotes` and `GET /api/quotes/{id}` keep their response key sets unchanged, with no owner field.

**Execution notes for every generator.**
- **Maven.** Run `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`
  before any Maven command. Backend tasks add `-Dskip.frontend=true` (P-2).
- **Selective integration tests** run under failsafe (KD-009): `./mvnw verify -Dskip.frontend=true -Dit.test=<Pattern>
  -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`. Every backend task's final green run
  is a full `./mvnw verify -Dskip.frontend=true` with JaCoCo enabled.
- **KD-014.** Run every long command (Maven, `npm test`, Playwright and its webServer) in the **foreground**, with a
  timeout of at most 600000 ms. Split anything longer. Never end the turn while a command or task is pending.
- **Commits (from the sprint brief, same policy as Sprint 2).**
  - Commit live on `feat/issuance-endorsement`: `test(red): AC-NN.M …` with the failing tests before the
    implementation, then `feat(green): …`, then `refactor: …` when there is cleanup.
  - Stage explicit paths and commit with `git commit -m "…" -- <paths>`.
  - Never push, and never commit on `dev` or `main`. Retry on `index.lock` (P-1).
  - Save each task's red and green runs as `sprint-progress/<TASK>-red.log` and `<TASK>-green.log`.
  - Frontend green commits include a refreshed `frontend/coverage/lcov.info`.
- **Data.** Use synthetic data only. KYC: Aadhaar `XXXX-XXXX-1234`, PAN `ABCDE1234F`, the name "Asha Test", and
  postcode 411001. Nominee: "Ravi Nominee". Keep money as decimal strings and `BigDecimal`. Log IDs only (policy
  number, application ID, quote ID).
- **Test names.** JUnit tests use `@Tag("AC-NN")` plus `@DisplayName("AC-NN.M: …")`, or the NFR form. Vitest and
  Playwright titles start with `AC-NN.M:`. The `ac-tag-check` hook rejects untagged test files.
- **Tests and seeds.** Integration tests are seed-aware: `customer2` already has the U-14 application. Tests find rows
  by ID and never assume counts or positions.
- **Port.** Port 8080 must be free when a task ends. Stop any server you started.
- **Agents and skills:**

  | Task | Agents and skills |
  |---|---|
  | TASK-001 | `test-engineer` (red) with `spec-to-test-generator`; `policy-state-machine-generator` skill (AC-10.1 matrix); `endorsement-validator` skill (E1–E11, P-4 naming); `premium-calc-evaluator` skill (oracle cross-check) |
  | TASK-002 | `ui-designer` (Vitest first, then pages) |
  | TASK-003 | `test-engineer`; `migration-coherence-agent` (V7) |
  | TASK-004 | `test-engineer`; `migration-coherence-agent` (V8); `archtest-author` skill (NFR-02 existence checks) |
  | TASK-005 | `endorsement-agent`; `test-engineer`; `migration-coherence-agent` (V9); `archtest-author` skill (AC-06.7); `premium-calc-evaluator` skill |
  | TASK-006 | `ui-designer` and `test-engineer` (E2E and snapshots) |

  The Evaluator adds a Playwright MCP walkthrough: quote → apply → issue → My Policies → detail → change address →
  sum-insured preview and confirm. It checks the 375 px layout.

**Out-of-orchestrator scope. The user does these manually after the sprint completes:**
1. Run `/sprint-archive S3`. It archives this workspace into `sprint-contracts/` and `specs/reviews/`.
2. Run `/pr`. It runs the review gate (tests, the `security-reviewer` and `test-engineer` agents, AC coverage), writes
   `docs/prs/PR-NNN.md`, pushes the branch and opens a GitHub PR into `dev`. The user merges it on GitHub with "Create
   a merge commit".
3. Carry the AC-10.3 API test (P-6) into the Sprint 4 brief.

## Tasks

### TASK-001: Policy and endorsement domain rules: PolicyStatus, PolicyStateMachine, InvalidPolicyStateException, PolicyNumberFormat, issuance dates, EndorsementType, EndorsementValidator and EndorsementPremiumAdjuster
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Generates PolicyStatus (ACTIVE, ENDORSED, RENEWED, LAPSED, CANCELLED, where ACTIVE, ENDORSED and RENEWED are in force and LAPSED and CANCELLED are terminal), PolicyStateMachine and InvalidPolicyStateException(from, to), which extends PolicyForgeDomainException. Uses the policy-state-machine-generator skill to translate the spec table mechanically, with a null `from` meaning a new policy
  - Proves "AC-10.1: …" with one parameterised test that emits all 30 (from, to) pairs of the spec table. The 13 allowed cells return the target, and the 17 forbidden cells throw InvalidPolicyStateException carrying both states. Every case's display name starts with "AC-10.1:"
  - Proves "AC-10.3: …" at domain level: LAPSED → CANCELLED throws InvalidPolicyStateException (P-6, the API half is a Sprint 4 carry-over)
  - Implements PolicyNumberFormat: TERM_LIFE, MOTOR and HOUSEHOLD map to TL, MO and HH, with a 4-digit year and a 6-digit zero-padded sequence. An unknown product or a sequence outside 1–999999 raises a domain exception. Proves "AC-05.1: …": (TERM_LIFE, 2026, 1) gives `PF-TL-2026-000001`, which matches `^PF-TL-2026-\d{6}$`, and MOTOR and HOUSEHOLD give `PF-MO-` and `PF-HH-` numbers
  - Implements the pure issuance date rules, with today passed in as an argument. The effective date may be from today to today + 60 days inclusive, and the expiry date is effectiveDate + termMonths − 1 day. Proves "AC-05.1: …": 2026-11-01 with 12 months gives 2027-10-31. Today + 60 is accepted, and today − 1 and today + 61 are rejected naming `effectiveDate` with `EFFECTIVE_DATE_OUT_OF_RANGE`
  - Implements EndorsementType (CHANGE_ADDRESS, ADD_NOMINEE, CHANGE_SUM_INSURED, where only CHANGE_SUM_INSURED re-rates). Implements EndorsementValidator as a pure function over a policy snapshot: product, status, current term start and end, the latest endorsement's effective date, the current nominees, the current sum-insured components, the rule version's sum-insured limits, the request and today. It defaults a missing effective date to today
  - Applies the endorsement-validator skill's matrix E1–E11 with its exact codes and the P-13 rules. E1 (not in force) raises InvalidPolicyStateException through PolicyStateMachine to ENDORSED and short-circuits. E2–E10 are all collected, each naming its C6 dotted field with a fixed message that never contains the submitted value. A valid request (E11) returns a normalised command
  - Proves the matrix as a parameterised test with one row per E1–E11, each display-named with its P-4 sub-criterion: E1 is "AC-10.2: …" (for both CANCELLED and LAPSED), E2 and E3 are "AC-06.6: …", E4 is "AC-06.4: …", E5–E7 are "AC-06.5: …", E8 and E9 are "AC-06.2: …", and E10 and E11 are "AC-06.1: …". The fixtures are the skill's TERM_LIFE policy (2026-11-01..2027-10-31, SI 5,000,000.00, one nominee at 60%) and a MOTOR policy
  - Proves "AC-06.5: …" at the nominee boundary: with nominees totalling 80%, a 30% nominee gives `NOMINEE_SHARE_EXCEEDED` and a 20% nominee (total 100) is accepted. With three nominees, a fourth gives `NOMINEE_LIMIT_REACHED`
  - Implements EndorsementPremiumAdjuster: adjustment = (new − current) × unexpiredDays / termDays, where unexpiredDays = DAYS(effectiveDate, termEnd + 1) and termDays = DAYS(termStart, termEnd + 1). Intermediates use scale 10 and the final result is HALF_UP to 2 dp. It also builds the modified risk inputs per product: TERM_LIFE `sumInsured`, MOTOR `vehicleValue`, and HOUSEHOLD `buildingSumInsured` and `contentsSumInsured`
  - Proves "AC-06.2: …" against premium_check.py. Over 2026-11-01..2027-10-31, 6850.00 → 9490.00 effective 2027-05-01 gives unexpiredDays 184, termDays 365 and adjustment 1330.85. The reverse change gives −1330.85. A change effective on the term start gives 2640.00, and one effective on the term end gives 7.23
  - Proves "AC-06.2: …" by re-rating with the existing PremiumCalculator and the shipped TERM_LIFE v1 rules (no duplicated rating logic). Age 35, non-smoker, termYears 20 and SI 7,000,000.00 give 9490.00. MOTOR vehicleValue 900000.00 (vehicle age 2, driver 30, 0 claims, NCB 2, PRIVATE) gives 17025.00
  - Imports only `java.*` and domain classes, uses no double or float, and passes DomainPurityArchTest and MoneyPrecisionArchTest
  - Shows a `test(red): AC-…` commit with the failing tests (stubs allowed) before the `feat(green)` commit. Saves `sprint-progress/TASK-001-red.log` and `TASK-001-green.log` naming AC-05.1, AC-06.1, AC-06.2, AC-06.4, AC-06.5, AC-06.6, AC-10.1, AC-10.2 and AC-10.3
  - Passes `./mvnw verify -Dskip.frontend=true` with ArchUnit green and JaCoCo at or above 80% domain and 70% overall

### TASK-002: Frontend pages: My Policies, Policy Detail and Endorse, the Issue action on Application Status, signed-in quote credentials, typed API client and Vitest
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Confirms before its first commit that the G-1 `spec:` commit exists in `819a246..HEAD`. If it doesn't, makes that commit first, with exactly the amendment text in the plan's G-1 section, touching only `specs/endorsement_spec.md` and `specs/app_spec.md`. If G-1 was vetoed at the checkpoint, skips this criterion and every preview criterion
  - Adds typed API client functions whose request and response types match contracts C1–C5 field for field (issue, my policies, policy detail, endorse, preview). Parses C6 problems, including `errors[]`, `code` and the invalid-policy-state `type`
  - Attaches the signed-in user's Basic `Authorization` header to `POST /api/quotes` when a session exists and sends none when signed out (AC-01.13, P-8). Product calls and `GET /api/quotes/{id}` stay credential-free even when signed in. A 401 on quote creation shows a fixed "sign in again" message and is never retried anonymously
  - Adds an Issue panel to the Application Status page for APPROVED applications only. It has an optional effective-date input and posts exactly `{}` when the input is blank, or `{ "effectiveDate": … }`. On 201 it calls a navigation callback with the policy number. It shows a 409 as a fixed message and a 400 `effectiveDate` error at that field. An ISSUED application shows an "Issued" state with a link to My Policies
  - Renders a My Policies page from C2 in API order, with an empty state. Each card's test ID contains its policy number and shows the product, a status badge, sum insured, premium, next premium due date and endorsement count, verbatim, with a link to the detail page
  - Renders a Policy Detail page from C3. The header shows the number, a status badge, the product, the effective and expiry dates, sum insured, premium and next due date. Tabs show Versions (every version with sum insured, premium, address and nominees), Endorsements (sequenceNo, type, before → after version and adjustment, in API order), Status (each transition, with a null `fromStatus` shown as a dash) and Refunds (an empty state). A 404 shows a fixed not-found message. The Endorse link appears only for ACTIVE, ENDORSED and RENEWED (P-13)
  - Renders an Endorse page with a type selector offering all three types and a dynamic form for each. CHANGE_ADDRESS has line1, city and postcode. ADD_NOMINEE has name, a relationship select with the five spec values, and sharePercent. CHANGE_SUM_INSURED shows `newSumInsured` for TERM_LIFE and MOTOR, or the building and contents fields for HOUSEHOLD. Every type has an optional effective date. Confirm posts exactly the C4 body: `type`, that type's payload keys and `effectiveDate` only when given
  - Requires a successful C5 preview for the current inputs before Confirm is enabled on a sum-insured change. The page shows the current premium, the new premium and the adjustment exactly as received, and editing any input clears the preview
  - Shows each `errors[]` entry next to the input whose dotted path matches its `field`. Shows a fixed message for every C6 business code, for 409 `INVALID_POLICY_STATE` and for 409 `POLICY_UPDATE_CONFLICT`. Client-side checks cover required fields only
  - Does no money or share arithmetic in the browser, renders API strings verbatim, and keeps `noMoneyArithmetic.test.ts` passing
  - Puts a `data-testid` on every input, button, tab, status badge, policy card, version, endorsement and transition row, and preview value. Lays out with CSS grid/flex and the single 640px breakpoint, with no horizontal scrolling at 375px width
  - Includes Vitest tests titled "AC-01.13: …" (signed-in quote carries Basic, signed-out carries none, 401 shows the fixed message), "AC-05.1: …" (issue body and callback; My Policies renders every C2 field), "AC-05.3: …" (409 shown), "AC-06.1: …" (address body; Versions tab shows v1 and v2 addresses), "AC-06.2: …" (preview shows 9490.00 and 1330.85 verbatim; confirm body; edit clears preview), "AC-06.4: …" (NOMINEE_NOT_SUPPORTED shown), "AC-06.5: …" (NOMINEE_SHARE_EXCEEDED shown), "AC-06.6: …" (Endorsements tab lists sequence 1, 2, 3 with versions 2, 3, 4), "AC-10.2: …" (409 invalid-policy-state shown; no Endorse link on CANCELLED) and "AC-10.4: …" (Status tab lists the transitions in API order and the badge equals the policy status)
  - Leaves the app shell routing unchanged (TASK-006 mounts the new pages) and keeps every existing AC-01, AC-03, AC-04 and AC-09 Vitest test passing. Touches nothing outside `frontend/`, except the G-1 spec fallback, and adds no npm dependencies
  - Shows a `test(red): AC-…` commit with the failing Vitest tests before the `feat(green)` commit. Saves `sprint-progress/TASK-002-red.log` and `TASK-002-green.log` naming every AC above
  - Passes `npm test` (lcov refreshed) and `npm run build` with no TypeScript errors

### TASK-003: Quote ownership: owner recorded on signed-in quotes, owner-only apply, anonymous claim
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-001]
- **acceptance_criteria**:
  - Adds Flyway V7 with a nullable quote-owner column and leaves V1–V6 byte-identical. The owner is set only when the quote is inserted and is never updated, including when an anonymous quote is claimed (P-8)
  - Keeps `POST /api/quotes` public, with no change to the explicit public matchers. It stores the caller as owner when the request carries valid CUSTOMER credentials. It stores no owner when the request has no credentials or valid credentials of another role. It returns 401 and stores nothing when the credentials are invalid
  - Proves "AC-01.12: …": customer1's quote is stored with owner customer1, and an anonymous quote has no owner. In both cases the `POST /api/quotes` response and `GET /api/quotes/{id}` have no owner field and the same key set as before this sprint. It also proves that underwriter1's quote is stored with no owner, and that invalid credentials get 401 with no quote row added
  - Applies U-2 step 2 in order. An owned quote submitted by a different customer gets 400 `QUOTE_NOT_FOUND` naming `quoteId`, before the expiry, already-applied and KYC-age checks
  - Proves "AC-03.9: …":
    - customer2 applying for customer1's quote gets 400 `QUOTE_NOT_FOUND` naming `quoteId`, with a body JSON-equal to the unknown-quote body (AC-03.6). No application, decision or audit row is stored
    - customer1 applying for the same quote gets 201
    - for an anonymous quote, customer1's application claims it, and customer2 then gets 409 `QUOTE_ALREADY_APPLIED` with one application still stored
    - customer2 applying for customer1's expired quote gets `QUOTE_NOT_FOUND`, not `QUOTE_EXPIRED`
  - Seeds the U-14 quote as owned by customer2 (P-8). Keeps the AC-09.7 seed test and seed idempotency passing
  - Keeps every existing AC-01, AC-03, AC-04 and AC-09 test passing. Re-proves "NFR-04.1: …": `POST /api/quotes` and `GET /api/quotes/{id}` without credentials do not return 401
  - Logs quote and application IDs only, and passes every ArchUnit test
  - Shows a `test(red): AC-…` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-003-red.log` and `TASK-003-green.log` naming AC-01.12, AC-03.9 and NFR-04.1
  - Passes `./mvnw verify -Dskip.frontend=true`

### TASK-004: Policy issuance API: policy persistence, PolicyNumberGenerator, POST /api/applications/{id}/issue, GET /api/policies/mine and GET /api/policies/{number}
> Effort override: high → xhigh. This task owns concurrency-safe unique numbering, ownership 404s, the role boundary and allow-listed policy responses. This is where the Sprint 1 security defects occurred (P-14).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-001, TASK-003]
- **acceptance_criteria**:
  - Adds Flyway V8 (C8) and leaves V1–V7 byte-identical. V8 creates:
    - a policy table with a unique number, a unique application, product, customer, status, currentVersionNo, an optimistic-lock version and the issue time
    - an append-only policy_version table, unique on policy and version number, holding sum insured, premium, risk inputs, address, nominees, rule version, effective and expiry dates and the creation time
    - a policy_term table, unique on policy and term number, holding start, end, premium and paid
    - an append-only policy_status_transition table with a nullable from, a to, reason, actor and time
    - a per-product, per-year number-sequence store

    No column uses a floating-point type
  - Maps PolicyVersionEntity and PolicyStatusTransitionEntity as immutable entities with no setters, whose repositories have only `save` and finders. PolicyEntity changes status and currentVersionNo only through one intention-revealing method that applies PolicyStateMachine. PolicyTermEntity changes only through `markPaid`, and neither aggregate has public setters. AppendOnlyArchTest gains NFR-02 existence checks for both entities and both repositories, which fail in the red commit
  - Implements PolicyNumberGenerator in the service layer. Inside the issuing transaction it allocates the next sequence for (product, issue year), using the issue date's year on the injected clock (P-13), and formats the number with PolicyNumberFormat
  - Exposes `POST /api/applications/{applicationId}/issue` for CUSTOMER only, consuming `application/json` only, with the C1 body, response and P-5 check order. In one transaction it moves the application from APPROVED to ISSUED and writes the C7 records: a Policy (ACTIVE, version 1), PolicyVersion v1, PolicyTerm #1 (paid), the `null → ACTIVE` transition (reason ISSUANCE, actor: the customer) and the `APPLICATION_ISSUED` audit entry (P-10)
  - Proves "AC-05.1: …" on a fixed clock at 2026-11-01. An APPROVED TERM_LIFE application (AC-04.1 profile) issued with effectiveDate 2026-11-01 gets 201, a number matching `^PF-TL-2026-\d{6}$`, effective date 2026-11-01, expiry 2027-10-31, and sumInsured 5000000.00 and annualPremium 6850.00 equal to its quote's. The ruleVersion equals the quote's and the status is ACTIVE
  - Proves the AC-05.1 date and product cases. An omitted effectiveDate defaults to today. Today − 1 and today + 61 get 400 naming `effectiveDate`, with nothing stored, and today + 60 is accepted. MOTOR and HOUSEHOLD policies get `PF-MO-` and `PF-HH-` numbers
  - Proves "AC-05.2: …":
    - 50 APPROVED applications issued concurrently on a fixed pool of 8 threads all return 201 with 50 distinct numbers
    - 5 issued one after another have contiguous sequence numbers n to n+4
    - TERM_LIFE and MOTOR issued alternately stay contiguous within each product
    - the first issue on a clock year no other test uses gets sequence 000001
  - Proves "AC-05.3: …":
    - REFERRED and DECLINED applications get 409 `INVALID_APPLICATION_STATE`, and nothing is stored
    - a second issue of an issued application gets 409, and the application still has one policy
    - two concurrent issues of the same application give exactly one 201 and one 409
  - Proves "AC-05.4: …": an issued policy has exactly one transition (`null → ACTIVE`) and exactly one PolicyVersion (v1), and its status equals that transition's `to`
  - Returns 404 with the fixed detail for another customer's, an unknown or a malformed application ID, and stores nothing
  - Exposes `GET /api/policies/mine` (CUSTOMER) per C2, with only the caller's policies, newest first. Exposes `GET /api/policies/{policyNumber}` (owner CUSTOMER or ADMIN) per C3 and the P-9 allow-list: no health inputs, no KYC fields and `refunds: []`. Proves in "AC-05.1: …" tests that nextPremiumDueDate is 2027-11-01 and endorsementCount is 0. Another customer and an unknown number get 404, and UNDERWRITER gets 403
  - Proves "NFR-04.1: …" by extending the parameterised role-boundary test:
    - unauthenticated requests to all three endpoints get 401
    - underwriter1 gets 403 on issue, mine and detail
    - admin1 gets 403 on issue and mine, and 200 on another customer's policy detail
  - Proves "NFR-04.3: …": a `text/plain` issue request gets 415 and stores nothing. Proves "NFR-04.4: …": a body over 16 KB gets 413 and stores nothing, including through a percent-encoded variant of the issue path
  - Proves "NFR-03.1: …" by extending the log capture to issuance and both GETs, with our packages at DEBUG. No captured line contains the KYC full name, date of birth, Aadhaar, PAN, health declaration text or address line. PII-carrying types never print PII in their string form
  - Keeps controllers free of business logic and repository injection, and passes every ArchUnit test
  - Shows a `test(red): AC-…` / `NFR-…` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-004-red.log` and `TASK-004-green.log` naming AC-05.1–05.4, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4
  - Passes `./mvnw verify -Dskip.frontend=true`

### TASK-005: Endorsement API: append-only endorsements, atomic version + transition, sum-insured re-rating, preview, state errors and history
> Effort override: high → xhigh. This task owns transactional atomicity, the invalid-state boundary, re-rating money and concurrent-endorsement integrity (P-14).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-004]
- **acceptance_criteria**:
  - Adds Flyway V9 (C8) with an endorsement table and leaves V1–V8 byte-identical. The table is unique on policy and sequenceNo, and holds the type, the normalised payload, the before and after versions, newAnnualPremium and adjustment as NUMERIC(19,2), the effective date, the actor and the creation time. No column uses a floating-point type
  - Maps EndorsementEntity as an immutable entity with no setters, whose repository has only `save` and finders. AppendOnlyArchTest gains an "AC-06.7: …" existence check for the entity and repository, which fails in the red commit
  - Exposes `POST /api/policies/{policyNumber}/endorsements` for the owning CUSTOMER, consuming `application/json` only, with the C4 body and response and the P-5 check order. Delegates every rule to EndorsementValidator, re-rates with PremiumCalculator using the policy's own rule version, and computes the adjustment with EndorsementPremiumAdjuster
  - Writes everything in one transactional method:
    - appends the Endorsement (sequence n+1, before version = current, after version = current + 1)
    - appends PolicyVersion n+1 per P-13
    - sets currentVersionNo and appends the `→ ENDORSED` transition through PolicyStateMachine (reason: the type, actor: the customer)

    Two concurrent endorsements on one policy never produce duplicate sequence or version numbers. The loser gets 409 `POLICY_UPDATE_CONFLICT` with nothing written
  - Maps InvalidPolicyStateException to a 409 problem+json with type `/problems/invalid-policy-state`, code `INVALID_POLICY_STATE` and a fixed detail that never echoes input
  - Proves "AC-06.1: …": a CHANGE_ADDRESS on an ACTIVE policy gives one endorsement with before version 1 and after version 2. Version 2 has the new address, version 1 still has the old address, and the status is ENDORSED
  - Proves "AC-06.2: …" on a fixed clock. On a TERM_LIFE policy (6850.00, 2026-11-01..2027-10-31), SI 5,000,000.00 → 7,000,000.00 effective 2027-05-01 stores newAnnualPremium 9490.00 and adjustment 1330.85. Version 2 has SI 7000000.00 and premium 9490.00. The result is still 9490.00 and 1330.85 while a newer TERM_LIFE rule-version fixture is active in an isolated context
  - Proves "AC-06.2: …" for the other products. MOTOR replaces vehicleValue (800000.00 → 900000.00 re-rates 15150.00 → 17025.00), and HOUSEHOLD replaces the supplied components. Each value matches premium_check.py
  - Proves "AC-06.3: …". Test configuration injects a failure at the version write, after the endorsement insert has reached the database. Afterwards no endorsement row and no version 2 exist, and the status, currentVersionNo and transition count are unchanged. Production code contains no test-only hook
  - Proves "AC-06.4: …": ADD_NOMINEE on a MOTOR policy gets 400 `NOMINEE_NOT_SUPPORTED`, and nothing is written
  - Proves "AC-06.5: …": with nominees totalling 80% from two earlier ADD_NOMINEE endorsements, adding 30% gets 400 `NOMINEE_SHARE_EXCEEDED` and nothing is written. A fourth nominee gets `NOMINEE_LIMIT_REACHED`
  - Proves "AC-06.6: …":
    - three endorsements get sequenceNo 1, 2 and 3
    - the detail API returns them in order, linked to before versions 1, 2, 3 and after versions 2, 3, 4, with versions 1 to 4 listed
    - an effective date before the latest endorsement's gets 400 `EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT`
    - a date outside the term gets `EFFECTIVE_DATE_OUT_OF_TERM`
  - Proves "AC-06.7: …":
    - the ArchUnit append-only rules cover EndorsementEntity and PolicyVersionEntity
    - no PUT, PATCH or DELETE handler mapping exists for any `/api` path
    - owner PUT, PATCH and DELETE requests to the endorsement paths do not succeed (405 or 404) and change nothing
  - Proves "AC-10.2: …": with a CANCELLED policy set up as P-6 describes, the endorsement and preview requests get 409 `invalid-policy-state`, and no endorsement, version or transition row is written. A LAPSED policy gets the same 409
  - Proves "AC-10.4: …" over several sequences: issue plus 0 to 4 mixed endorsements, plus sequences that add RENEWED and then LAPSED or CANCELLED through P-6 setup. After each sequence the policy status equals the last transition's `to`, and the transition count equals the operations + 1. Operations rejected after a terminal state change no counts
  - Exposes the C5 preview endpoint (G-1) with the same role, consumes and checks as C4, and writes nothing (all row counts unchanged). Proves "AC-06.2: …": the preview returns 9490.00, 1330.85, 184 and 365, and confirming the same request stores the same values
  - Extends the C3 detail with endorsements (`changes` allow-listed per type) and the nominees and address of each version. Extends C2 and PolicyHeader so that endorsementCount counts the endorsements and premium and sum insured come from the current version
  - Proves "NFR-04.1: …": unauthenticated requests to endorse and preview get 401, while underwriter1 and admin1 get 403. Another customer and an unknown policy get 404
  - Proves "NFR-04.3: …": `text/plain` to endorse and preview gets 415 and changes nothing. Proves "NFR-04.4: …": a body over 16 KB gets 413 and changes nothing, including through a percent-encoded variant of the endorsements path
  - Proves "NFR-03.1: …" by extending the log capture to all three endorsement types and the preview. No captured line contains the nominee name, an address line, the KYC values or health declaration text
  - Confirms that the G-1 `spec:` commit precedes this task's first preview commit (skip if G-1 was vetoed). Keeps controllers thin and passes every ArchUnit test
  - Shows a `test(red): AC-…` / `NFR-…` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-005-red.log` and `TASK-005-green.log` naming AC-06.1–06.7, AC-10.2, AC-10.4, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4
  - Passes `./mvnw verify -Dskip.frontend=true`

### TASK-006: App shell routes for the policy pages, Playwright journeys (desktop + mobile), committed visual snapshots and sprint-wide verification
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-002, TASK-005]
- **acceptance_criteria**:
  - Mounts My Policies, Policy Detail and Endorse at the C9 routes behind the sign-in gate and adds a "My Policies" nav link. Issuing from the Application Status page navigates to `#/policies/<policyNumber>`, the detail page's Endorse link opens `#/policies/<policyNumber>/endorse`, and a confirmed endorsement returns to the detail page
  - Includes Vitest tests titled "AC-05.1: …" (`#/policies` renders My Policies, and `#/policies/<number>` renders that policy's detail) and "AC-06.1: …" (`#/policies/<number>/endorse` renders the Endorse page for that policy). Keeps every existing routing test passing
  - Resolves any mismatch between the SPA and the API in favour of the specs and contracts C1–C10, and lists each change in the progress file
  - Adds `policy-lifecycle.spec.ts` with these Playwright tests:
    - "AC-05.1: …": customer1 quotes the AC-04.1 profile, applies and issues, then sees an ACTIVE policy whose number matches `^PF-TL-\d{4}-\d{6}$`, with premium 6850.00 and SI 5000000.00 in My Policies and on the detail page
    - "AC-06.1: …": an address change shows ENDORSED, and the Versions tab shows v1 with the old address and v2 with the new one
    - "AC-06.2: …": changing SI to 7000000.00 effective on the term start previews 9490.00 and 2640.00, and after confirming, the Endorsements tab shows adjustment 2640.00 (P-12)
    - "AC-06.5: …": nominees of 80% then 30% show the share-exceeded message, and no endorsement is added
    - "AC-06.6: …": three endorsements show sequence 1, 2 and 3, linked to versions 2, 3 and 4
    - "AC-01.13: …": a signed-in customer's `POST /api/quotes` carries an Authorization header, and a signed-out one does not
  - Uses `data-testid` selectors and web-first assertions, with no `waitForTimeout`. Each test creates its own quote, application and policy, and acts on them by ID, as in Sprint 2's P-8. Every test passes on `desktop-chromium` and `mobile-chromium` running in parallel, and the Sprint 1 and Sprint 2 journeys still pass
  - Adds visual snapshot assertions for at least the My Policies list, the Policy Detail Versions tab and the Endorse page with a sum-insured preview, with policy numbers, dates and timestamps masked. Baselines are committed under `frontend/e2e/__snapshots__/`, platform-suffixed, and a second consecutive run passes without updating snapshots
  - Passes a full `./mvnw verify` (SPA packaged, frontend not skipped) with the JaCoCo thresholds unchanged at 80% domain and 70% overall. Also passes `npm test` (lcov refreshed and committed) and `npm run build`
  - Reports COVERED (not PARENT-ONLY or MISSING) in `python scripts/ac_audit.py --offline --no-write` for AC-01.12, AC-01.13, AC-03.9, AC-05.1–05.4, AC-06.1–06.7, AC-10.1–10.4, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4, and still for every Sprint 1 and Sprint 2 ID
  - Confirms from `git log 819a246..feat/issuance-endorsement` that every ID in the previous criterion has a `test(red)` commit naming it before a `feat(green)` commit, and that the G-1 `spec:` commit precedes every preview commit
  - Confirms that `dev` is still `246e7cb` and `main` is still `8e265d6`, locally and in `git ls-remote origin`, and that `origin/feat/issuance-endorsement` does not exist (nothing pushed)
  - Confirms that `python scripts/redact_evidence.py --check` exits 0 over every `sprint-progress/` log
  - Records in the progress file every command run and its result (verify, npm test, build, Playwright on both projects twice, the AC audit, the git checks). Also records the AC-10.3 API carry-over for Sprint 4 (P-6) and the spec gaps SG-1 to SG-3. Leaves port 8080 free
  - Shows a `test(red): AC-…` commit with the E2E and routing tests failing before the shell routes exist, then the `feat(green)` commit. Saves `sprint-progress/TASK-006-red.log` and `TASK-006-green.log` showing both Playwright projects

## Execution Schedule

### Parallel Batch (no dependencies — run simultaneously via Agent Teams)
- TASK-001
- TASK-002

### Sequential Tasks (ordered by dependency)
1. TASK-003 (depends on TASK-001)
2. TASK-004 (depends on TASK-001, TASK-003)
3. TASK-005 (depends on TASK-004)
4. TASK-006 (depends on TASK-002, TASK-005)
