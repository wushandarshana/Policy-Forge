---
description: Promote dev to main (production) after QA — full test suite on dev, review sign-off, --no-ff merge, tag, release record, push.
argument-hint: "<version e.g. v0.1.0> [title]"
allowed-tools: Read, Write, Grep, Glob, Bash, Agent
---

# /release

Release `$ARGUMENTS`. Branching strategy: feature → `dev` → **`main`**. `main` is production-ready, and this command is
the **only** sanctioned way to change it (root `CLAUDE.md` rule 3).

## 1. Preconditions (stop on any failure)
- On `dev`, working tree clean, `dev` in sync with `origin/dev` (`git fetch origin && git status -sb`).
- `dev` is ahead of `main` (`git rev-list --count main..dev` > 0).
- Every feature merged since the last release has a `docs/prs/PR-NNN.md` with an APPROVE from both the
  `security-reviewer` and the `test-engineer`.

## 2. QA on `dev` (full, from clean)
- `cd backend && ./mvnw clean verify`: all unit, integration and ArchUnit tests pass, JaCoCo gates met.
- `cd frontend && npm test -- --run && npm run build && npx playwright test`: all pass on desktop and mobile.
- `python -I .claude/skills/premium-calc-evaluator/premium_check.py spec-examples`: all PASS.
- `python scripts/ac_audit.py --offline --no-write`: report coverage. Any AC that a released sprint claims and that is
  not COVERED blocks the release.
- `python scripts/redact_evidence.py --check docs sprint-contracts specs/reviews` must exit 0.

## 3. Release record
Write `docs/releases/RELEASE-<version>.md` on a `chore/release-<version>` branch cut from `dev`, and merge it through
`/pr`:
- the version, the date, and the merged PRs since the last release;
- the ACs delivered;
- the QA command results, with counts and coverage;
- known residuals and open items.

## 4. Promote (through a GitHub PR `dev` → `main`)
```bash
git push origin dev   # only if dev is ahead of origin/dev, which shouldn't happen when features land via GitHub PRs
gh pr create --base main --head dev --title "Release <version>: <title>" --body-file docs/releases/RELEASE-<version>.md
```
- The user merges it on GitHub with **"Create a merge commit"**. If the user asks, run `gh pr merge <number> --merge`
  (never `--squash`, `--rebase` or `--admin`).
- Then tag the **merge commit on `main`**, never a local-only commit:
  ```bash
  git checkout main && git pull --ff-only
  git tag -a <version> -m "<title>" && git push origin <version>
  git checkout dev && git pull --ff-only
  ```
- Check that `git merge-base --is-ancestor <version> origin/main` succeeds. Never force-push `main` and never move a
  pushed tag. If anything fails after the PR is merged, stop and report.
