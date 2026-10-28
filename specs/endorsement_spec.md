# Feature Spec — Endorsements

**Parent:** [`app_spec.md`](app_spec.md) · **ACs:** AC-06 (plus AC-10 for state) · **NFR:** NFR-01, NFR-02
**Domain classes:** `EndorsementValidator`, `EndorsementType`, `EndorsementPremiumAdjuster`

## Purpose
Let a policyholder change an in-force policy mid-term. Each change produces an **immutable endorsement** and a **new
policy version**, written atomically.

## Request
`POST /api/policies/{number}/endorsements` (owner):

| `type` | Payload | Re-rates? |
|---|---|---|
| `CHANGE_ADDRESS` | `{ "address": { "line1", "city", "postcode" } }` | No |
| `ADD_NOMINEE` | `{ "nominee": { "name", "relationship", "sharePercent" } }` | No |
| `CHANGE_SUM_INSURED` | `{ "newSumInsured": "7000000.00" }` | Yes |

Optional `effectiveDate`, default today. It must be within the current term and not before the latest endorsement's
effective date.

## Validation rules (`EndorsementValidator`)
- The policy status must be in force (ACTIVE, ENDORSED or RENEWED). Otherwise `InvalidPolicyStateException` (409).
- `ADD_NOMINEE`: `TERM_LIFE` only (400 `NOMINEE_NOT_SUPPORTED` for other products). There can be at most 3 nominees.
  The total `sharePercent` across nominees must be ≤ 100. `relationship` ∈ {SPOUSE, CHILD, PARENT, SIBLING, OTHER}.
- `CHANGE_SUM_INSURED`: the new value must be within the rule version's min/max and differ from the current value. For
  MOTOR it replaces `vehicleValue`. For HOUSEHOLD the payload uses `newBuildingSumInsured` and/or
  `newContentsSumInsured`.
- `CHANGE_ADDRESS`: the postcode must be 6 digits.

## Premium adjustment (CHANGE_SUM_INSURED)
- `newAnnualPremium` = re-quote with the modified risk inputs using the **policy's own rule version** (not the latest).
- `adjustment = (newAnnualPremium − currentAnnualPremium) × unexpiredDays / termDays`, rounded HALF_UP to 2 dp.
  `unexpiredDays = DAYS.between(effectiveDate, termEnd + 1)` and `termDays = DAYS.between(termStart, termEnd + 1)`.
- A positive adjustment is an additional premium charge (simulated as paid). A negative adjustment is a credit. Either
  way the adjustment is stored on the endorsement.

## Atomicity
In one `@Transactional` service method:
1. Append an `Endorsement` (id, policyNumber, sequenceNo, type, payload JSON, before/after version numbers, adjustment,
   effectiveDate, actorId, createdAt).
2. Append a `PolicyVersion` (n+1) with the updated attributes.
3. Set the policy `currentVersionNo` and append the transition → ENDORSED.

If any step fails, nothing is persisted. Endorsement and version entities have no setters and no repository deletes.

## Acceptance Criteria

- **AC-06.1** Given an ACTIVE policy, when a `CHANGE_ADDRESS` endorsement is applied, then one endorsement record exists
  with before version 1 and after version 2, policy version 2 has the new address, version 1 still has the old
  address, and the status is `ENDORSED`.
- **AC-06.2** Given a TERM_LIFE policy (premium 6850.00, term 2026-11-01..2027-10-31), when sum insured changes from
  5,000,000 to 7,000,000 effective 2027-05-01, then the new annual premium is 9490.00 and the adjustment is
  **(9490.00 − 6850.00) × 184 / 365 = 1330.85**.
- **AC-06.3** Given a failure injected after the endorsement insert but before the version insert, when the endorsement
  is applied, then neither the endorsement nor the version is persisted and the policy status is unchanged
  (atomicity).
- **AC-06.4** Given a MOTOR policy, when `ADD_NOMINEE` is requested, then the response is 400
  `NOMINEE_NOT_SUPPORTED`.
- **AC-06.5** Given nominees totalling 80%, when a nominee with 30% is added, then 400 `NOMINEE_SHARE_EXCEEDED`.
- **AC-06.6** Given three endorsements on a policy, then their `sequenceNo` values are 1, 2 and 3, and the history API
  returns them in order with linked versions 2, 3 and 4.
- **AC-06.7** Given an existing endorsement, then no API or repository method can change or delete it (ArchUnit
  append-only rule, and the absence of PUT/PATCH/DELETE endpoints).

## UI
The **Endorse** page (from policy detail) has a type selector and a dynamic form. For a sum-insured change it previews
the new premium and adjustment before confirming. The endorsement history is shown on the policy detail.
