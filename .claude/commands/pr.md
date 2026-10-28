---
description: Run the PolicyForge PR gate on the current feature branch (tests, reviewer agents, AC coverage), write docs/prs/PR-NNN.md, and open a GitHub PR into dev with gh, merged with a merge commit (feature → dev; /release promotes dev → main).
argument-hint: "[title]"
allowed-tools: Read, Write, Grep, Glob, Bash, Agent
---

# /pr

PR-only merge rule: this command is the **only** sanctioned way to change `dev`. `main` changes only through `/release`. Title: `$ARGUMENTS`.

## 1. Preconditions
- The current branch is a `feat/`, `fix/` or `chore/` branch cut from `dev` (`git branch --show-current`;
  `git merge-base --is-ancestor dev HEAD`). It is never `main` or `dev`. The working tree is clean (`git status --porcelain`
  is empty).
- The branch is ahead of `dev` (`git rev-list --count dev..HEAD` > 0). If `dev` moved on, merge `dev` into the
  branch first and re-run the gate.

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
- title, branch, base `dev`, date;
- scope and the ACs covered;
- the red, green and refactor commits for each AC (`git log --oneline dev..HEAD`);
- test counts and coverage from the JaCoCo / Vitest summaries;
- reviewer verdict table with links;
- link to the sprint contract, if any.

Commit it on the branch: `docs(pr): PR-NNN record`.

## 5. Open the GitHub PR (the remote rules require PRs into `dev` and `main`)
The local `--no-ff` merge into `dev` is **not** used any more, because GitHub rejects direct pushes to protected
branches.
```bash
git push -u origin <branch>
gh pr create --base dev --head <branch> --title "PR-NNN: <title>" --body-file docs/prs/PR-NNN.md
```
- Print the PR URL. The user reviews it and merges it on GitHub with **"Create a merge commit"**, which keeps the
  `--no-ff` history. Don't use squash or rebase merges.
- If the user asks you to merge it: `gh pr merge <number> --merge` (never `--squash`, `--rebase` or `--admin`).
- After the merge, sync locally: `git checkout dev && git pull --ff-only`. The merge commit's SHA is recorded in the
  next PR record.
- Don't touch `main`; that's `/release`.
- If `gh` is missing or not authenticated (`gh auth status`), stop. Print the compare link
  `https://github.com/wushandarshana/Policy-Forge/compare/dev...<branch>?expand=1` for the user to open the PR.
