> Archived from `.sprint/20261103-223107/` on 2026-11-04 · branch `feat/renewal-cancellation` · harness agent-harness 2.8.0. Content below is unchanged from the harness artifact.

# Sprint Plan

## Spec
specs/renewal-cancellation_spec.md specs/portfolio-dashboard_spec.md specs/policy-issuance_spec.md — PolicyForge Sprint 4 (branch feat/renewal-cancellation, cut from dev @ 12f95c3). Scope: renewal cycle with idempotent offers on the currently active rule version, owner pay → RENEWED, grace-period lapse per product (AC-07.1–07.6); cancellation with pro-rata refund, cooling-off, fee floor at 0.00, append-only RefundRecord and refund preview (AC-08.1–08.5, AC-08.1 = 4473.29 per premium_check.py); AC-10.3 API half (LAPSED → cancel gives 409, no refund; Sprint 3 carry-over P-6); admin portfolio dashboard API + page (AC-11.1–11.5); synthetic sample-policy seed per app_spec §9 (≥2 policies per product for customer1/customer2, 1 near renewal, 1 past expiry within grace), which AC-11.1 depends on (Sprint 3 carry-over P-11). Frontend: Renew and Cancel actions on Policy Detail (renewal offer + Pay; refund preview then confirm; Refunds tab populated), Admin Dashboard page (KPI tiles, per-product table, renewal pipeline, lapse forecast; tiles wrap under 640px), Playwright journeys (desktop + mobile) with committed visual snapshots. Carry-over security minors from the Sprint 2/3 reviews (specs/reviews/security-feat-issuance-endorsement-20261103.md): suppress the server Basic challenge (WWW-Authenticate) for SPA/API 401s so browsers never cache credentials; a fixed or sanitised problem `instance` instead of the raw request path; one shared, length- and character-bounded sanitiser for echoed unknown keys; a fixed 409/503-style problem (not a generic 500) when a policy-number sequence is exhausted. Spec gap SG-1 (sum-insured endorsements do not re-underwrite) — the planner should propose a decision for the checkpoint, not implement silently. Any spec gaps or ambiguities (e.g. AC-07.3 needs a TERM_LIFE v2 rule set: published file vs isolated test fixture; seed date relative to today vs fixed; renewal pay amount) must be listed as G-n items for the checkpoint; spec amendments go in a separate `spec:` commit before code. Constraints: root CLAUDE.md non-negotiables and folder CLAUDE.md files, incl. web/CLAUDE.md rules KD-007..KD-010 (JSON-only consumes, allow-list responses, explicit public matchers, fixed problem detail, default-deny body limit on the routed path); money BigDecimal (scale 10 intermediate, HALF_UP 2 dp) and refund/premium values must equal premium_check.py oracle; append-only terms-paid history, refund records and transitions; never edit an existing Flyway migration or published rule file; every test named AC-NN.M. Commits: generators commit locally on feat/renewal-cancellation only, TDD order (`test(red)` before `feat(green)`), explicit paths, never push (same policy as Sprints 2–3). Hard rule KD-014: run long commands (mvnw verify, npm test, Playwright webServer) in the FOREGROUND, never background-and-wait. Environment: prefix Maven with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`. Use project agents where they fit (test-engineer for red, renewal-orchestrator-agent, premium-calc-evaluator skill, ui-designer; evaluator uses Playwright MCP).

### Interpretation (read this at the plan checkpoint)

**How to read this plan.** The specs are the source of truth: `renewal-cancellation_spec.md` (AC-07, AC-08),
`portfolio-dashboard_spec.md` (AC-11), `policy-issuance_spec.md` (AC-10.3, AC-10.4) and app_spec §8 and §9. This plan
cites them and does not restate them. **G-1 to G-6** are the decisions the user makes at this checkpoint. Each has a
planner default, and approving the plan approves every default. G-3 to G-6 change spec text, so the exact amendment is
given below. **P-1 to P-15** are planner calls on what the spec leaves open. Veto any of them here. **SG-1 to SG-7**
are spec gaps this sprint records but does not fill. Contracts **C1 to C10** fix the wire shapes, so the frontend task
runs in parallel and every task can start cold. If a criterion seems to disagree with a spec rule, the spec wins and
the criterion is a plan defect.

**Readiness check (planner, 2026-11-04).**
- `feat/renewal-cancellation` is at `12f95c3`, with a clean tree, equal to `dev`.
- `dev` = `origin/dev` = `12f95c3`, and `main` = `origin/main` = `8e265d6` (checked with `git ls-remote`). No
  `origin/feat/renewal-cancellation` exists.
- Flyway is at V9. No PUT, PATCH or DELETE mapping and no scheduled method exists in `backend/src/main`.
- `premium_check.py spec-examples` passes every case: AC-08.1 gives 4473.29, AC-08.2 gives 6850.00 and AC-08.3 gives
  0.00. The oracle also gives these values, which this plan pins as extra cases:
  - TERM_LIFE cancelled on 2026-11-16 (15 days, just outside cooling-off) gives 6068.49.
  - MOTOR 15150.00 cancelled on 2027-02-09 with fee 250.00 gives 10749.32. MOTOR cancelled on 2026-11-08 (day 7, cooling-off 7 does not apply) gives 14609.45.
  - HOUSEHOLD 11350.00 cancelled on 2027-02-09 with fee 200.00 gives 8040.41.
  - A premium basis of 8180.85 (6850.00 + 1330.85) cancelled on 2027-06-01 gives 2929.23 (153/365).
  - A 366-day renewal term 2027-11-01..2028-10-31 at 6850.00, cancelled on 2027-11-03, gives 6312.57 (no cooling-off). The same at 7400.00 gives 6859.56.
  - Quotes: TERM_LIFE age 35, non-smoker, SI 12,000,000 gives 16090.00. A smoker with SI 5,000,000 gives 10150.00, and a smoker with SI 12,000,000 gives 24010.00. MOTOR (AC-01.2 profile) gives 15150.00 and HOUSEHOLD (AC-01.3 profile) gives 11350.00.
  - The TERM_LIFE v2 fixture of G-1 (band 31–40 factor 1.30) gives 7400.00 for the AC-04.1 profile. This was computed with the oracle's rounding rules; no step rounds.
  - Sums: 6850.00 + 6850.00 + 1330.85 − 4473.29 = 10557.56, and 13700.00 + 30300.00 + 22700.00 = 66700.00.
- `ac_audit.py --offline` covers 65 of 81 IDs. Exactly AC-07.1–07.6, AC-08.1–08.5 and AC-11.1–11.5 are NOT COVERED.
- Existing tests that pin behaviour this sprint changes on purpose. No spec AC pins any of them, and each change is named
  in TASK-003:
  - the `WWW-Authenticate: Basic` assertions in SecurityBaselineIT (line 90), RoleBoundaryIT (line 136) and
    EndorsementSecurityIT (line 62)
  - the 100-character unknown-key echo in PolicyIssuanceIT (line 195)
- **A latent defect that renewal exposes.** EndorsementService and PolicyQueryService take the highest-numbered term,
  paid or not, as the current term. A renewal offer (an unpaid term 2) would therefore move the endorsement window,
  the adjustment basis and the header dates to the unpaid term. TASK-004 fixes this under G-5.
- `policy_term` has no rule-version column, but AC-07.1 and AC-07.3 require "the renewal term records that version".
  This needs V10 (C8).

**Sprint base commit: `12f95c3`.** All git-history checks count commits after it.

**Primary deliverable.** The time-driven half of the policy lifecycle, plus the admin's view of the portfolio:
- A renewal cycle (end-of-day schedule and an ADMIN trigger) offers a renewal term at the active rule version's
  premium. It is idempotent per date. A pending renewal that is unpaid past grace lapses the policy. The owner pays the
  offer and the policy becomes RENEWED.
- The owner previews a pro-rata refund, with cooling-off and the fee floor, then cancels. One append-only refund record
  and the CANCELLED transition are written in one transaction. A LAPSED policy cannot be cancelled.
- An ADMIN dashboard shows active counts, exact premium collected, the renewal pipeline and the lapse forecast.
- A synthetic sample-policy seed makes the dashboard and the Renew action meaningful on a fresh start.
- Four carried-over security minors are closed, and the SG-1 underwriting bypass is decided (G-6).
- The SPA gains Renew and Cancel panels on Policy Detail, a populated Refunds tab and an Admin Dashboard page. These are
  tested with Vitest, with Playwright on desktop and mobile, and with committed snapshots.

**Who depends on it.** Sprint 5 (CI, Linux baselines, release) ships this as the feature-complete v1. The dashboard
is the last AC-bearing feature. NFR-02 is judged on the append-only refund records and transitions added here.

**Done looks like:**
- A full `./mvnw verify` (SPA packaged) is green, with ArchUnit passing and the JaCoCo thresholds unchanged (80%
  domain, 70% overall).
- `npm test` (lcov committed) and `npm run build` are green.
- The Playwright journeys pass on `desktop-chromium` and `mobile-chromium` twice in a row with no snapshot drift.
  Baselines are committed under `frontend/e2e/__snapshots__/`.
- `python scripts/ac_audit.py --offline --no-write` reports every ID in the Scope table as COVERED, plus every Sprint
  1–3 ID. That is 81 of 81, or 82 of 82 with AC-06.8 under G-6.
- After `12f95c3`, the amendment `spec:` commit comes before any code commit. Every Scope ID also has a `test(red)`
  commit naming it before a `feat(green)` commit.
- Every `sprint-progress/` log passes `scripts/redact_evidence.py --check` (KD-011).
- Nothing is pushed: `dev` and `main` are unchanged locally and on `origin`.

**Scope.** Each ID below is owned by at least one task:

| ID | Owned by |
|---|---|
| AC-07.1, AC-07.5 | TASK-001 (domain), TASK-004 (API), TASK-002 (Vitest), TASK-007 (E2E) |
| AC-07.2, AC-07.4, AC-07.6 | TASK-001, TASK-004 |
| AC-07.3 | TASK-004 |
| AC-08.1, AC-08.3 | TASK-001, TASK-005, TASK-002 |
| AC-08.2 | TASK-001, TASK-005, TASK-002, TASK-007 |
| AC-08.4 | TASK-005, TASK-002, TASK-007 |
| AC-08.5 | TASK-001 |
| AC-10.2 | TASK-005 (re-proved with a really cancelled policy) |
| AC-10.3 | TASK-005 (API half, Sprint 3 carry-over P-6), TASK-002 |
| AC-10.4 | TASK-004, TASK-005 (sequences with real pay, lapse and cancel) |
| AC-11.1 | TASK-006 (seed and API), TASK-002, TASK-007 |
| AC-11.2 | TASK-001, TASK-006, TASK-002 |
| AC-11.3, AC-11.4 | TASK-001, TASK-006, TASK-002, TASK-007 |
| AC-11.5 | TASK-006, TASK-002, TASK-007 |
| AC-05.1, AC-06.1 (shared unknown-key sanitiser) | TASK-001, TASK-003 |
| AC-05.2 (sequence exhausted) | TASK-003 |
| AC-06.8 (only if G-6 keeps its default) | TASK-003 |
| NFR-04.1 | TASK-003 (challenge and `instance`), TASK-004, TASK-005, TASK-006 |
| NFR-03.1, NFR-04.3, NFR-04.4 | TASK-004, TASK-005, TASK-006 (each extends the existing tests to its new endpoints) |

**Not in this sprint:**
- `GET /api/admin/audit`
- GitLab CI and Linux snapshot baselines (Sprint 5)
- audit of ADMIN reads
- refunding a prepaid renewal term separately (SG-5)
- restricting the admin job's date range (SG-4)
- editing `.claude/**` substrate, such as the endorsement-validator skill's code list under G-6 (humans only, root rule 1)

#### Checkpoint decisions (G-1 to G-6). Approving the plan approves each default; veto any of them here.

- **G-1. The TERM_LIFE v2 rule set for AC-07.3.**
  - **Planner default: an isolated test fixture, with no published file.** The fixture is TERM_LIFE v1 with band
    31–40 `baseRatePerThousand` factor `"1.30"`, `version` 2 and `effectiveFrom` 2027-09-01.
  - The test publishes it through the existing PRODUCT_MANAGER publish API, in its own application context, which is
    discarded after the test. This is the same pattern as Sprint 3's EndorsementRuleVersionIT.
  - The AC-04.1 profile then renews at 7400.00 at rule version 2, while term 1 and version 1 keep 6850.00 at version 1.
  - No `rules/term-life/v2.json` is added, so production pricing and every other test are unchanged.
  - *If vetoed:* publish a real `v2.json`. That needs a product decision on the rates and the date, a spec change to
    the catalog, and a re-baseline of every real-clock E2E price once `effectiveFrom` passes. The planner advises against it.
- **G-2. Seed dates for the sample policies.**
  - **Planner default: relative to "today" on the injected clock at startup** (C10). A fixed date would make "near
    renewal" and "past expiry within grace" true on only a few calendar days.
  - Under a fixed test clock the seed is relative to that clock, so it stays deterministic in tests.
  - *If vetoed:* fixed dates (issued 2026-11-01). The demo then shows neither state on most days, and AC-11.1 still holds.
- **G-3. Renewal payment: what is paid and what is recorded.**
  - **Planner default:** the request body is empty or `{}`, and the amount paid is the offered premium. No client
    amount is accepted.
  - The "simulated payment record" is three things written in one transaction: the term's paid flag (one-way, so a
    paid term never becomes unpaid), a new policy version n+1 for the renewal term, and the → RENEWED transition. This
    meets "append-only terms-paid history": nothing is updated back, and nothing is deleted.
  - Version n+1 is needed so that later endorsements re-rate on the renewal premium and rule version. Without it, the
    header and the endorsement basis would stay on the old term's premium.
  - *If vetoed:* also add an append-only `premium_payment` table (term, amount, paid date, actor) in V10, with an
    entity, a repository and AppendOnlyArchTest checks, owned by TASK-004.
- **G-4. Cancellation reasons.** The spec shows only `"reason": "NO_LONGER_NEEDED"`.
  - **Planner default:** `reason` is required, from the closed set `NO_LONGER_NEEDED`, `REPLACED_BY_OTHER_COVER`,
    `AFFORDABILITY` and `OTHER`. The CANCELLED transition records it as its reason.
  - *If vetoed:* a free-form code matching `^[A-Z][A-Z0-9_]{0,39}$`, and the SPA offers the same four values.
- **G-5. What "current paid term" means.**
  - The spec says the cancellation date "must be within the current paid term". Endorsements use "the current term".
    The spec allows paying a renewal from offer creation, which can be before the renewal term starts.
  - **Planner default:** the current paid term is the paid term with the highest term number: term 1 until a renewal
    is paid, and the renewal term from the moment it is paid. The endorsement window and basis, the policy header, the
    renewal cycle and cancellation all use it. An unpaid offer changes nothing.
  - The consequence is SG-5. Between an early payment and the renewal term's start, endorsement and cancellation dates
    must lie in the renewal term.
  - *If vetoed:* use "the paid term containing today". Endorse and cancel then get 409 `RENEWAL_TERM_NOT_STARTED`
    between an early payment and the renewal term's start (a new code, amended the same way).
- **G-6. SG-1: sum-insured endorsements do not re-run underwriting.**
  - **Planner proposal: close it.** A smoker can raise their sum insured above 10,000,000 without the UW-TL-002
    referral, which bypasses underwriting. The fix is small and uses the domain evaluator that already exists.
  - A CHANGE_SUM_INSURED is refused when its modified inputs fire a REFER or DECLINE rule of the policy's own rule
    version that the current inputs don't fire. Comparing only *newly* firing rules means a policy that an underwriter
    approved by hand (for example BMI 37) can still change its sum insured.
  - This adds AC-06.8 and the 400 code `SUM_INSURED_REQUIRES_UNDERWRITING` (TASK-003).
  - *If vetoed:* record the gap as an accepted v1 limitation, using the alternative text below. Nothing else changes.

**Amendment commit (G-3 to G-6).** Approving this plan approves the text below. The main session commits it as one
commit at the checkpoint, before it launches the generators. The message is `spec: renewal payment, current paid
term, cancellation reasons and SG-1 underwriting re-check (plan G-3 to G-6, approved at the Sprint 4 checkpoint)`, and
the commit touches only `specs/renewal-cancellation_spec.md` and `specs/endorsement_spec.md`. If the commit is missing
when TASK-001 starts, TASK-001 makes it as its first commit, using exactly this text, adjusted for any veto. No other
task commits spec files.
- `renewal-cancellation_spec.md`, Renewal cycle section, a new paragraph after the "Paying:" paragraph: "**Payment
  request (G-3).** The body is empty or `{}`. Any key gives 400 naming it. The amount paid is the pending term's
  premium, never a client value. In one transaction the pending term is marked paid, policy version n+1 is appended,
  and the policy moves to **RENEWED** (reason `RENEWAL_PAID`, actor the owner). Version n+1 has the current version's
  risk inputs, sum insured, address and nominees, with the renewal term's premium, rule version, start and end dates.
  The paid term, its version and its transition are the simulated payment record. A paid term never becomes unpaid.
  With no pending term the response is 409 `NO_PENDING_RENEWAL`. After `termEnd + gracePeriodDays` it is 409
  `RENEWAL_PAYMENT_WINDOW_CLOSED`."
- `renewal-cancellation_spec.md`, Cancellation section, a new paragraph after the "The date is optional…" sentence:
  "**Current paid term (G-5).** The current paid term is the paid term with the highest term number: term 1 until a
  renewal is paid, and the renewal term from the moment it is paid, even before it starts. The endorsement spec's
  'current term', the policy header and the renewal cycle use the same term. A cancellation date outside it gives 400
  `CANCELLATION_DATE_OUT_OF_TERM`. **Reason (G-4).** `reason` is required and is one of `NO_LONGER_NEEDED`,
  `REPLACED_BY_OTHER_COVER`, `AFFORDABILITY` or `OTHER`. Anything else gives 400 naming `reason`. The transition
  → CANCELLED records it as its reason."
- `endorsement_spec.md`, Validation rules, a new last bullet:
  - **Default text:** "`CHANGE_SUM_INSURED` re-checks underwriting (G-6, closes SG-1). The modified risk inputs and the
    current risk inputs are evaluated with the underwriting rules of the policy's own rule version. If a REFER or
    DECLINE rule fires on the modified inputs and not on the current inputs, the response is 400
    `SUM_INSURED_REQUIRES_UNDERWRITING` naming each supplied sum-insured field, and nothing is written. The check runs
    only when every other rule passes. The preview runs the same check."
  - **Default, also in its Acceptance Criteria after AC-06.7:** "**AC-06.8** Given a TERM_LIFE policy for a smoker
    aged 35 with sum insured 5,000,000, when the sum insured changes to 12,000,000, then the response is 400
    `SUM_INSURED_REQUIRES_UNDERWRITING` and no endorsement or version is written. Given the same change for a
    non-smoker aged 35, then it is applied with new annual premium 16090.00."
  - **If G-6 is vetoed, this text replaces both:** "A sum-insured change does not re-run underwriting. This is an
    accepted v1 limitation (G-6, SG-1)." No AC is added.

#### Planner calls (P-1 to P-15). Veto any of these here.

- **P-1 Decomposition and scheduling.** The exec workflow runs the parallel batch to completion, then the sequential
  tasks one at a time in listed order.
  - The parallel batch is TASK-001 (pure domain) and TASK-002 (`frontend/` only).
  - The backend tasks then run one at a time (TASK-003 → TASK-004 → TASK-005 → TASK-006). They share one working tree,
    one `backend/target`, one Flyway sequence and one branch.
  - TASK-003 goes first among them, so the new endpoints are born with the fixed `instance`, the sanitiser and no Basic
    challenge.
  - TASK-007 joins both lines.
  - A `.git/index.lock` error during the parallel batch means another generator is committing: retry the commit and
    never delete the lock.
- **P-2 Build flag.** TASK-001 and TASK-003 to TASK-006 run Maven with `-Dskip.frontend=true`. A full build runs
  `npm ci`, which would delete `frontend/node_modules` while TASK-002 uses it. Only TASK-007 runs the full build.
- **P-3 TDD evidence shape (same as Sprints 2–3).** A red commit compiles (production stubs are allowed), and its new
  tests fail on an assertion, not a compile error. One red commit may name several sub-criteria. An ArchUnit existence
  check gets its red by failing while the class is absent.
- **P-4 Test names for rules without their own sub-criterion** (root rule 8):
  - current-term behaviour with an unpaid offer, the job response and audit → AC-07.1
  - idempotency and concurrent runs → AC-07.2
  - pay checks and the renewal version → AC-07.5
  - cancel shape, reason, date window and premium basis → AC-08.1
  - one record, second cancel, concurrent cancels and atomicity → AC-08.4
  - dashboard shape, `asOf` parsing and the seed → AC-11.1
  - the Basic challenge and `instance` → NFR-04.1
  - sequence exhausted → AC-05.2
  - a sanitised echo → the AC that endpoint's existing unknown-key test already uses (AC-05.1 for issue, AC-06.1 for
    endorse and preview, and so on)
- **P-5 Check order** (role checks by `@PreAuthorize` always come first):
  - **Job:** body shape and the `date` format (400), then run.
  - **Pay:** body shape (400), then lookup and owner (404), then state (409 invalid-policy-state, because the state
    machine refuses → RENEWED), then 409 `NO_PENDING_RENEWAL`, then 409 `RENEWAL_PAYMENT_WINDOW_CLOSED`.
  - **Preview:** the `date` format (400), then 404, then state (409, because the state machine refuses → CANCELLED),
    then 400 `CANCELLATION_DATE_OUT_OF_TERM` naming `date`.
  - **Cancel:** body shape (400: unknown keys, a malformed date, a missing or unknown `reason`), then 404, then 409,
    then 400 `CANCELLATION_DATE_OUT_OF_TERM` naming `cancellationDate`.
  - **Dashboard:** the `asOf` format (400 naming `asOf`).
- **P-6 Real states now exist.** AC-10.3, the AC-10.2 re-proof and the new AC-10.4 sequences use the real cycle, pay
  and cancel endpoints. The Sprint 3 test helper (an aggregate transition plus a transition row) stays for the older
  tests, and no new test uses it.
- **P-7 Renewal cycle semantics.**
  - Each in-force policy is processed in its own transaction, so one failure doesn't undo the others: first the offer
    (step 1), then the lapse (step 2). A policy whose offer window was missed therefore still lapses in the same run.
  - **Offer timing and grace** come from the current paid term's rule version. **Premium and `termMonths`** of the
    renewal term come from the version active on the run date, which the term records.
  - The premium re-rates the current version's stored risk inputs unchanged (SG-3).
  - A policy whose inputs the active version rejects gets no offer. It is logged by policy number only (SG-7).
  - An offer writes a term and no transition.
  - **Lapse:** reason `GRACE_PERIOD_EXPIRED`, actor `system:renewal-scheduler`, even when an ADMIN triggered the run.
  - **Scheduled run:** `0 0 23 * * *` in the configured `policyforge.time-zone`, with run date = today on the injected
    clock. Test configuration turns the trigger off, so a run at 23:00 can never touch an integration-test database.
  - The admin job accepts any date (SG-4).
- **P-8 Pay concurrency.** A pay that races a lapse, a cancel or another pay on the same policy loses on the policy's
  optimistic lock. The loser gets 409 `POLICY_UPDATE_CONFLICT`, or for the cycle, the policy is skipped. Nothing of
  the loser is kept. A cancel that races a cancel loses the same way, and the refund table's one-row-per-policy key is
  the backstop.
- **P-9 Allow-lists (KD-008).**
  - The detail gains only the C5 fields. Refund records and previews carry numbers, dates, codes and the formula
    string only.
  - The dashboard carries policy numbers, products, dates and money only: no customer IDs and no PII.
  - The `formula` string contains only the numbers of the calculation (C5).
- **P-10 Audit.**
  - Each run of the cycle, from the admin endpoint or the schedule, appends one `RENEWAL_CYCLE_RUN` audit entry. The
    actor is the admin or `system:renewal-scheduler`, the entity type is `RENEWAL_CYCLE` and the entity ID is the run
    date.
  - Customer pay and cancel are recorded by their transition rows (actor, reason, time), as Sprint 3 P-10 did for
    endorsements.
  - Dashboard reads are not audited (no PII, and no spec rule asks for it).
- **P-11 Dashboard semantics** (C6):
  - **Active:** the current status is in force (`asOf` does not rewrite history).
  - **Premium collected:** all policies of the product whatever their status, computed from every paid term,
    endorsement and refund stored at request time, not filtered by `asOf` (SG-6).
  - **Pipeline:** uses the paid term *in force on* `asOf` (start ≤ `asOf` ≤ end) whose end is at most `asOf` + 60.
    `renewalPremium` and `paid` come from the next term, or are `null` and `false` when no offer exists.
  - **Forecast:** in force, and the current paid term ended before `asOf`.
  - **Order:** the pipeline sorts by `termEnd`, then number. The forecast sorts by number.
  - **SPA:** the "Active policies" tile adds the three integer counts. Counts are not money, so `noMoneyArithmetic`
    still holds.
- **P-12 Deterministic E2E on the real clock.**
  - **Cancel:** a policy issued today and cancelled today is inside cooling-off, so its refund is exactly 6850.00 with
    fee 0.00 on any date.
  - **Renew:** the test calls the admin job through the API with date = its own policy's expiry − 30 days. It never
    uses a later date, so no policy issued during the run can lapse.
  - **Dashboard:** journeys pick `asOf` around their own policy's term end, and compare the rendered values with the
    page's own dashboard response.
  - E2E never asserts seed state, because a parallel renewal run may lapse the seed policies.
- **P-13 Effort overrides.** TASK-003, TASK-004 and TASK-005 are raised from `high` to `xhigh`. They hold
  security-boundary changes, concurrency (cycle and pay, cancel races), atomicity and money. If the orchestrator clamps
  sonnet to `high`, nothing else changes.
- **P-14 Security-minor design choices** (C7):
  - **Basic challenge:** no 401 carries a `WWW-Authenticate` header, on any path. Preemptive Basic still works.
  - **`instance`:** every problem's `instance` is a fixed URN per problem type, extending the existing
    `urn:policyforge:problem:not-found` and `…:payload-too-large`.
  - **Echoed keys:** one sanitiser bounds every echoed unknown key to 64 characters of `[A-Za-z0-9_.-]`, and every other
    character becomes `_`. The Sprint 3 100-character tests are updated to it.
  - **Sequence exhausted:** the issue answers 503 `POLICY_NUMBER_SEQUENCE_EXHAUSTED`. This is an availability
    condition, and the application stays APPROVED.
- **P-15 The seed runs in every application context**, like the U-14 seed.
  - Existing integration tests that list customer1's or customer2's policies or applications are made seed-aware:
    they filter by their own IDs and keep every assertion's strength.
  - AC-11.1 runs in its own context, where only the seed exists.
  - The seed's backdated issuance path must not be reachable from the web layer.

**Spec gaps. Recorded, not implemented:**
- **SG-1.** Under G-6, this is closed by AC-06.8. If G-6 is vetoed, it is an accepted limitation.
- **SG-2.** The cancellation date may be any date in the current paid term, including past dates, as the spec allows.
  A backdated cancellation gives a larger refund. Recommendation: allow only today or later.
- **SG-3.** Renewal re-rates the stored inputs unchanged. Ages (TERM_LIFE `age`, MOTOR `driverAge` and
  `vehicleAgeYears`, HOUSEHOLD `propertyAgeYears`) are not advanced, and underwriting is not re-run at renewal. The
  spec says "current risk inputs", and the dashboard example's renewal premium 6850.00 agrees.
- **SG-4.** The admin job accepts any date, including future ones, and lapses are terminal. A mistaken future run
  lapses policies early. It is a testing endpoint by spec. Recommendation: restrict it before production.
- **SG-5.** Under G-5, after an early renewal payment, the rest of the previous term can't be endorsed or cancelled.
  Only the renewal term's premium is refundable.
- **SG-6.** There is no paid-date history, so `premiumCollected` can't be cut at `asOf`.
- **SG-7.** A policy whose stored inputs the active rule version rejects never gets an offer, and so never lapses.

**Cross-task contracts.** Money is a decimal string with scale 2, as in Sprints 1–3. Dates are ISO `YYYY-MM-DD`.
Instants are ISO-8601. Every state-changing endpoint consumes `application/json` only (KD-007).

- **C1. `POST /api/admin/jobs/renewal-cycle?date=YYYY-MM-DD`** (ADMIN).
  - `date` is optional and defaults to today. A malformed `date` gets 400 naming `date`. The body is empty or `{}`, and
    any key gets 400 naming it, sanitised.
  - Success is **200** `{ runDate, offersCreated: [policyNumber…], lapsed: [policyNumber…] }`, with both lists
    ascending.
  - A second run for the same date returns two empty lists (AC-07.2). Audit follows P-10.
- **C2. `POST /api/policies/{policyNumber}/renewal/pay`** (CUSTOMER owner).
  - The body is empty or `{}`. Checks follow P-5 and writes follow G-3.
  - Success is **200** `{ policy: PolicyHeader, term: Term }`, where `term` is the newly paid term.
- **C3. `GET /api/policies/{policyNumber}/cancel/preview?date=YYYY-MM-DD`** (CUSTOMER owner). It writes nothing.
  - Success is **200** `RefundQuote`: `{ cancellationDate, termNo, termStart, termEnd, premium, termDays, unexpiredDays,
    coolingOff, fee, amount, formula }`.
  - `premium` is the basis: the term premium plus the adjustments of the endorsements effective in that term.
- **C4. `POST /api/policies/{policyNumber}/cancel`** (CUSTOMER owner).
  - The body is exactly `{ reason, cancellationDate? }`, with `reason` from the G-4 set. Checks follow P-5.
  - Success is **201** `{ refund: Refund, policy: PolicyHeader }`.
  - In one transaction it writes the RefundRecord, applies → CANCELLED through the aggregate, and appends the
    transition (reason = the request's `reason`, actor = the customer).
- **C5. Detail and record shapes.**
  - **Term:** `{ termNo, startDate, endDate, premium, paid, ruleVersion }`.
  - **Refund:** `{ termNo, cancellationDate, premium, termDays, unexpiredDays, coolingOff, fee, amount, formula,
    actorId, createdAt }`.
  - **Formula:** `max(0, <premium> * <unexpiredDays> / <termDays> - <fee>) = <amount>` for pro-rata (for example
    `max(0, 6850.00 * 265 / 365 - 500.00) = 4473.29`), or `cooling-off: full premium <premium> = <amount>`.
  - **Detail (C3 of Sprint 3):** gains `pendingRenewal`, which is `null` or `{ termNo, startDate, endDate, premium,
    ruleVersion, payableUntil }`, where `payableUntil` = the current paid term's end + its grace days. `refunds` lists
    Refund objects in append order.
  - **PolicyHeader and C2 keys** are unchanged. Their term dates and `nextPremiumDueDate` follow the current paid term
    (G-5), and the premium and sum insured come from the current version.
- **C6. `GET /api/admin/dashboard?asOf=YYYY-MM-DD`** (ADMIN).
  - The keys are exactly the spec's: `asOf`, `activePoliciesByProduct` (always all three products, 0 when none),
    `premiumCollected` (three products plus `total`, scale 2), `renewalPipeline[]` of `{ policyNumber, termEnd,
    renewalPremium (string or null), paid }`, and `lapseForecast` `{ count, policies[] }`.
  - The semantics follow P-11. CUSTOMER and UNDERWRITER get 403 (AC-11.5).
- **C7. Errors.** All errors are `application/problem+json` with a fixed `detail`.
  - **New 400 codes:** `CANCELLATION_DATE_OUT_OF_TERM`, and `SUM_INSURED_REQUIRES_UNDERWRITING` (G-6).
  - **New 409 codes:** `NO_PENDING_RENEWAL` and `RENEWAL_PAYMENT_WINDOW_CLOSED`. Invalid state stays
    `/problems/invalid-policy-state`, `INVALID_POLICY_STATE`.
  - **New 503 code:** `POLICY_NUMBER_SEQUENCE_EXHAUSTED`.
  - **`instance`** is always `urn:policyforge:problem:<slug>`. The slug is the last segment of the problem `type`. For
    framework `about:blank` problems, it is the hyphenated, lower-case reason phrase (for example `method-not-allowed`).
    It is never derived from the request.
  - **401** never carries `WWW-Authenticate`.
  - **Echoed keys:** every `errors[].field` that names a caller-supplied key is sanitised (P-14). Fixed prefixes such
    as `kyc.`, `kyc.address.` and `riskInputs.` are kept.
- **C8. Flyway.** V10 (TASK-004) adds `rule_version` to `policy_term`, backfilled from each policy's version 1 and then
  NOT NULL. V11 (TASK-005) adds `refund_record`, unique on the policy number, with NUMERIC(19,2) money. Never edit
  V1–V9 or any migration committed earlier in this sprint. Fix forward with the next free number.
- **C9. SPA.**
  - **Route:** `#/admin/dashboard` (TASK-007), behind the sign-in gate, with nav link `nav-dashboard`. Renew and Cancel
    are panels on Policy Detail, with no new routes.
  - **Test IDs:** at least `renew-panel`, `renew-start`, `renew-end`, `renew-premium`, `renew-payable-until`,
    `renew-pay`, `renew-error`, `cancel-panel`, `cancel-date`, `cancel-reason`, `cancel-preview`,
    `refund-preview-amount`, `refund-preview-fee`, `refund-preview-days`, `refund-preview-cooling-off`,
    `refund-preview-formula`, `cancel-confirm`, `cancel-error`, `refund-row-<termNo>` and `refund-<termNo>-amount`.
  - **Dashboard test IDs:** `dashboard-asof`, `dashboard-load`, `kpi-active`, `kpi-premium`, `product-row-<CODE>`,
    `product-<CODE>-active`, `product-<CODE>-premium`, `pipeline-row-<number>`, `pipeline-<number>-premium`,
    `pipeline-<number>-paid`, `forecast-count`, `forecast-<number>` and `dashboard-error`.
  - The SPA does not know roles: the server's 403 shows a fixed message. Credentials stay in memory only.
- **C10. Seed (G-2).** T is today on the injected clock at startup. The policies use synthetic KYC and go through the
  quote, application and issuance services, with issuance backdated to the effective date through a path not
  reachable from the web layer. The seed then runs one renewal cycle for T. It is idempotent by a synthetic marker,
  and it runs after the U-14 seed.

  | # | Customer | Product (profile) | Term | State after seeding |
  |---|---|---|---|---|
  | S1 | customer1 | TERM_LIFE (AC-04.1, 6850.00) | ends T+20 | in force, renewal offer pending (near renewal) |
  | S2 | customer1 | MOTOR (AC-01.2, 15150.00) | starts T−30 | in force |
  | S3 | customer1 | HOUSEHOLD (AC-01.3, 11350.00, inputs with no UW rule firing) | starts T−30 | in force |
  | S4 | customer2 | TERM_LIFE (AC-04.1) | starts T−30 | in force |
  | S5 | customer2 | MOTOR (AC-01.2) | ended T−5 | in force, offer unpaid, in grace (lapses at T+11) |
  | S6 | customer2 | HOUSEHOLD (AC-01.3) | starts T−30 | in force |

  On a fresh context the dashboard shows counts 2/2/2, `premiumCollected` 13700.00 / 30300.00 / 22700.00 with total
  66700.00, S1 in the pipeline (6850.00, unpaid), and `lapseForecast` = `{ count: 1, policies: [S5] }`.

**Execution notes for every generator.**
- **Maven.** Run `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`
  before any Maven command. Backend tasks add `-Dskip.frontend=true` (P-2).
- **Selective integration tests** run under failsafe (KD-009): `./mvnw verify -Dskip.frontend=true -Dit.test=<Pattern>
  -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`. Every backend task's final green run
  is a full `./mvnw verify -Dskip.frontend=true` with JaCoCo enabled.
- **KD-014.** Run every long command (Maven, `npm test`, Playwright and its webServer) in the **foreground**, with a
  timeout of at most 600000 ms. Split anything longer. Never end the turn while a command or task is pending.
- **Commits (from the sprint brief, same policy as Sprints 2–3).**
  - Commit live on `feat/renewal-cancellation`: `test(red): AC-NN.M …` with the failing tests before the
    implementation, then `feat(green): …`, then `refactor: …` when there is cleanup.
  - Stage explicit paths and commit with `git commit -m "…" -- <paths>`.
  - Never push, and never commit on `dev` or `main`. Retry on `index.lock` (P-1).
  - Save each task's red and green runs as `sprint-progress/<TASK>-red.log` and `<TASK>-green.log`.
  - Frontend green commits include a refreshed `frontend/coverage/lcov.info`.
- **Data.** Use synthetic data only. KYC: Aadhaar `XXXX-XXXX-1234`, PAN `ABCDE1234F`, the name "Asha Test", and
  postcode 411001. The seed uses its own distinctive synthetic markers. Keep money as decimal strings and `BigDecimal`.
  Log IDs only (policy number, application ID, quote ID).
- **Test names.** JUnit tests use `@Tag("AC-NN")` plus `@DisplayName("AC-NN.M: …")`, or the NFR form. Vitest and
  Playwright titles start with `AC-NN.M:`. The `ac-tag-check` hook rejects untagged test files. The name mapping
  follows P-4.
- **Tests, clocks and seeds.**
  - Fixed-clock integration tests move the shared test clock and put it back afterwards.
  - Tests are seed-aware: find rows by ID, and never assume counts or positions (P-15).
  - A test that publishes a rule version (G-1), or needs an exact whole-portfolio state (AC-11.1), runs in its own
    application context.
- **Oracle.** Every new money value in a test must match `premium_check.py`. The values are listed in the readiness
  check.
- **Port.** Port 8080 must be free when a task ends. Stop any server you started.
- **Agents and skills:**

  | Task | Agents and skills |
  |---|---|
  | TASK-001 | `test-engineer` (red) with `spec-to-test-generator`; `renewal-orchestrator-agent` (date boundaries); `premium-calc-evaluator` skill (oracle cross-check) |
  | TASK-002 | `ui-designer` (Vitest first, then pages) |
  | TASK-003 | `test-engineer`; `endorsement-agent` (G-6 re-check) |
  | TASK-004 | `renewal-orchestrator-agent`; `test-engineer`; `migration-coherence-agent` (V10); `policy-version-validator` skill (G-1 fixture); `premium-calc-evaluator` skill |
  | TASK-005 | `renewal-orchestrator-agent`; `test-engineer`; `migration-coherence-agent` (V11); `archtest-author` skill (RefundRecordEntity); `premium-calc-evaluator` skill |
  | TASK-006 | `test-engineer`; `premium-calc-evaluator` skill (dashboard sums) |
  | TASK-007 | `ui-designer` and `test-engineer` (E2E and snapshots) |

  The Evaluator adds a Playwright MCP walkthrough on a fresh packaged app:
  - customer1: My Policies shows the seed policies. S1's detail shows the Renew panel at 6850.00, and Pay makes it
    RENEWED.
  - customer2: S5 shows Renew with `payableUntil` = its term end + 15.
  - A same-day policy previews a 6850.00 refund, is cancelled, and the Refunds tab shows the refund.
  - admin1: the Dashboard tiles, table, pipeline and forecast, with the tiles stacked at 375px.
  - customer1: the Dashboard shows the not-authorised message.
  - curl: a 401 has no `WWW-Authenticate`, and `instance` values are URNs.

**Out-of-orchestrator scope. The user does these manually after the sprint completes:**
1. Run `/sprint-archive S4`. It archives this workspace into `sprint-contracts/` and `specs/reviews/`.
2. Run `/pr`. It runs the review gate, writes `docs/prs/PR-NNN.md`, pushes the branch and opens a GitHub PR into `dev`.
   The user merges it with "Create a merge commit".
3. Under G-6, add `SUM_INSURED_REQUIRES_UNDERWRITING` to the `endorsement-validator` skill's code matrix. This is
   `.claude/**` substrate, so it is human-only under root rule 1.
4. Decide SG-2, SG-4 and SG-6 for Sprint 5 (production restrictions), and record the Sprint 4 lessons in
   `docs/knowledge-deposits.md`.


> **Checkpoint outcome (2026-11-04, user):** plan APPROVED with G-1 to G-5 at their defaults. **G-6 VETOED**: SG-1 is
> recorded as an accepted v1 limitation (the veto text was committed). TASK-003's G-6 re-check and AC-06.8 criteria are
> replaced by a 'no re-check, record the veto' criterion; TASK-007 audits 81/81. `approved-plan.json` is authoritative.

## Tasks

### TASK-001: Domain rules: GracePeriodPolicy, RenewalPolicy (current paid term, offer, renewal dates, payment window), ProRataRefundCalculator and RefundQuote, the cancellation-date rule, the refund premium basis, portfolio figures and the shared unknown-key sanitiser
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Confirms before its first commit that the G-3 to G-6 amendment `spec:` commit exists after `12f95c3`. If it is missing, makes it first, with exactly the plan's amendment text adjusted for any checkpoint veto, touching only `specs/renewal-cancellation_spec.md` and `specs/endorsement_spec.md`.
  - Implements GracePeriodPolicy.isLapsed(termEnd, graceDays, runDate, paid) exactly as the spec defines it, and proves "AC-07.4: …": with termEnd 2027-10-31, grace 30 and unpaid, 2027-11-30 is not lapsed and 2027-12-01 is lapsed. A paid term never lapses.
  - Proves "AC-07.6: …" with the lifecycles of the shipped v1 rule files. MOTOR and HOUSEHOLD (grace 15) are first lapsed on termEnd + 16 and not on termEnd + 15. TERM_LIFE (grace 30) is first lapsed on termEnd + 31 and not on termEnd + 30.
  - Implements RenewalPolicy as pure functions. The current paid term is the highest-numbered paid term (G-5). The term in force on a date is the paid term whose start ≤ date ≤ end (P-11). An offer is due ⇔ runDate ≥ current paid term end − renewalOfferDaysBeforeExpiry and no later term exists. The renewal term starts at termEnd + 1 and ends at start + termMonths − 1 day. Payment is allowed ⇔ an unpaid later term exists and today ≤ current paid term end + graceDays.
  - Proves "AC-07.1: …": for term 2026-11-01..2027-10-31 with 30 offer days, an offer is due on 2027-10-01 and not on 2027-09-30, and the renewal term is 2027-11-01..2028-10-31. An unpaid offer leaves term 1 as the current paid term, and a paid renewal term is current even before it starts. Proves "AC-07.2: …": an existing later term means no offer.
  - Proves "AC-07.5: …" for the payment window with grace 30: payment is allowed on 2027-11-10 and 2027-11-30, and refused on 2027-12-01 and when no unpaid later term exists.
  - Implements ProRataRefundCalculator.calculate(premium, termStart, termEnd, cancellationDate, policyEffectiveDate, firstTerm, coolingOffDays, cancellationFee), returning a RefundQuote of termDays, unexpiredDays, coolingOff, fee, amount and the C5 formula string. Intermediates use scale 10 HALF_UP after each multiply and divide in the oracle's order, and the result is HALF_UP to 2 dp with a floor of 0.00. Cooling-off applies only to the first term and returns the full premium with fee 0.00.
  - Proves "AC-08.1: …": 6850.00 over 2026-11-01..2027-10-31, cancelled on 2027-02-09 with fee 500.00, gives termDays 365, unexpiredDays 265, amount 4473.29 and formula `max(0, 6850.00 * 265 / 365 - 500.00) = 4473.29`. MOTOR 15150.00 with fee 250.00 gives 10749.32, and HOUSEHOLD 11350.00 with fee 200.00 gives 8040.41.
  - Proves "AC-08.2: …" with cooling-off 15: 2026-11-11 gives 6850.00, fee 0.00 and coolingOff true. 2026-11-15 (day 14) still gives the full premium, and 2026-11-16 (day 15) gives the pro-rata 6068.49. A renewal term (2027-11-01..2028-10-31, 6850.00) cancelled on 2027-11-03 gives 6312.57 and never the full premium.
  - Proves "AC-08.3: …": cancellation on 2027-10-31 (unexpiredDays 1) with fee 500.00 gives 0.00 at scale 2, never a negative amount.
  - Proves "AC-08.5: …" with a fixed-seed generator over 1,000 random cases (random terms and cancellation dates within them, random 2 dp premiums and fees, random cooling-off days and first-term flags): every amount satisfies 0.00 ≤ amount ≤ premium, has scale exactly 2, and no case throws.
  - Implements the pure cancellation-date rule. An absent date defaults to today. A date outside [current paid term start, end] is a violation naming the given field, with code `CANCELLATION_DATE_OUT_OF_TERM` and a fixed message. Proves "AC-08.1: …" that the term start and end are accepted and the days just outside them are rejected.
  - Implements the refund premium basis: the term premium plus the adjustments of endorsements whose effective date lies in that term. Proves "AC-08.1: …": 6850.00 + 1330.85 (effective 2027-05-01) gives a basis of 8180.85, and cancelling on 2027-06-01 refunds 2929.23 (153/365). An endorsement effective in another term is excluded.
  - Implements the pure portfolio rules of P-11: premium collected per product is the sum of paid term premiums plus adjustments minus refunds, and total is the sum of the products, all at scale 2; plus pipeline and forecast membership. Proves "AC-11.2: …": 6850.00 + 6850.00 + 1330.85 − 4473.29 = 10557.56 exactly, and total equals the per-product sum.
  - Proves "AC-11.3: …" for the pipeline: a term ending in 20 days is in, one ending in 90 days is out, and the boundaries are 60 days in and 61 days out. Proves "AC-11.4: …" for the forecast: an in-force policy whose current paid term ended before asOf with no paid renewal is in, while a paid renewal, LAPSED, CANCELLED and not-yet-ended are out.
  - Implements the shared unknown-key sanitiser (P-14): at most 64 characters, and any character outside `[A-Za-z0-9_.-]` becomes `_`. Proves "AC-05.1: …" and "AC-06.1: …": a 5000-character key gives its first 64 characters, `<script>` gives `_script_`, and a valid key is unchanged.
  - Imports only `java.*` and domain classes, uses no double or float, and passes DomainPurityArchTest and MoneyPrecisionArchTest.
  - Cross-checks every refund and sum value above with `premium_check.py` and records the oracle output in the green log.
  - Shows a `test(red): AC-…` commit with the failing tests (stubs allowed) before the `feat(green)` commit. Saves `sprint-progress/TASK-001-red.log` and `TASK-001-green.log` naming AC-05.1, AC-06.1, AC-07.1, AC-07.2, AC-07.4, AC-07.5, AC-07.6, AC-08.1–08.5, AC-11.2, AC-11.3 and AC-11.4.
  - Passes `./mvnw verify -Dskip.frontend=true` with ArchUnit green and JaCoCo at or above 80% domain and 70% overall.

### TASK-002: Frontend: Renew and Cancel panels on Policy Detail, the Refunds tab, the Admin Dashboard page, the typed API client and Vitest
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: []
- **acceptance_criteria**:
  - Touches nothing outside `frontend/`, adds no npm dependencies, and never commits spec files (TASK-001 is the only amendment fallback).
  - Adds typed API client functions and types matching C2–C6 field for field: pay renewal, cancel preview, cancel, the extended policy detail (Term with ruleVersion, pendingRenewal, Refund) and the dashboard. Parses the C7 problems, including `errors[]`, `code` and the invalid-policy-state `type`.
  - Renders a Renew panel on Policy Detail only when `pendingRenewal` is non-null and the status is in force. It shows the renewal start and end dates, premium, rule version and payable-until date verbatim. Pay posts exactly `{}` and reloads the detail on 200. It shows fixed messages for 409 `NO_PENDING_RENEWAL`, 409 `RENEWAL_PAYMENT_WINDOW_CLOSED`, 409 invalid-policy-state, 409 `POLICY_UPDATE_CONFLICT` and 403.
  - Renders a Cancel panel only for in-force statuses, with an optional date input and a required reason select offering the four G-4 values. Preview calls the C3 endpoint with `date` only when one is given, and shows amount, fee, unexpiredDays/termDays, the cooling-off flag and formula verbatim.
  - Enables Confirm only after a successful preview for the current inputs, and clears the preview when any input changes. Confirm posts exactly `{reason}` or `{reason, cancellationDate}` and reloads the detail on 201. It shows a 400 `date` or `cancellationDate` error at the date input, and fixed messages for every C7 code and 409.
  - Populates the Refunds tab with one row per refund (termNo, cancellation date, amount, fee, days, cooling-off and formula, verbatim) and keeps the empty state when there are none.
  - Adds an Admin Dashboard page component, which TASK-007 mounts. It has an optional As-of input, and loads C6 with `asOf` only when given. The Active policies tile shows the sum of the three integer counts, and the Premium collected tile shows `total` verbatim.
  - Renders the dashboard's per-product table (count and premium per product), the renewal pipeline (number, term end, renewal premium or "No offer yet" when null, and Paid/Unpaid) and the lapse forecast (count and numbers). It shows a fixed not-authorised message on 403 and an `asOf` error at its field on 400.
  - Lays out with CSS grid/flex and the single 640px breakpoint, where the KPI tiles wrap to one column below 640px. Has no horizontal scrolling at 375px on the detail page and the dashboard. Puts every C9 `data-testid` on its element.
  - Does no money arithmetic, renders API strings verbatim, and keeps `noMoneyArithmetic.test.ts` passing.
  - Includes Vitest tests titled "AC-07.1: …" (the panel shows the offered term and premium verbatim, and is hidden with no offer), "AC-07.5: …" (Pay posts `{}` and the reload shows RENEWED; the window-closed message), "AC-08.1: …" (preview shows 4473.29 and the formula verbatim; exact confirm body; editing clears the preview), "AC-08.2: …" (cooling-off preview shows 6850.00, fee 0.00 and the flag) and "AC-08.3: …" (0.00 shown).
  - Includes Vitest tests titled "AC-08.4: …" (Refunds tab shows one row; no Cancel panel on CANCELLED; the 409 message), "AC-10.3: …" (no Renew or Cancel panel on LAPSED; the 409 invalid-policy-state message from cancel), "AC-11.1: …" (tiles and table verbatim), "AC-11.2: …" (premium collected per product and total verbatim), "AC-11.3: …" (pipeline rows, including a null premium), "AC-11.4: …" (forecast count and numbers) and "AC-11.5: …" (403 shows the not-authorised message).
  - Leaves the app shell routing unchanged (TASK-007 mounts the dashboard), and keeps every existing Vitest test passing, updating detail fixtures only for the new C5 fields.
  - Shows a `test(red): AC-…` commit with the failing Vitest tests before the `feat(green)` commit. Saves `sprint-progress/TASK-002-red.log` and `TASK-002-green.log` naming every AC above.
  - Passes `npm test` (lcov refreshed) and `npm run build` with no TypeScript errors.

### TASK-003: Security hardening carry-overs (no Basic challenge, fixed problem instance, shared key sanitiser, exhausted policy-number sequence)
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-001]
- **acceptance_criteria**:
  - Confirms that the amendment `spec:` commit precedes its first commit.
  - Sends no `WWW-Authenticate` header on any 401: from the security entry point, a failed Basic filter or the exception handler. Updates the three existing `startsWith("Basic")` assertions (SecurityBaselineIT, RoleBoundaryIT, EndorsementSecurityIT) to assert that the header is absent.
  - Proves "NFR-04.1: …": anonymous and bad-credential requests to a protected GET and POST, and invalid credentials on `POST /api/quotes`, get 401 problem+json with no `WWW-Authenticate` header, while valid preemptive Basic credentials still succeed.
  - Sets every problem+json `instance` to the C7 URN, across the exception handler, the security handler, the body-limit filter and framework errors (400, 404, 405, 406, 413, 415, 500).
  - Proves "NFR-04.1: …" with a parameterised test: a marker path segment and query value are sent to the issue, endorse, preview, application-status, decision and override routes, triggering 400, 401, 403, 404, 405, 413 and 415. The marker appears in no response header or body, and every `instance` matches `^urn:policyforge:problem:[a-z-]+$`.
  - Wires the TASK-001 sanitiser into every place that names a caller-supplied key in `errors[].field`: quote risk inputs, the application body (including `kyc.` and `kyc.address.` keys), the underwriting decision and override, issue, and endorse and preview. Updates the Sprint 3 100-character assertions to the 64-character rule.
  - Proves the sanitiser per endpoint, titled with that endpoint's existing unknown-key AC (for example "AC-05.1: …" for issue and "AC-06.1: …" for endorse): a 5000-character key and a key containing `<`, `>`, quotes and spaces are echoed sanitised, and no other field or message changes.
  - Returns 503 problem+json with code `POLICY_NUMBER_SEQUENCE_EXHAUSTED` and a fixed detail (no SQL or constraint text) when the counter for the product and issue year is at 999999. The application stays APPROVED, no policy, version, term, transition or audit row is written, and the counter is unchanged.
  - Proves "AC-05.2: …" for the exhausted sequence, with test setup that sets the counter of an otherwise unused year with one JDBC statement (the counter table is not append-only), and shows that another product or year still issues.
  - Implements no underwriting re-check for sum-insured endorsements: G-6 was VETOED at the checkpoint, so SG-1 stays an accepted v1 limitation as the spec amendment records. No AC-06.8 test and no `SUM_INSURED_REQUIRES_UNDERWRITING` code are added, and the progress file records the veto
  - Keeps every existing test passing, logs IDs only, keeps NFR-03.1 log capture green, and passes every ArchUnit test.
  - Shows a `test(red): …` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-003-red.log` and `TASK-003-green.log` naming NFR-04.1, AC-05.1, AC-05.2 and AC-06.1, plus every other endpoint AC used for sanitiser tests.
  - Passes `./mvnw verify -Dskip.frontend=true`.

### TASK-004: Renewal backend: V10 term rule version, current paid term, RenewalService cycle, RenewalScheduler, the admin job endpoint, the pay endpoint and the detail additions
> Effort override: high → xhigh. This task owns idempotency under concurrent runs, per-policy transactions, pay and lapse races, the state machine on new paths and refreshed-premium money (P-13).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-001, TASK-003]
- **acceptance_criteria**:
  - Adds Flyway V10 (C8) and leaves V1–V9 byte-identical. Issuance writes term 1 with the quote's rule version. The term's rule version is set at construction with no setter, and marking an already-paid term paid is refused.
  - Applies the current paid term (G-5) in the endorsement service, the policy header, C2 and C3. Proves "AC-07.1: …": after an offer exists, the header dates, `nextPremiumDueDate`, the endorsement window and the AC-06.2 preview values (9490.00, 1330.85, 184, 365) are unchanged. After a payment, the header shows the renewal term's dates.
  - Implements the cycle of P-7 in the service layer. Each in-force policy is processed in its own transaction, offer then lapse. The offer premium comes from PremiumCalculator over the current version's stored risk inputs, with the rule version active on the run date, and the term records that version.
  - Applies the lapse through the policy aggregate and PolicyStateMachine, appending the transition with reason `GRACE_PERIOD_EXPIRED` and actor `system:renewal-scheduler`.
  - Exposes C1 for ADMIN only, consuming `application/json` only, returning the C1 body, and appending one `RENEWAL_CYCLE_RUN` audit entry per run (P-10).
  - Adds RenewalScheduler with cron `0 0 23 * * *` in the configured time zone, run date = today on the injected clock and actor `system:renewal-scheduler`. Disables its trigger in test configuration, and proves "AC-07.1: …" by invoking it directly on a fixed clock.
  - Proves "AC-07.1: …" on a fixed clock: for an ACTIVE TERM_LIFE policy (AC-04.1 profile, 2026-11-01..2027-10-31), the 2027-10-01 run creates term 2 (2027-11-01..2028-10-31, unpaid, 6850.00, ruleVersion 1), lists the policy in `offersCreated` and appends no transition. A 2027-09-30 run creates nothing.
  - Proves "AC-07.2: …": a second 2027-10-01 run creates no term and returns empty lists, and two concurrent runs for the same date leave exactly one term 2 per policy.
  - Proves "AC-07.3: …" in its own application context. The policy is issued at 2026-11-01 under v1. The G-1 fixture is published through the publish API with effectiveFrom 2027-09-01. The 2027-10-01 run creates the renewal at 7400.00 and ruleVersion 2. Term 1 still shows 6850.00 at ruleVersion 1, and version 1 still has ruleVersion 1. After the payment, version 2 has 7400.00 at ruleVersion 2.
  - Proves "AC-07.4: …": after the offer, the 2027-11-30 run leaves the policy in force and unlisted. The 2027-12-01 run makes it LAPSED with reason `GRACE_PERIOD_EXPIRED` and actor `system:renewal-scheduler`, and lists it in `lapsed`. A rerun adds no second transition.
  - Exposes C2 (CUSTOMER owner, `application/json` only) with the P-5 order and the G-3 writes in one transaction.
  - Proves "AC-07.5: …": paying on 2027-11-10 gives 200, with term 2 paid, status RENEWED, version 2 appended per G-3 and transition `RENEWAL_PAID` by customer1. Paying again gives 409 `NO_PENDING_RENEWAL`. Paying on 2027-12-01 before any run gives 409 `RENEWAL_PAYMENT_WINDOW_CLOSED`, and after the 2027-12-01 lapse it gives 409 invalid-policy-state, with nothing written in either case. A pay racing a lapse leaves exactly one of RENEWED or LAPSED, and the loser writes nothing.
  - Proves "AC-07.6: …": MOTOR and HOUSEHOLD policies issued 2026-11-01 are still in force after the 2027-11-15 run and LAPSED after the 2027-11-16 run. TERM_LIFE is in force after 2027-11-30 and LAPSED after 2027-12-01.
  - Extends C3 with `terms[].ruleVersion` and `pendingRenewal` (C5). Updates the existing detail key-set assertions to exactly the new contract and nothing else.
  - Proves "AC-10.4: …" with real operations: issue → offer → pay → endorse → offer → lapse, plus a variant ending in a rejected pay. Status equals the last transition's `to`, and the transition count equals the status-changing operations + 1, where offers add none.
  - Proves "NFR-04.1: …": on the job, anonymous gets 401, customer1 and underwriter1 get 403, and admin1 gets 200. On pay, anonymous gets 401, underwriter1 and admin1 get 403, and another customer and an unknown number get 404.
  - Proves for job and pay: unknown body keys get a sanitised 400, a malformed `date` gets 400 naming `date`, "NFR-04.3: …" `text/plain` gets 415, and "NFR-04.4: …" a body over 16 KB, including through a percent-encoded path, gets 413. Nothing changes in any of these cases.
  - Proves "NFR-03.1: …" by extending log capture to the cycle and pay: no captured line contains KYC values, an address line or health declaration text.
  - Keeps controllers thin, passes every ArchUnit test, and keeps every existing AC-05, AC-06 and AC-10 test passing.
  - Shows a `test(red): …` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-004-red.log` and `TASK-004-green.log` naming AC-07.1–07.6, AC-10.4, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4.
  - Passes `./mvnw verify -Dskip.frontend=true`.

### TASK-005: Cancellation backend: V11 append-only refund records, refund preview, cancel, refunds in the detail, AC-10.3 API half
> Effort override: high → xhigh. This task owns refund money, one-transaction atomicity, double-cancel races and the terminal-state boundary (P-13).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-004]
- **acceptance_criteria**:
  - Adds Flyway V11 (C8) and leaves V1–V10 byte-identical. The refund table is unique on the policy number, stores money as NUMERIC(19,2) and has no floating-point column.
  - Maps RefundRecordEntity as an immutable entity with no setters, and its repository has only `save` and finders. AppendOnlyArchTest gains existence checks for both, which fail in the red commit.
  - Exposes C3 (CUSTOMER owner) with the P-5 order. It writes nothing and delegates to the TASK-001 calculator, date rule and premium basis, using the current paid term's rule-version lifecycle for the fee and cooling-off.
  - Exposes C4 (CUSTOMER owner, `application/json` only) with the P-5 order and the G-4 reasons. One transactional method writes the RefundRecord, applies → CANCELLED and appends the transition.
  - Proves "AC-08.1: …" on a fixed clock: for a TERM_LIFE 6850.00 policy issued 2026-11-01 and cancelled on 2027-02-09, the preview and the cancel both give 4473.29, 265/365, fee 500.00 and the C5 formula. Every stored field equals the preview, and the response is 201 with the C4 body.
  - Proves "AC-08.1: …" for the premium basis: after a +1330.85 endorsement effective 2027-05-01, cancelling on 2027-06-01 refunds 2929.23 on basis 8180.85.
  - Proves "AC-08.1: …" for the date window: before the term start or after the term end gets 400 `CANCELLATION_DATE_OUT_OF_TERM`, naming `cancellationDate` on cancel and `date` on preview. A malformed date, a missing or unknown `reason`, or an unknown key (sanitised) gets 400. In grace, today's date gets 400.
  - Proves "AC-08.1: …" after an early renewal payment (G-5): a date in term 1 gets 400, and a date in the renewal term refunds against the renewal premium with no cooling-off.
  - Proves "AC-08.2: …": cancelling on 2026-11-11 gives 6850.00, fee 0.00 and coolingOff true. MOTOR cancelled on day 7 (cooling-off 7) gives the pro-rata 14609.45.
  - Proves "AC-08.3: …": cancelling on 2027-10-31 stores amount 0.00 and the status becomes CANCELLED.
  - Proves "AC-08.4: …": after a cancel, the status is CANCELLED and exactly one refund record exists. A second cancel gets 409 invalid-policy-state and there is still one record. Two concurrent cancels give exactly one 201 and one 409, and one record. A preview of a CANCELLED policy gets 409.
  - Proves "AC-08.4: …" for atomicity: test configuration injects a failure at the transition write after the refund insert reached the database. Afterwards no refund row exists and the status and transition count are unchanged. Production code contains no test-only hook.
  - Proves "AC-10.3: …": a policy LAPSED by the real cycle gets 409 invalid-policy-state on cancel and on preview, and no refund record is written. This closes the Sprint 3 P-6 carry-over.
  - Proves "AC-10.2: …" with a really cancelled policy: the endorsement and its preview get 409 and nothing is written.
  - Proves "AC-10.4: …" with sequences that include a real cancel (issue → endorse → cancel, then rejected endorse, pay and cancel): the counts are unchanged by the rejected operations.
  - Extends C3 `refunds[]` with the C5 Refund shape, in append order.
  - Proves "NFR-04.1: …" for preview and cancel: anonymous 401, underwriter1 and admin1 403, another customer and an unknown number 404.
  - Proves "NFR-04.3: …" (`text/plain` cancel gets 415) and "NFR-04.4: …" (a body over 16 KB gets 413, including through a percent-encoded path), and proves "NFR-03.1: …" (log capture of preview and cancel has no PII). Nothing is written in any of these cases.
  - Keeps controllers thin, passes every ArchUnit test, and keeps every existing test passing.
  - Shows a `test(red): …` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-005-red.log` and `TASK-005-green.log` naming AC-08.1–08.4, AC-10.2, AC-10.3, AC-10.4, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4.
  - Passes `./mvnw verify -Dskip.frontend=true`.

### TASK-006: Admin portfolio dashboard API and the synthetic sample-policy seed
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-005]
- **acceptance_criteria**:
  - Exposes C6 for ADMIN only, using the TASK-001 portfolio rules. It returns exactly the spec's keys, always lists all three products, renders money as scale-2 strings, sorts per P-11, and gives 400 naming `asOf` for a malformed `asOf`.
  - Proves "AC-11.5: …": customer1, customer2 and underwriter1 get 403 problem+json, anonymous gets 401 ("NFR-04.1: …"), and admin1 gets 200.
  - Implements the C10 seed through the quote, application and issuance services, with a backdated issuance path in the service layer that no web class can reach (an ArchUnit rule, or package-private visibility), followed by one renewal cycle for T. It is idempotent by its synthetic marker, uses synthetic data only, and logs IDs only.
  - Proves "AC-11.1: …" in its own context on the fixed clock. The seed holds S1–S6 with the C10 dates and states. The admin dashboard returns `activePoliciesByProduct` equal to the in-force seed policies per product, counted from the seed's own records (2/2/2), and `premiumCollected` 13700.00 / 30300.00 / 22700.00, total 66700.00. The pipeline contains S1 with renewalPremium 6850.00 and paid false, `lapseForecast` is `{ count: 1, policies: [S5] }`, and running the seed again adds nothing.
  - Proves "AC-11.2: …" with exact decimal string equality and no tolerance, in its own context or as an exact before/after difference: two paid TERM_LIFE terms, a +1330.85 endorsement and a 4473.29 refund move TERM_LIFE by exactly 10557.56, `total` equals the sum of the three products, and a negative adjustment (a credit) lowers the product's figure.
  - Proves "AC-11.3: …" on the fixed clock: a policy whose term ends in 20 days is in the pipeline. Its premium is null and paid false before the cycle, 6850.00 and false after the offer, and paid true after payment. A policy ending in 90 days is absent, and the 60-day and 61-day boundaries are in and out.
  - Proves "AC-11.4: …": a policy past termEnd with an unpaid renewal, within grace, is in `lapseForecast` with the right count. It leaves the forecast after payment and after the lapse, and a policy before its termEnd is absent.
  - Makes the existing tests seed-aware without weakening any assertion (P-15), and keeps the AC-09.7 seed test, seed idempotency and policy-number tests passing.
  - Proves "NFR-03.1: …" by extending log capture to the dashboard and the seed: no captured line contains seed KYC names, Aadhaar, PAN or an address line.
  - Keeps controllers thin and passes every ArchUnit test.
  - Shows a `test(red): …` commit before the `feat(green)` commit. Saves `sprint-progress/TASK-006-red.log` and `TASK-006-green.log` naming AC-11.1–11.5, NFR-03.1 and NFR-04.1.
  - Passes `./mvnw verify -Dskip.frontend=true`.

### TASK-007: App shell route for the Admin Dashboard, Playwright journeys (desktop + mobile), committed visual snapshots and sprint-wide verification
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-002, TASK-006]
- **acceptance_criteria**:
  - Mounts the Admin Dashboard at `#/admin/dashboard` behind the sign-in gate and adds the `nav-dashboard` link. Includes a Vitest routing test titled "AC-11.1: …", and keeps every existing routing test passing.
  - Resolves any mismatch between the SPA and the API in favour of the specs and contracts C1–C10, and lists each change in the progress file.
  - Adds Playwright tests to `policy-lifecycle.spec.ts`: "AC-07.1: …" (the job, called through the API with date = own expiry − 30, lists the policy in `offersCreated`, and the Renew panel shows start = expiry + 1 and 6850.00) and "AC-07.5: …" (Pay shows RENEWED and hides the Renew panel; the Status tab ends with RENEWAL_PAID; the Versions tab shows v2).
  - Adds Playwright tests to `policy-lifecycle.spec.ts`: "AC-08.2: …" (a same-day cancel previews 6850.00, fee 0.00 and cooling-off, then confirms to CANCELLED with one 6850.00 row in the Refunds tab) and "AC-08.4: …" (after cancelling, no Cancel, Renew or Endorse action is offered, a second cancel through the API gets 409, and the Refunds tab still has one row).
  - Adds `dashboard.spec.ts`: "AC-11.1: …" (admin1's tiles and per-product table equal the page's own dashboard response; the tiles stack in one column on mobile-chromium and sit side by side on desktop-chromium), "AC-11.3: …" (asOf = own term end − 20 lists the policy, and − 90 does not), "AC-11.4: …" (asOf = own term end + 1 lists it in the forecast) and "AC-11.5: …" (customer1 sees the not-authorised message).
  - Follows P-12: never runs the cycle with a date later than its own policy's expiry − 30 days, and never asserts seed state.
  - Uses `data-testid` selectors and web-first assertions with no `waitForTimeout`. Each test creates its own data and acts on it by number. Every test passes on `desktop-chromium` and `mobile-chromium` running in parallel, and the Sprint 1–3 journeys and their snapshots still pass unchanged.
  - Adds visual snapshot assertions for the Renew panel with an offer, the cancel refund preview, the Refunds tab and the dashboard KPI tiles, with dates, policy numbers and dashboard values masked. Baselines are committed under `frontend/e2e/__snapshots__/`, platform-suffixed, and a second consecutive run passes without updating snapshots.
  - Passes a full `./mvnw verify` (SPA packaged, frontend not skipped) with the JaCoCo thresholds unchanged at 80% domain and 70% overall. Also passes `npm test` (lcov refreshed and committed) and `npm run build`.
  - Reports COVERED (not PARENT-ONLY or MISSING) in `python scripts/ac_audit.py --offline --no-write` for AC-07.1–07.6, AC-08.1–08.5, AC-10.2–10.4, AC-11.1–11.5, plus NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4, and still for every Sprint 1–3 ID (81/81).
  - Confirms from `git log 12f95c3..feat/renewal-cancellation` that the amendment `spec:` commit precedes every code commit, and that every ID in the previous criterion has a `test(red)` commit naming it before a `feat(green)` commit.
  - Confirms that `dev` is still `12f95c3` and `main` is still `8e265d6`, locally and in `git ls-remote origin`, and that `origin/feat/renewal-cancellation` does not exist (nothing pushed).
  - Confirms that `python scripts/redact_evidence.py --check` exits 0 over every `sprint-progress/` log.
  - Records in the progress file every command run and its result (verify, npm test, build, Playwright on both projects twice, the AC audit, the git checks), the spec gaps SG-1 to SG-7, the G-6 veto (SG-1 accepted), and that port 8080 is free.
  - Shows a `test(red): AC-…` commit with the E2E and routing tests failing before the dashboard route exists, then the `feat(green)` commit. Saves `sprint-progress/TASK-007-red.log` and `TASK-007-green.log` showing both Playwright projects.

## Execution Schedule

### Parallel Batch (no dependencies — run simultaneously via Agent Teams)
- TASK-001
- TASK-002

### Sequential Tasks (ordered by dependency)
1. TASK-003 (depends on TASK-001)
2. TASK-004 (depends on TASK-001, TASK-003)
3. TASK-005 (depends on TASK-004)
4. TASK-006 (depends on TASK-005)
5. TASK-007 (depends on TASK-002, TASK-006)
