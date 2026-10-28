---
description: Archive the latest agent-harness sprint (.sprint/<timestamp>/) into the committed evidence trail — sprint-contracts/ and specs/reviews/.
argument-hint: "<sprint-number e.g. S1> [timestamp-dir]"
allowed-tools: Read, Write, Glob, Bash(git:*), Bash(ls:*), Bash(cp:*), Bash(mkdir:*)
---

# /sprint-archive

Arguments: `$ARGUMENTS`

The harness writes its working files to `.sprint/<timestamp>/` (gitignored). The capstone requires the sprint
contracts and evaluator outputs to be **committed** under `sprint-contracts/` and `specs/reviews/`.

1. Find the sprint dir: the given timestamp, or the newest directory in `.sprint/`. If none exists, stop and report
   it. Never fabricate a sprint record.
2. Let `S` be the sprint label (e.g. `S1`) and `B` be the current branch.
3. Copy, without editing the content:
   - `sprint-plan.md` → `sprint-contracts/<S>-<B with / → ->/sprint-contract.md`
   - `sprint-eval.md` → `specs/reviews/<S>-sprint-eval.md`
   - any other `*.md` progress files → `sprint-contracts/<S>-…/`
4. Prepend a header to each archived contract (a new file, so the original text stays byte-identical below the
   header):
   ```
   > Archived from .sprint/<ts>/ on <date> · branch <B> · harness agent-harness <version from /plugin list if known>
   ```
5. Generate `sprint-contracts/<S>-…/README.md` with:
   - the contracted ACs;
   - the evaluator verdict per AC (from sprint-eval);
   - the number of ratchet iterations;
   - links to the reviewer reports in `specs/reviews/` for this branch (security-, tests-, design-, rules-,
     migrations-).
6. `git add sprint-contracts specs/reviews` and commit `docs(sprint): archive <S> contract and evaluation`.
