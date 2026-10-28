# Feature Spec — Policy Issuance & Policy State Machine

**Parent:** [`app_spec.md`](app_spec.md) · **ACs:** AC-05, AC-10 · **NFR:** NFR-02
**Domain classes:** `PolicyNumberFormat` (pure formatting of product, year and sequence), `PolicyStateMachine`,
`PolicyStatus`, `InvalidPolicyStateException`. **Service:** `PolicyNumberGenerator` (reads the DB sequence, calls
`PolicyNumberFormat`).

## Issuance (AC-05)

`POST /api/applications/{id}/issue` (CUSTOMER, owner), body `{ "effectiveDate": "2026-11-01" }`. The effective date is
optional and defaults to today. It must be between today and today + 60 days.

- Only `APPROVED` applications can be issued. Anything else gives 409. An application can be issued only once.
- The policy number format is `PF-<TL|MO|HH>-<YYYY>-<NNNNNN>`. `YYYY` is the issue year. `NNNNNN` is a zero-padded
  per-product, per-year DB sequence. A unique constraint guarantees no duplicates.
- Created:
  - `Policy`: number, productCode, customerId, status `ACTIVE`, currentVersionNo 1.
  - `PolicyVersion` v1: an immutable snapshot of sumInsured, annualPremium, riskInputs, insured address, nominees
    (empty), ruleVersion, effectiveDate, expiryDate = effectiveDate + `termMonths` − 1 day.
  - `PolicyTerm` #1: start, end, premium, `paid = true`. Initial premium payment is simulated at issuance.
  - `PolicyStatusTransition`: `null → ACTIVE`, with actor and timestamp.
- Sum insured and premium are copied from the quote, so the policy uses the same rule version as the quote.

## State machine (AC-10)

`PolicyStateMachine.transition(current, target)` is a pure domain function. It returns the target or throws
`InvalidPolicyStateException(current, target)`.

| From \ To | ACTIVE | ENDORSED | RENEWED | LAPSED | CANCELLED |
|---|---|---|---|---|---|
| (new) | ✅ | ❌ | ❌ | ❌ | ❌ |
| ACTIVE | ❌ | ✅ | ✅ | ✅ | ✅ |
| ENDORSED | ❌ | ✅ | ✅ | ✅ | ✅ |
| RENEWED | ❌ | ✅ | ✅ | ✅ | ✅ |
| LAPSED | ❌ | ❌ | ❌ | ❌ | ❌ |
| CANCELLED | ❌ | ❌ | ❌ | ❌ | ❌ |

- "In force" statuses are ACTIVE, ENDORSED and RENEWED. LAPSED and CANCELLED are terminal.
- Every successful transition appends a `PolicyStatusTransition` row (from, to, reason, actorId, timestamp). The policy's
  `status` column always equals the latest transition's `to`. The transition rows are never updated or deleted.
- The REST layer maps `InvalidPolicyStateException` to **409** problem+json with `type = /problems/invalid-policy-state`.

## Viewing
- `GET /api/policies/mine` lists the customer's policies: number, product, status, sum insured, premium, next premium
  due date (current term end + 1 day), and the endorsement count.
- `GET /api/policies/{number}` returns the policy detail, the version history, transitions, endorsements, terms and
  refunds.

## Acceptance Criteria

- **AC-05.1** Given an APPROVED TERM_LIFE application, when it is issued on 2026-11-01, then the policy has a number
  matching `^PF-TL-2026-\d{6}$`, effective date 2026-11-01, expiry 2027-10-31, sum insured and premium equal to the
  quote's, and status `ACTIVE`.
- **AC-05.2** Given 50 applications issued concurrently (thread pool of 8), then all 50 policy numbers are unique.
  Given 5 applications issued one after another, then their sequence numbers are contiguous (n, n+1, …, n+4).
- **AC-05.3** Given a REFERRED or DECLINED application, when issue is called, then the response is 409. Given an
  application already issued, then 409.
- **AC-05.4** Given an issued policy, then exactly one `PolicyStatusTransition (null → ACTIVE)` and one `PolicyVersion`
  v1 exist.
- **AC-10.1** For every pair in the transition matrix, `PolicyStateMachine` allows exactly the ✅ cells and throws
  `InvalidPolicyStateException` for every ❌ cell (parameterised test, 30 cases).
- **AC-10.2** Given a CANCELLED policy, when an endorsement is requested, then the response is 409 with
  `invalid-policy-state`, and no endorsement or version row is written.
- **AC-10.3** Given a LAPSED policy, when cancellation is requested, then 409 and no refund record is written.
- **AC-10.4** Given any sequence of valid operations, then the policy's `status` equals the last transition's `to`, and
  the transition count equals the number of operations + 1.

## UI
A **My Policies** page shows cards or a table of policies with status badge, premium due date and an endorsement count.
The detail view shows history tabs: Versions, Endorsements, Status, Refunds.
