---
description: Run the PolicyForge PR gate on the current feature branch (tests, reviewer agents, AC coverage), write docs/prs/PR-NNN.md, and merge into main with --no-ff.
argument-hint: "[title]"
allowed-tools: Read, Write, Grep, Glob, Bash, Agent
---

# /pr

PR-only merge rule: this command is the **only** sanctioned way to change `main`. Title: `$ARGUMENTS`.

## 1. Preconditions
- The current branch is not `main` (`git branch --show-current`). The working tree is clean (`git status --porcelain`
  is empty).
- The branch is ahead of `main` (`git rev-list --count main..HEAD` > 0).

## 2. Verify (stop on failure)
- If `backend/` exists: `cd backend && ./mvnw -q verify` (tests, ArchUnit, JaCoCo check).
- If `frontend/package.json` exists: `cd frontend && npm test -- --run && npm run build`.
- `/ac-coverage`: no MISSING ACs for the specs this branch touches.

## 3. Review gate (agents run in parallel; each writes `specs/reviews/*-<branch>-<date>.md`)
- `security-reviewer`: always.
- `test-engineer` (review mode): always.
- `policy-version-validator-agent`: if `backend/src/main/resources/rules/` changed.
- `migration-coherence-agent`: if `db/migration/` or `repository/` changed.
- `design-critic`: if `frontend/src/pages/` changed and the app is running.

Any `VERDICT: CHANGES_REQUESTED` stops the merge. Report the findings and leave the branch open.

## 4. PR record
NNN = (highest existing `docs/prs/PR-*.md`) + 1, zero-padded to 3 digits. Have `doc-writer` write
`docs/prs/PR-NNN.md`:
- title, branch, base `main`, date;
- scope and the ACs covered;
- the red, green and refactor commits for each AC (`git log --oneline main..HEAD`);
- test counts and coverage from the JaCoCo / Vitest summaries;
- reviewer verdict table with links;
- link to the sprint contract, if any.

Commit it on the branch: `docs(pr): PR-NNN record`.

## 5. Merge
```bash
git checkout main
git merge --no-ff <branch> -m "Merge PR-NNN: <title> (<branch>)" -m "Reviewed-by: security-reviewer, test-engineer[, …]"
```
Then append the merge SHA to the PR record. This needs a follow-up branch commit, so note it in the next PR instead.
Never amend on main. Print `git log --first-parent --oneline -5 main`.

When a GitLab remote exists, push the branch and open an MR with the PR record as its description, instead of merging
locally.
