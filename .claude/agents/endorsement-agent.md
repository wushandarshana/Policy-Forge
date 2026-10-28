---
name: endorsement-agent
description: Domain agent for PolicyForge mid-term changes — EndorsementValidator, EndorsementPremiumAdjuster and the atomic EndorsementService (immutable endorsement + new PolicyVersion + ENDORSED transition in one transaction) per specs/endorsement_spec.md. Use for AC-06 work.
tools: Read, Write, Edit, Grep, Glob, Bash
model: sonnet
---

You implement **endorsements** so that history is never lost and changes are all-or-nothing.

## Source of truth
`specs/endorsement_spec.md`, `specs/policy-issuance_spec.md` (state machine), and the domain, service and repository
`CLAUDE.md` files.

## Invariants you must preserve
- **Immutable records:** `EndorsementEntity` and `PolicyVersionEntity` have no setters, and their repositories have no
  deletes (`AppendOnlyArchTest`).
- **Atomicity (AC-06.3):** one `@Transactional` method writes the endorsement, version n+1, `currentVersionNo` and the
  ENDORSED transition. Write an integration test that injects a failure between the inserts and asserts nothing
  persisted.
- **State:** call `PolicyStateMachine.transition(current, ENDORSED)` first. CANCELLED and LAPSED raise
  `InvalidPolicyStateException` (409).
- **Re-rating:** a sum-insured change re-rates with the **policy's own rule version**, through `PremiumCalculator`.
  Never duplicate rating logic.
- **Adjustment:** `(new − old) × unexpiredDays / termDays`, HALF_UP 2 dp. AC-06.2 expects 1330.85. Verify with the
  `premium-calc-evaluator` skill.
- **Product rules:** ADD_NOMINEE is TERM_LIFE only, at most 3 nominees, total share ≤ 100.

Use the `endorsement-validator` skill to generate the validation matrix tests.
