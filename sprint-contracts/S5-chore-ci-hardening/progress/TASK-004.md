# TASK-004 Progress

## Status
DONE

## What was built
The CI definitions of Sprint 5, with their config tests, on `chore/ci-hardening` (nothing pushed, no `gh` write, no label,
secret or ruleset created). Amendments A (`4730168`) and B (`f9a1664`) exist after `d9a5694` and were not recreated;
TASK-001, TASK-002 and TASK-003 were DONE.

- **`.github/workflows/ci.yml`** (name `CI`): five jobs whose `name:` equals their ID, all on `ubuntu-24.04` with the P-11
  timeouts (40, 15, 45, 20, 40 minutes). Triggers per C2 (`push` and `pull_request` for `dev` and `main`,
  `workflow_dispatch`), top-level `permissions: contents: read`, a concurrency group that cancels only superseded pull
  request runs, and `defaults.run.shell: bash`.
  - `build`: `setup-java` Temurin 21.0.13+9 with `cache: maven`, an `actions/cache` for `~/.npm`,
    `cd backend && ./mvnw -B verify`; artifacts `policyforge-jar` (7 days), `jacoco-report` (always), `backend-test-reports`
    (failure).
  - `frontend-unit`: `setup-node` 22.11.0 with `cache: npm`, `cd frontend && npm ci && npm test && npm run build`,
    artifact `frontend-coverage` (always).
  - `e2e` (`needs: build`): `container:` the pinned Playwright image with `--ipc=host`, downloads `policyforge-jar`,
    `setup-java` Temurin JRE, a step that resolves the one JAR into `PF_E2E_JAR`, `cd frontend && npm ci && npx playwright
    test`, then a step that prints the count and names in `visual-baselines-missing.txt` (also into the job summary).
    Artifacts `visual-baselines-missing` (always), `playwright-report` and `playwright-traces` (failure). The job never
    sets `PF_VISUAL_BASELINES` and never passes `--update-snapshots`.
  - `quality-gates`: `setup-python` 3.14.10 with `cache: pip`, `pip install -r scripts/requirements-ci.txt`,
    `python scripts/ci/quality_gates.py --report ci-out/quality-gates/report.md`, artifact `quality-gates-report` (always).
  - `claude-review` (`needs` the other four, job `permissions: contents: read, pull-requests: write`): full-history
    checkout, `setup-node` 22.11.0, `npm install -g @anthropic-ai/claude-code@2.1.311`, the review step (the only place that
    names `ANTHROPIC_API_KEY`, in its `env`, with the event values passed through `env` and a `case` that picks
    `--event pull_request|push|manual`), a separate comment step (`if: always() && github.event_name == 'pull_request'`,
    `env: GH_TOKEN` only), artifact `claude-review` (always). No `continue-on-error` anywhere.
- **`.github/workflows/linux-snapshots.yml`** (name `Linux snapshots`, G-1, C2, C9): `pull_request` of type `labeled` into
  `dev` or `main` plus `workflow_dispatch`, every job guarded by `github.event_name == 'workflow_dispatch' ||
  github.event.label.name == 'update-linux-snapshots'`, `contents: read` only, no secret. Job `build-jar` runs
  `./mvnw -B -DskipTests package` (SPA packaged) and uploads the JAR for one day. Job `linux-snapshots` (`needs: build-jar`)
  uses the same container, JRE and `PF_E2E_JAR` steps as `e2e`, then `PF_VISUAL_BASELINES=update` with
  `npx playwright test --update-snapshots=missing`. It uploads exactly `frontend/e2e/__snapshots__/*-linux.png` as
  `linux-snapshot-baselines` (`if-no-files-found: error`) and the HTML report as `linux-snapshot-report`.
- **`.gitlab-ci.yml`**: `workflow:rules` per C2 (merge request into `dev` or `main`, push to them, `web`), stages `build`,
  `test`, `e2e`, `review`, the same five jobs with the same commands, the four pinned images (`eclipse-temurin`, `node`,
  `python`, Playwright), `.m2/repository` (keyed on `backend/pom.xml`) and `.npm` (keyed on `package-lock.json`) caches plus a
  pip cache, one artifact archive per job, `frontend-unit` and `quality-gates` with `needs: []`, `claude-review` with the four
  `needs` and `allow_failure: false`. `e2e` and the manual `e2e-update-linux-snapshots` (`when: manual`,
  `allow_failure: true`) install the C3 JRE tarball after `sha256sum -c`, before `tar`. No `!reference`.
- **`backend/mvnw`** is mode 100755 in the index (`git update-index --chmod=+x`, content unchanged).
- **`scripts/tests/test_ci_config.py`**: 27 tests in five classes. Docstrings start with `NFR-09.1` (9 tests),
  `NFR-09.2` (8), `NFR-09.3` (6), `NFR-09.6` (2) and `NFR-09.5` (2: the two snapshot generators). Expected pins are the
  verified C3 values, so a typo in a SHA or digest fails here and not in the first CI run. A missing PyYAML or CI file is a
  failure, never a skip. The `ac_audit` offline matrix now reports **92/92** with 0 parent-only.

## Files changed
- .github/workflows/ci.yml
- .github/workflows/linux-snapshots.yml
- .gitlab-ci.yml
- backend/mvnw (mode only: 100644 to 100755)
- scripts/tests/test_ci_config.py
- (workspace, not committed) .sprint/20261104-075010/sprint-progress/TASK-004.md, TASK-004-red.log, TASK-004-green.log

Nothing under `backend/src/main/`, no Flyway migration, no rule file, no substrate (`specs/`, `docs/`, `CLAUDE.md`,
`AGENTS.md`, `.claude/**`, `.mcp.json`) and nothing from another task's files was touched. `git diff d9a5694 -- backend/src/main`
is empty.

## Commits (all on `chore/ci-hardening`, explicit paths, nothing pushed)
1. `54896b1` test(red): the config tests. Run against a tree with no CI files and `backend/mvnw` at 100644: 27 tests, 26
   fail on an assertion (22 "ci.yml does not exist", 1 "linux-snapshots.yml does not exist", 2 ".gitlab-ci.yml does not
   exist", 1 mode 100644), 0 errors. The one pass is the `git grep` check for `sk-ant-` literals, which a tree without CI
   files satisfies (characterisation). Log: `TASK-004-red.log`.
2. `2ecaea2` ci: NFR-09.1 `backend/mvnw` mode 100755.
3. `693b60a` ci: NFR-09.1 NFR-09.2 NFR-09.3 NFR-09.6 `ci.yml`.
4. `c2886b8` ci: NFR-09.5 NFR-09.2 NFR-09.3 `linux-snapshots.yml`.
5. `600d257` ci: NFR-09.1 NFR-09.2 NFR-09.3 NFR-09.6 `.gitlab-ci.yml`. The config tests are green from here (27 OK).
6. `7c7fdf3` ci: NFR-09.3 GitLab `environment: claude-review` (finding F1 below) plus one assertion added to
   `test_ci_config.py` after the red commit, recorded in the message.

## Verification (details in `TASK-004-green.log`)
- `test_ci_config.py`: 27 tests OK. **Mutation proofs** (temporary faults, never committed, files restored, `git status`
  clean afterwards): 13 faults, each caught by the named test. Examples: one changed action SHA (pin test),
  `continue-on-error` on `claude-review`, a `${{ github.event... }}` expression in a `run:`, `persist-credentials` removed,
  an image without digest, the key also in the comment step, `pull_request_target`, `set -x`, update mode replaced by
  `require`, GitLab `allow_failure: true`, GitLab `sha256sum` check removed, `--only` added to the gates command, the GitLab
  environment removed.
- **actionlint 1.7.12 with shellcheck v0.11.0** (both fetched through `fetch_tools.py`, SHA-256 checked) on
  `.github/workflows/ci.yml` and `linux-snapshots.yml`: 0 findings, exit 0. A throw-away workflow with an unquoted variable
  is rejected (SC2086, SC2115), so the check is live.
- **check-jsonschema 0.39.0**: `--builtin-schema vendor.github-workflows` on both workflows, and `--builtin-schema
  vendor.gitlab-ci --data-transform gitlab-ci` on `.gitlab-ci.yml`: "ok -- validation done", exit 0 each.
- **`python scripts/ci/quality_gates.py` (run with the `.venv` interpreter, which has PyYAML and check-jsonschema)**: 8/8
  gates passed, exit 0.

  | gate | exit | result |
  |---|---|---|
  | ac-audit (92/92 covered, 0 parent-only) | 0 | PASS |
  | redact-evidence | 0 | PASS |
  | hook-tests (ALL PASSED) | 0 | PASS |
  | hook-line-endings (6 files, all LF) | 0 | PASS |
  | ci-script-tests (138 tests) | 0 | PASS |
  | oracle-fixture (1000 rows equal a fresh generation) | 0 | PASS |
  | actionlint | 0 | PASS |
  | ci-schema | 0 | PASS |
- **Pins re-verified read-only on 2026-11-04** (so the first CI run does not fail on a wrong pin): the seven action SHAs
  against `git ls-remote` tags; the digests of the Playwright, `eclipse-temurin`, `node` and `python` images against their
  registries; the Temurin JRE tarball SHA-256 against the release's `.sha256.txt`; every `with:` input name against the
  `action.yml` of each pinned action; Python 3.14.10 (linux 24.04 x64) and Node 22.11.0 in the setup action manifests. All
  matched. The setup-java Maven cache also covers `~/.m2/wrapper/dists`, as P-12 says.
- `dev` is `d9a5694`, `main` is `8e265d6`, `origin/chore/ci-hardening` does not exist, port 8080 is free.
  `git grep` finds no `sk-ant-` literal of 20 or more characters. `redact_evidence.py --check` over the two logs: total 0.
- The only `gh` calls were read-only `gh api` GETs of `action.yml` and manifest files (P-16).

## Security review of the three CI files (security-reviewer criteria, read-only)
The `security-reviewer` agent could not be started: this generator has no subagent tool (same as TASK-002) and P-15 forbids
a live API call. I applied the agent's checklist (`.claude/agents/security-reviewer.md`, items 1 to 8 are about the Java
code and are N/A here) and the P-6 list to `ci.yml`, `linux-snapshots.yml` and `.gitlab-ci.yml`, line by line.

| P-6 item | Result | Evidence |
|---|---|---|
| Top-level read-only token, write only where needed | PASS | `permissions: contents: read` in both workflows; `pull-requests: write` only on `claude-review` |
| No `pull_request_target` or `workflow_run` | PASS | test and `grep` |
| `persist-credentials: false` on every checkout | PASS | 7 checkouts (5 in `ci.yml`, 2 in `linux-snapshots.yml`), all set; the review checkout also has `fetch-depth: 0` |
| No `${{ github.event... }}` in a `run:` | PASS | every `run:` has zero `${{ }}` expressions; event values go through `env` |
| Key only in the review step's `env`, never printed | PASS | one reference in the whole tree; the Claude step has no GitHub token; the comment step has `GH_TOKEN` and no key |
| No `set -x`, no environment dump, no TLS bypass | PASS | test patterns, including `CI_DEBUG_TRACE` and `NODE_TLS_REJECT_UNAUTHORIZED` |
| Review job does not restore caches | PASS | `claude-review` has no cache step (a poisoned cache cannot reach the key) |
| Timeouts bound the key's exposure | PASS | 40 minutes on the review, budget cap in the runner |
| `linux-snapshots.yml` has no secret and a read-only token | PASS | no `secrets.` and no job permissions |

Findings:
- **F1 (MINOR, fixed in `7c7fdf3`).** GitLab gives every job of a pipeline the project's CI variables, so `build` and `e2e`,
  which run the merge request's code, would also receive `ANTHROPIC_API_KEY`. That defeats "reaches only the review job".
  Fix: the job has `environment: claude-review`, and the setup text must scope the variable to that environment (see
  Interface notes). Without the scope nothing breaks, the key then reaches every job as before.
- **F2 (INFO, accepted, SG-10).** Anyone who can push a branch to this repository can change `ci.yml` or
  `scripts/ci/claude_review.py` in a same-repo pull request and read the key in the `claude-review` job, because
  `pull_request` runs the PR's own workflow. Fork PRs get no secrets. The repository has no outside contributors. A control
  that does not need a spec change: limit who has write access, and review changes to `.github/`, `scripts/ci/` and
  `.claude/agents/` before they run with the key.
- **F3 (MINOR, accepted).** The CLI is pinned to an exact version, but its npm dependencies are not locked, and the CLI
  runs in the step that has the key. This is the same class as SG-13 (transitive pip packages). A lock file or a
  hash-checked tarball for the CLI would close it.
- **F4 (INFO).** `workflow_dispatch` lets any writer start `ci.yml` on any branch, including the `claude-review` job with the key.
  The same trust assumption as F2.
- **F5 (INFO).** The repository is public, so uploaded artifacts (reports, traces, JAR) are readable by anyone. They hold
  synthetic data only, and the review outputs are redacted by the runner.
- **F6 (INFO).** `--ipc=host` on the Playwright container shares the runner's IPC namespace with the container, as
  Playwright's documentation recommends. The runner is an ephemeral VM.

No BLOCKER and no MAJOR finding.

## Decisions and deviations
- **Two jobs in `linux-snapshots.yml`** (`build-jar` on the host, `linux-snapshots` in the container) instead of one job with
  a JDK in the container. Both reuse the shapes of `build` and `e2e`, so a working `ci.yml` implies a working snapshot
  workflow.
- **GitLab keeps one retention for a job's artifacts.** GitLab has one archive and one `expire_in` per job, so the JAR is kept
  14 days (GitHub: 7) and, on `e2e`, the report and traces are kept on success as well (they are small: traces are only
  written on failure). GitLab `build` and `e2e` use `when: always`.
- **`e2e` uses the Node of the Playwright image**, not `setup-node` 22.11.0, as C1 says. The image's Node runs only the test
  runner. The NFR-09.2 test covers `setup-node` and the Node images, which equal the pom's `v22.11.0`.
- **GitLab extras:** a pip cache for `quality-gates`, `git remote set-url origin "${CI_PROJECT_URL}.git"` as the first line of
  `claude-review` (the clone URL may hold the job token; the review reads files), and the `environment` of F1. The first
  two cannot be tested without a runner.
- **Commit trailer** is `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`, the host's attribution rule, as in
  TASK-002. Say if the log needs "Opus 5.5" instead.
- **The test file was edited once after its red commit** (the F1 assertion), recorded in `7c7fdf3`.

## Interface notes for TASK-005 and the orchestrator
- **Amendment C text to adjust** (`docs/harness-setup.md`, "Add the API key", GitLab bullet): add "set the Environment scope of
  `ANTHROPIC_API_KEY` (and of `PF_GITLAB_REVIEW_TOKEN`) to `claude-review`, which the `claude-review` job uses through
  `environment: claude-review`". Keep "masked" and "protected".
- **Check names** for G-8 (job names equal IDs): `build`, `frontend-unit`, `e2e`, `quality-gates`, `claude-review`. The
  snapshot workflow's jobs are `build-jar` and `linux-snapshots`; they are not required checks.
- **Artifact names** (GitHub): `policyforge-jar`, `jacoco-report`, `backend-test-reports`, `frontend-coverage`,
  `playwright-report`, `playwright-traces`, `visual-baselines-missing`, `quality-gates-report`, `claude-review`,
  `linux-snapshot-baselines`, `linux-snapshot-report`.
- **Expected first run on the PR:** `e2e` prints 20 skipped visual assertions (`visual-baseline-missing`) because Linux has
  no baseline yet; the list is in the job summary and the artifact `visual-baselines-missing`.
- **Local interpreter:** `quality_gates.py` and `test_ci_config.py` need PyYAML and check-jsonschema, so run them with
  `.venv/Scripts/python.exe` here (the system Python has neither). CI installs them first.
- The test module counts `NFR-09.1`, `NFR-09.2`, `NFR-09.3`, `NFR-09.5` and `NFR-09.6` for `ac_audit.py`.

## Open issues
- **Nothing here ran on Linux or on GitHub.** There is no Docker or WSL. The container jobs, `setup-java` inside the
  Playwright container, the `actions/cache` restore there, the Linux behaviour of the hook suite and of the script tests, and
  the real Claude CLI flags (`--max-turns` is hidden in 2.1.318 and untested on 2.1.311, see TASK-002) are first exercised by
  the CI run after the push (POST-PUSH step 2). The static layers are covered: actionlint with shellcheck, both JSON
  schemas, the config tests and the pin re-verification.
- **Downloads outside the CI files have no checksum in this repository** (a gap against the wording of NFR-09.2, "every
  downloaded tool ... with a SHA-256 checksum"). The Maven distribution is fetched by the wrapper
  (`backend/.mvn/wrapper/maven-wrapper.properties` has no `distributionSha256Sum`), and the frontend-maven-plugin and
  `setup-node` fetch Node. The config test covers what the CI files download (the JRE tarball, actionlint, shellcheck, which
  `fetch_tools.py` checks). Closing the Maven gap is one property in a backend build file, outside this task's scope. The
  orchestrator may want to number it as a spec gap (the plan's SG-8 to SG-13 are taken).
- **The GitLab pipeline has never run** (SG-11). `git remote set-url` and `environment:` behave as documented but are
  untested. The first GitLab run is the user's.
- **A hidden risk of `needs` and G-8:** if a required job fails, GitHub shows `claude-review` as skipped, and a skipped
  required check counts as passing. The failed job itself is also required, so the merge is still blocked.
- **`claude-review` on a pull request from the same repository** runs the PR's own copy of `claude_review.py` and of the agent
  files (F2). The runner's version check and the required status checks are the only controls.
- **1 px visual flake** from TASK-003 (`my-policies-card`, desktop) can still appear once the Linux baselines exist; CI has
  `retries: 1`.
