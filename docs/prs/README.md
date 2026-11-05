# Pull-request records

Remote: `origin` = https://github.com/wushandarshana/Policy-Forge. Branching is feature → `dev` → `main`. Each `--no-ff`
merge of a feature branch into `dev` has a PR record here,
written by `/pr`: `PR-NNN.md` with branch, scope, ACs covered, red/green/refactor commit list, reviewer agent verdicts
(security-reviewer, test-engineer, evaluator) and the merge commit SHA. When the GitLab remote exists, the same
content becomes the MR description.

## Index: PR records, GitHub PRs and merge commits

PR-001 to PR-006 were merged locally with `git merge --no-ff` and pushed before the GitHub ruleset existed (KD-013).
From PR-007 on, every change has landed through a GitHub PR, merged with "Create a merge commit". Since Sprint 5 the
ruleset on `dev` and `main` requires a PR, the `ci-gate` check, and merge commits only.

| Record | GitHub | Branch → base | Merge commit | How |
|---|---|---|---|---|
| [PR-001](PR-001.md) | — | `feat/substrate` → `main` | `50a5f62` | Local `--no-ff` (initial substrate) |
| [PR-002](PR-002.md) | — | `feat/catalog-quote` → `dev` | `1eb6dc7` | Local `--no-ff`. Sprint 1 |
| [PR-003](PR-003.md) | — | `chore/branching-strategy` → `dev` | `d9e5379` | Local `--no-ff` |
| [PR-004](PR-004.md) | — | `chore/release-v0.1.0` → `dev` | `e5122d2` | Local `--no-ff` |
| [PR-005](PR-005.md) | — | `fix/hook-quoted-separators` → `dev` | `3c3db55` | Local `--no-ff` |
| [PR-006](PR-006.md) | — | `chore/release-v0.1.0-addendum` → `dev` | `6d23f8f` | Local `--no-ff` |
| Release v0.1.0 | — | `dev` → `main` | `8e265d6` | Local `--no-ff`, tag `v0.1.0` ([record](../releases/RELEASE-v0.1.0.md)) |
| [PR-007](PR-007.md) | [#1](https://github.com/wushandarshana/Policy-Forge/pull/1) | `chore/gh-pr-workflow` → `dev` | `ddeae97` | GitHub PR |
| [PR-008](PR-008.md) | [#2](https://github.com/wushandarshana/Policy-Forge/pull/2) | `feat/application-underwriting` → `dev` | `246e7cb` | GitHub PR. Sprint 2 |
| [PR-009](PR-009.md) | [#3](https://github.com/wushandarshana/Policy-Forge/pull/3) | `feat/issuance-endorsement` → `dev` | `12f95c3` | GitHub PR. Sprint 3 |
| [PR-010](PR-010.md) | [#4](https://github.com/wushandarshana/Policy-Forge/pull/4) | `feat/renewal-cancellation` → `dev` | `d9a5694` | GitHub PR. Sprint 4 |
| [PR-011](PR-011.md) | [#5](https://github.com/wushandarshana/Policy-Forge/pull/5) | `chore/ci-hardening` → `dev` | `f3a5bc9` | GitHub PR. Sprint 5, first PR gated by CI |
| (PR-011 follow-up) | [#6](https://github.com/wushandarshana/Policy-Forge/pull/6) | `chore/ci-hardening` → `dev` | `943359a` | GitHub PR (`-linux` baselines, KD-016..033) |
| [PR-012](PR-012.md) | [#7](https://github.com/wushandarshana/Policy-Forge/pull/7) | `chore/release-v1.0.0` → `dev` | `a461f3a` | GitHub PR (release record) |
| Release v1.0.0 | [#8](https://github.com/wushandarshana/Policy-Forge/pull/8) | `dev` → `main` | `7433e43` | GitHub PR, tag `v1.0.0` ([record](../releases/RELEASE-v1.0.0.md)) |
