# specs/ — CLAUDE.md

Specs are the source of truth (see root `CLAUDE.md`, rule 2).

## Writing specs
- One file per feature, `<feature>_spec.md`, linked from `app_spec.md` §3 and `AGENTS.md`.
- Every feature spec has an `## Acceptance Criteria` section. Each AC is `**AC-NN.M**` followed by
  Given / When / Then, with **concrete numbers** (inputs and the exact expected outputs).
- Money examples must be computed with the formula in the spec, to 2 dp HALF_UP. Use the `premium-calc-evaluator`
  skill to verify every worked example before committing.
- New ACs get the next free number. Never renumber or reuse an AC ID, because tests reference them.

## Changing specs
- Commit a spec change separately (`spec: …`) before the code that implements it.
- After changing an AC, run `/ac-coverage` to find tests that must change.
- `reviews/` holds evaluator outputs, AC audits and sprint evaluations. These are append-only, one file per run.
