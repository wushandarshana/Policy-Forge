---
description: Report which spec Acceptance Criteria (AC-NN.M) have no tagged test, and which tests reference unknown ACs.
argument-hint: "[spec-file … | all]"
allowed-tools: Read, Grep, Glob, Bash(git:*), Bash(python:*)
---

# /ac-coverage

Produce an AC → test traceability report for PolicyForge.

Scope: `$ARGUMENTS` (default `all`, meaning every `specs/*_spec.md`).

1. **Collect ACs.** In each spec in scope, find every `**AC-NN.M**` (and `**NFR-NN.M**` in `specs/app_spec.md`). Record
   the ID, spec file and a 10-word summary.
2. **Collect test references.** Grep for `AC-\d{2}(\.\d+)?` and `NFR-\d{2}(\.\d+)?` in:
   - `backend/src/test/java/**` (`@Tag("AC-NN")`, `@DisplayName("AC-NN.M: …")`, parameterised names);
   - `frontend/src/**/*.test.ts(x)` and `frontend/e2e/**/*.spec.ts` (test titles).
   A test tagged only `AC-NN` (no `.M`) counts as covering **any** `AC-NN.M` only if its DisplayName or a parameter row
   names the sub-ID. Otherwise mark it as **parent-only**.
3. **Build the matrix:** `AC id | spec | tests (file:line) | level (domain/service/web/e2e/ui) | status`. Status is
   COVERED, PARENT-ONLY or MISSING.
4. **Orphans:** list test references to AC IDs that don't exist in any spec. These are spec/code drift.
5. **Summary line:** `Covered X / Y ACs (Z%) — missing: …`.
6. Write the report to `specs/reviews/ac-coverage-<YYYYMMDD-HHMM>.md` and print the summary and the MISSING rows.

If any AC is MISSING, say which agent should fix it: `test-engineer` for a red test, using the
`spec-to-test-generator` skill. For the deeper, programmatic audit (spec/code drift), suggest
`python scripts/ac_audit.py`.
