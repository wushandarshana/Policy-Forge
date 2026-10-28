---
name: policy-state-machine-generator
description: Generate (or regenerate) PolicyForge's PolicyStatus enum, PolicyStateMachine, InvalidPolicyStateException and the exhaustive AC-10 transition-matrix test directly from the table in specs/policy-issuance_spec.md. Use when the state machine spec changes or the AC-10 matrix test is missing.
---

# Policy state machine generator

The state machine must be a **mechanical translation** of the spec table, so spec and code can't drift.

## Steps
1. Parse the `## State machine (AC-10)` table in `specs/policy-issuance_spec.md`. Rows are "from" states (including
   `(new)`), columns are "to" states, ✅ means allowed and ❌ means forbidden.
2. Generate `domain/PolicyStatus.java`: an enum of ACTIVE, ENDORSED, RENEWED, LAPSED, CANCELLED, with
   `isInForce()` (true for ACTIVE, ENDORSED, RENEWED) and `isTerminal()`.
3. Generate `domain/PolicyStateMachine.java`:
   - A `private static final Map<PolicyStatus, Set<PolicyStatus>> ALLOWED`, built with `EnumMap` / `EnumSet` and
     populated literally from the table. Represent the `(new)` row as a separate `INITIAL_ALLOWED = EnumSet.of(ACTIVE)`.
   - `public PolicyStatus transition(PolicyStatus from, PolicyStatus to)`, where `from == null` means new. It returns
     `to` or throws `InvalidPolicyStateException(from, to)`.
   - `public boolean canTransition(PolicyStatus from, PolicyStatus to)`.
   - Pure Java only (domain rules).
4. Generate `domain/InvalidPolicyStateException.java extends PolicyForgeDomainException`, with fields `from` and `to`
   and the message `"Invalid policy state transition: %s -> %s"`.
5. Generate `domain/PolicyStateMachineTest.java`:
   - `@Tag("AC-10")`, plus `@ParameterizedTest(name = "AC-10.1: {0} -> {1} allowed={2}")` and `@MethodSource` that emits
     **every** (from, to) pair from the table: 6 rows × 5 columns = 30 cases.
   - Allowed cases assert the return value. Forbidden cases assert
     `assertThatThrownBy(...).isInstanceOf(InvalidPolicyStateException.class)`.
6. Run `./mvnw -q test -Dtest=PolicyStateMachineTest`. Expect all 30 to pass after green, and fail before
   implementation (red).

## Guardrails
- If the table changes, change the **spec first**, then regenerate. Never hand-edit `ALLOWED` to differ from the spec.
- The service layer must call `transition` before appending a `PolicyStatusTransition`. Grep for direct
  `setStatus`/`status =` assignments outside `PolicyEntity.applyTransition` and flag them.
