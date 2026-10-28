---
name: spec-to-test-generator
description: Turn the Acceptance Criteria (AC-NN.M Given/When/Then) of a PolicyForge spec into failing, AC-tagged JUnit 5 / Vitest / Playwright tests that use the spec's exact numbers — the TDD red step. Use at the start of every sprint task and whenever a spec AC is added or changed.
---

# Spec to test generator

## Input
A spec file path (e.g. `specs/underwriting_spec.md`) and optionally a list of AC IDs. Default: all ACs in the file.

## Procedure
1. Extract every bullet that matches `**AC-NN.M**` under `## Acceptance Criteria`. Split it into Given, When and Then.
2. Choose the **lowest test level** that can prove the Then:

   | Then is about | Test level | Location |
   |---|---|---|
   | A pure calculation or rule outcome | Domain unit (no Spring) | `backend/src/test/java/.../domain/` |
   | Persistence, append-only rows, transactions | `@DataJpaTest` / `@SpringBootTest` (`*IT`) | `.../service/` |
   | HTTP status, role 401/403, problem+json | `@WebMvcTest` | `.../web/` |
   | What the user sees or responsiveness | Playwright | `frontend/e2e/` |
   | A component render or state | Vitest | `frontend/src/**/*.test.tsx` |

3. Name and tag the test:
   - JUnit: `@Tag("AC-NN")`, `@DisplayName("AC-NN.M: <Then in a few words>")`.
   - Vitest and Playwright: `test('AC-NN.M: <Then>', …)`.
4. Use the **exact literals** from the spec. Money is compared with `isEqualByComparingTo("…")` and a `scale()==2`
   assertion. Dates use `Clock.fixed(Instant.parse("<date>T00:00:00Z"), ZoneOffset.UTC)`.
5. Matrices (AC-04.6, AC-10.1, the E1–E11 endorsement matrix): use `@ParameterizedTest` with one row per rule or cell.
   Rules with nested thresholds (e.g. BMI > 35 and > 45) list all the codes that fire together (KD-004).
6. Create stub production types only if needed for compilation. Stub methods
   `throw new UnsupportedOperationException("red: AC-NN")`.
7. Run the tests. Confirm they **fail** with assertion or `UnsupportedOperationException`, not compile errors.
8. Commit `test(red): AC-NN.M[, …] <summary>`.

## Output checklist
- [ ] Every AC in scope has at least 1 test containing its ID.
- [ ] No test asserts on implementation details (private fields, log text other than for NFR-03).
- [ ] `/ac-coverage` shows the ACs in scope as covered (by failing tests, at this stage).
