# Business Case: PolicyForge

**Business case ID:** BC-AINE-004 · **Domain:** Insurance (General / Life) · **Client:** Horizon Insurance (fictional)

## 1. Problem

Horizon Insurance sells term life, motor and household cover through three separate legacy policy administration
systems. Each product has its own rating spreadsheets, underwriting checklists and servicing screens. This causes
four recurring problems:

- **Inconsistent pricing.** Premiums are worked out partly by hand from rate tables that live outside version control.
  The same applicant can get different quotes on different days, and nobody can say which rate table produced a
  given premium.
- **Slow, opaque underwriting.** Every application, simple or not, goes to an underwriter's inbox. Applicants can't
  see where their case is or why it was referred or declined.
- **Fragile servicing.** Endorsements (address change, nominee change, sum-insured change) overwrite policy records
  in place. When a dispute comes in, the business can't rebuild what the policy looked like on a given date.
- **Leaking revenue at renewal and cancellation.** Renewals are tracked manually, lapsed policies stay "active" in the
  books, and pro-rata refunds are calculated in spreadsheets with floating-point rounding errors.

Rate changes need an IT release, so launching or repricing a product takes weeks.

## 2. Target users

| Persona | Needs |
|---|---|
| **Customer / policyholder** | Get an instant, explainable quote; apply online; track underwriting status; see policies, premium due dates and history; request endorsements; renew or cancel and see the refund |
| **Underwriter** | A work queue of referred cases with all risk inputs and the rule-generated reason codes; approve or decline with a reason; override auto-declines when justified |
| **Admin / operations manager** | Portfolio view: active policies by product, premium collected, renewal pipeline, lapse forecast; an audit trail of every manual decision |
| **Product manager** | Add or reprice a product by publishing a new rule-set version, with no code release; published versions can't be altered |
| **Compliance / audit** | Rebuild any policy at any point in time; prove which rule version priced and underwrote it; confirm PII never reaches logs |

## 3. Value proposition

PolicyForge is one configurable platform for the whole quote-to-bind-to-renewal lifecycle across products:

- **Rules as versioned data.** Each product's rating factors, underwriting thresholds, reason codes, grace periods and
  refund rules live in a versioned rule file. A new version is published, never edited. Every quote, decision and
  policy records the rule version it used.
- **Straight-through processing.** Clean applications are auto-bound. Only genuinely risky cases reach an
  underwriter, each with reason codes that explain why.
- **Immutable history.** Policy versions, endorsements, state transitions, refunds and audit entries are append-only,
  so the policy can be rebuilt as it stood on any date.
- **Exact money.** Premiums and refunds use fixed-point decimals with one documented rounding rule.
- **Automated lifecycle.** An end-of-day job generates renewal offers and lapses policies whose premium is unpaid
  after the grace period.

## 4. Success metrics

| Metric | Target | How measured |
|---|---|---|
| Quote determinism | 100% identical premium for identical input and rule version | AC-01 tests; repeated-quote property test |
| Straight-through processing rate | ≥ 60% of clean synthetic applications auto-bound | Underwriting decision counts (dashboard) |
| Time to launch or reprice a product | Same day, with no code change | Publish a new rule version via the catalog API (AC-02) |
| Endorsement traceability | 100% of endorsements linked to a before/after policy version | AC-06 tests; version history API |
| Refund accuracy | 0.00 variance against the reference pro-rata formula | AC-08 tests on boundary dates |
| Lapse hygiene | 0 policies ACTIVE after expiry + grace period with unpaid renewal | AC-07 scheduler tests; dashboard lapse count |
| Audit completeness | 100% of underwriter and admin actions carry an actor ID | NFR-04 tests |
| PII in logs | 0 occurrences | NFR-03 log-capture test and `pii-redaction-check.sh` hook |

## 5. Domain rules

The authoritative rules are in `specs/`. These are the essentials.

### 5.1 Products and rule versions
- Three products at launch: **TERM_LIFE**, **MOTOR**, **HOUSEHOLD**. Each has distinct rating factors and underwriting
  rules.
- A rule set is identified by `(productCode, version)` and has an `effectiveFrom` date. The **active** version is the
  highest published version whose `effectiveFrom` is on or before today.
- Published rule versions are **immutable**. A change means publishing version N+1.

### 5.2 Quote
- Premium = rating formula over the insured profile using the active rule version. Same input and same version
  always give the same premium.
- Money uses `BigDecimal` with intermediate scale 10. The final premium is rounded to 2 dp with HALF_UP.
- Each quote records the rule version, a breakdown of factors, and a 30-day validity.

### 5.3 Application and underwriting
- An application needs a valid quote, a KYC stub (synthetic Aadhaar-format and PAN-format IDs, date of birth, address)
  and the product's risk inputs. Inputs are validated against the product rule set. Valid applications move to
  `UNDERWRITING`.
- Underwriting returns **AUTO_BIND**, **MANUAL_REVIEW** or **DECLINE**, each with reason codes from the active rule
  version (e.g. `UW-TL-002 Smoker with sum insured above threshold`).
- Precedence: any DECLINE rule gives DECLINE; otherwise any REFER rule gives MANUAL_REVIEW; otherwise AUTO_BIND.
- Underwriters approve or decline MANUAL_REVIEW cases. Admins can **override a DECLINE**, which needs a reason code and
  comment. Every manual action is audited with the actor ID.

### 5.4 Policy issuance and state
- Issuing a policy assigns a unique number `PF-<PRODUCT>-<YYYY>-<6-digit sequence>` and records the effective date,
  expiry (effective + term), sum insured, premium, and status **ACTIVE**.
- Allowed transitions: ACTIVE / ENDORSED / RENEWED → ENDORSED, LAPSED, CANCELLED, RENEWED. LAPSED and CANCELLED are
  terminal. Any other transition raises `InvalidPolicyStateException`.

### 5.5 Endorsements
- Supported types: change address, add nominee, change sum insured. A sum-insured change re-rates the premium using
  the policy's rule version and charges or credits the pro-rata difference.
- An endorsement creates an immutable endorsement record and a new policy version in one transaction. Partial
  updates are impossible.

### 5.6 Renewal, lapse and cancellation
- A renewal offer (new term, refreshed premium from the currently active rule version) is generated a configurable
  number of days before expiry (default 30).
- If the renewal premium is paid, the policy moves to **RENEWED** with a new term. If it is still unpaid after
  expiry + the product's **grace period** (Term Life 30 days, Motor and Household 15 days), the policy auto-lapses to
  **LAPSED**.
- Cancellation refunds `premium × unexpiredDays / termDays − cancellationFee`, floored at 0. Cancelling within the
  product's cooling-off period gives a full refund with no fee. The policy moves to **CANCELLED** and an append-only
  refund record is written.

## 6. Scope

**In scope:** customer journeys (quote, apply, view, endorse, renew, cancel); underwriter workbench; product catalog
manager; renewal scheduler; admin portfolio dashboard.

**Out of scope:** real KYC verification, bureau, health or vehicle record integrations, payment gateways (payments are
simulated), reinsurance, distribution and commission, production deployment, secret management, multi-region.

## 7. Constraints

- Synthetic data only. Seed data includes 3 products and sample policies with fictional customers.
- Every line of production code is generated by Claude Code agents under human supervision (see `CLAUDE.md`).
