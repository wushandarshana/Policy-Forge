# Pull-request records

The repository is local-first, and its remote is added later. Each `--no-ff` merge into `main` has a PR record here,
written by `/pr`: `PR-NNN.md` with branch, scope, ACs covered, red/green/refactor commit list, reviewer agent verdicts
(security-reviewer, test-engineer, evaluator) and the merge commit SHA. When the GitLab remote exists, the same
content becomes the MR description.
