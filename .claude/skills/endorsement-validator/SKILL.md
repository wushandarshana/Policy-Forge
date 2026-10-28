---
name: endorsement-validator
description: Validate PolicyForge endorsement requests and generate the EndorsementValidator rule matrix tests from specs/endorsement_spec.md (state, product applicability, nominee limits, sum-insured bounds, effective-date window). Use when implementing or reviewing AC-06 endorsement logic.
---

# Endorsement validator

## Validation matrix (from `specs/endorsement_spec.md`)

| # | Type | Condition | Expected |
|---|---|---|---|
| E1 | any | policy status CANCELLED or LAPSED | `InvalidPolicyStateException` → 409 |
| E2 | any | effectiveDate outside current term | 400 `EFFECTIVE_DATE_OUT_OF_TERM` |
| E3 | any | effectiveDate before the latest endorsement's effectiveDate | 400 `EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT` |
| E4 | ADD_NOMINEE | product ≠ TERM_LIFE | 400 `NOMINEE_NOT_SUPPORTED` |
| E5 | ADD_NOMINEE | already 3 nominees | 400 `NOMINEE_LIMIT_REACHED` |
| E6 | ADD_NOMINEE | total share + new share > 100 | 400 `NOMINEE_SHARE_EXCEEDED` |
| E7 | ADD_NOMINEE | relationship not in {SPOUSE, CHILD, PARENT, SIBLING, OTHER} | 400 `NOMINEE_RELATIONSHIP_INVALID` |
| E8 | CHANGE_SUM_INSURED | new value outside rule-version min/max | 400 `SUM_INSURED_OUT_OF_RANGE` |
| E9 | CHANGE_SUM_INSURED | new value equals current | 400 `SUM_INSURED_UNCHANGED` |
| E10 | CHANGE_ADDRESS | postcode not 6 digits | 400 `POSTCODE_INVALID` |
| E11 | any valid | — | passes; returns a normalised `EndorsementCommand` |

## How to use
1. **Generating tests:** create `domain/EndorsementValidatorTest.java`, `@Tag("AC-06")`, with one
   `@ParameterizedTest` row per matrix line E1–E11. Use a fixed `PolicySnapshot` fixture (TERM_LIFE,
   2026-11-01..2027-10-31, SI 5,000,000, 1 nominee at 60%) and a MOTOR fixture for E4.
2. **Reviewing an implementation:** check each row maps to exactly one validation branch, that error codes match
   exactly, and that **all** violations are collected (not only the first), except E1, which short-circuits.
3. `EndorsementValidator` is pure domain (no repositories). The service loads the snapshot and passes it in.

## Atomicity reminder
Validation happens **before** any write. The service then performs endorsement + version + transition in one
transaction (AC-06.3). Validation passing doesn't excuse a missing transaction test.
