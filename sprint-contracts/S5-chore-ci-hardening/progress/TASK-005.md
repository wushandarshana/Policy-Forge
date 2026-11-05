# TASK-005 Progress

## Status
DONE

## What was built
Sprint-wide verification of `chore/ci-hardening` at `7c7fdf3`, plus the POST-PUSH runbook and the knowledge-deposit drafts
below. TASK-005 changed no tracked file and made no commit (every output is in the ignored workspace), so the branch
still ends at `7c7fdf3`. Every verification criterion passed:

- Full `./mvnw -B verify` (SPA packaged, frontend not skipped): BUILD SUCCESS in 7:29. 490 surefire and 570 failsafe
  tests, 0 failures. ArchUnit green, including `RepriceOfferCallerArchTest` (3 tests) and `RenewalRepriceRaceIT` (2).
  JaCoCo thresholds unchanged.
- `npm test` (17 files, 173 tests, coverage 96.5% lines) and `npm run build`: pass. `git status` clean afterwards and
  `git ls-files frontend/coverage` is empty (`.gitignore:16:frontend/coverage/` ignores `lcov.info`).
- Playwright against a freshly packaged JAR (`clean package`, SPA rebuilt) with `PF_E2E_JAR`: `desktop-chromium` 22
  passed, `mobile-chromium` 22 passed. No PNG created or changed (20 tracked baselines, all `-win32`, `git status`
  clean) and no `visual-baselines-missing.txt`, so no `visual-baseline-missing` entry on win32.
- `quality_gates.py`: 8/8 gates PASS. `python -m unittest discover -s scripts/tests -v`: 138 tests OK.
- `ac_audit.py --offline --no-write`: 92/92 covered, 0 parent-only, 0 missing, 0 orphans. NFR-08.1, NFR-09.1 to
  NFR-09.6, AC-07.7 and AC-08.5 are each COVERED.
- History, migrations and rules, pins, remote state, redaction: all confirmed (sections below). Nothing found that
  fails a criterion.

## Files changed
- none tracked (no commit by TASK-005)
- (workspace, not committed) `.sprint/20261104-075010/sprint-progress/TASK-005.md`, `TASK-005-green.log`,
  `mvn-verify-raw.txt` (13,977 lines of the Maven run, mostly JSON application log lines; the digest is in the green log)
- (ignored by `.gitignore`, created by the runs) `ci-out/quality-gates/report.md`, `frontend/test-results/`,
  `frontend/playwright-report/`, `frontend/coverage/`, `backend/target/` (now holds the clean-packaged JAR)

## Verification record

### Commands run and their results
Interpreters: system Python 3.14.7, `.venv/Scripts/python.exe` (PyYAML 6.0.3, check-jsonschema 0.39.0), Node v24.20.0,
npm 11.19.0, Temurin 21.0.12.1 (`JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot`). Everything ran in
the foreground (KD-014). Full output is in `TASK-005-green.log`.

| # | Command | Result |
|---|---|---|
| 0 | `git branch --show-current`, `git status --short`, `git rev-parse dev main HEAD`, `git ls-remote origin ...`, `netstat`, `tasklist` | branch `chore/ci-hardening`, tree clean, HEAD `7c7fdf3`, `dev` `d9a5694`, `main` `8e265d6`, port 8080 free, no Java process |
| 1 | `cd backend && ./mvnw -B verify` | exit 0, BUILD SUCCESS, 07:29 min. Frontend ran (`install-node-and-npm`, `npm ci`, `npm run build`, `copy-spa`). 490 + 570 tests, 0 failures. `jacoco:check` overall and domain: "All coverage checks have been met" |
| 1b | `git diff --stat d9a5694 -- backend/pom.xml` | empty. `jacoco.domain.line.minimum` 0.80 and `jacoco.overall.line.minimum` 0.70 |
| 2 | `cd frontend && npm test` | exit 0. 17 files, 173 tests pass. Statements 96.5%, branches 89.23%, functions 96.85%, lines 96.5% |
| 2b | `npm run build` | exit 0 (`tsc --noEmit`, `vite build`) |
| 2c | `git status --short`; `git ls-files frontend/coverage`; `git check-ignore -v frontend/coverage/lcov.info` | 0 status lines; empty; ignored by `.gitignore:16:frontend/coverage/` |
| 3 | `cd backend && ./mvnw -B -DskipTests clean package` | BUILD SUCCESS in 1:19. The JAR holds exactly one `index-JDWy5faI.js` and one `index-B-sjZ0Y5.css` (no stale bundles) |
| 4a | `PF_E2E_JAR=<abs jar> npx playwright test --project=desktop-chromium --reporter=list` | exit 0, 22 passed (21.5 s) |
| 4b | the same for `--project=mobile-chromium` | exit 0, 22 passed (20.8 s) |
| 4c | `git status --short`; `ls frontend/test-results/visual-baselines-missing.txt`; `ls frontend/e2e/__snapshots__` | 0 status lines; file absent; 20 files, all `-win32`, all tracked. `PF_VISUAL_BASELINES` was unset (auto mode) |
| 5 | `.venv/Scripts/python.exe scripts/ci/quality_gates.py --report ci-out/quality-gates/report.md` | exit 0, 8/8 gates PASS (table below) |
| 6 | `.venv/Scripts/python.exe -m unittest discover -s scripts/tests -v` | `Ran 138 tests ... OK` (52 s) |
| 6b | `python -m unittest discover -s scripts/tests` with the system interpreter (informational) | 22 failures, all "PyYAML is not installed", by design (see Open issues) |
| 7 | `python scripts/ac_audit.py --offline --no-write` | exit 0, `Covered 92 / 92 ACs (100%) · parent-only 0 · not covered 0` |
| 7b | read-only snippet that calls `ac_audit.collect_criteria` and `collect_references` (the script prints only the summary with `--no-write`) | per-ID table below |
| 8 | `git log`, `git show --stat`, `git merge-base --is-ancestor`, `git diff --stat d9a5694 -- ...` | history and diff checks below |
| 9 | a read-only Python script that compares the C3 table with the three CI files, `.mcp.json`, `package*.json`, `pom.xml`, `requirements-ci.txt` and `fetch_tools.py` | 0 mismatches (table below) |
| 10 | `git ls-remote origin ...`, `git branch -r`, `gh auth status`, `gh label list`, `gh secret list`, `gh variable list`, `gh workflow list`, `gh pr list --head chore/ci-hardening`, `gh api repos/.../rulesets/26032911`, `gh api repos/.../actions/permissions/workflow` (all GET) | unchanged, see below |
| 11 | `python scripts/redact_evidence.py --check .sprint/20261104-075010/sprint-progress ci-out`; `git grep -nE "sk-ant-[A-Za-z0-9_-]{20,}"` | `total: 0` for both directories, exit 0; `git grep` exit 1 (no match) |

Gate table of step 5:

| gate | exit | result |
|---|---|---|
| ac-audit | 0 | PASS |
| redact-evidence | 0 | PASS |
| hook-tests | 0 | PASS |
| hook-line-endings | 0 | PASS |
| ci-script-tests (138 tests) | 0 | PASS |
| oracle-fixture (1000 rows equal a fresh generation) | 0 | PASS |
| actionlint (1.7.12, shellcheck v0.11.0) | 0 | PASS |
| ci-schema (GitHub workflows and GitLab CI) | 0 | PASS |

### Per-ID audit status (step 7b)
| ID | Status | Exact references |
|---|---|---|
| NFR-08.1 | COVERED | 11 (`RepriceOfferCallerArchTest`) |
| NFR-09.1 | COVERED | 11 (`test_ci_config.py`) |
| NFR-09.2 | COVERED | 10 (`test_ci_config.py`) |
| NFR-09.3 | COVERED | 24 |
| NFR-09.4 | COVERED | 56 |
| NFR-09.5 | COVERED | 12 (`visualBaselinePolicy.test.ts`, `test_ci_config.py`) |
| NFR-09.6 | COVERED | 51 |
| AC-07.7 | COVERED | 18 (`RenewalRepriceRaceIT` among them) |
| AC-08.5 | COVERED | 6 (`ProRataRefundOracleCrossCheckTest` among them) |
| total | 92 / 92 | parent-only 0, missing 0, orphan references 0 |

### History (`git log d9a5694..chore/ci-hardening`, 24 commits)
- **Amendments first.** Commit 1 is `4730168` (amendment A: `specs/app_spec.md`, `specs/renewal-cancellation_spec.md`,
  `specs/portfolio-dashboard_spec.md` only). Commit 2 is `f9a1664` (amendment B: `.mcp.json`, `docs/architecture.md`,
  `frontend/CLAUDE.md`, `frontend/e2e/CLAUDE.md`, `scripts/CLAUDE.md` only). `git merge-base --is-ancestor f9a1664 2b307af`
  holds, `2b307af` being the first generator commit after TASK-001's two. Nothing under `backend/src`, `.github/` or
  `.gitlab-ci.yml` precedes them (the only path-filter hit before `f9a1664` is the three folder `CLAUDE.md` files inside B itself).
- **Red before green, per ID** (first `test(red)` commit, then the first `feat(green)`, `fix(green)` or `ci:` commit):
  - NFR-08.1: red `1e26f7f`, green `ed11eb6`.
  - NFR-09.1: red `54896b1`, then `ci:` `2ecaea2`, `693b60a`, `600d257`.
  - NFR-09.2: red `54896b1`, then `ci:` `693b60a`, `c2886b8`, `600d257`. The `chore:` `8647f54` (exact Playwright pin) names it too, and it is not one of the commit types the criterion lists.
  - NFR-09.3: red `9e9b20c` (scripts) and `54896b1` (CI files), then `feat(green)` `11fd0f9`, `10fae5e` and `ci:` `693b60a`, `c2886b8`, `600d257`, `7c7fdf3`.
  - NFR-09.4: red `9e9b20c`, green `10fae5e`.
  - NFR-09.5: red `6d26416` (policy) and `54896b1` (snapshot generators), then `feat(green)` `59420aa` and `ci:` `c2886b8`.
  - NFR-09.6: red `9e9b20c` and `54896b1`, then `feat(green)` `588feb0`, `11fd0f9`, `12dc531`, `3da04da`, `61f20f3` and `ci:` `693b60a`, `600d257`.
  - **Characterisation IDs (P-3):** AC-07.7 `49a588e test:` and AC-08.5 `79a53e6 test:` (its fixture generator is `588feb0`). `TASK-001-red.log` has the mutation proofs: AC-07.7 mutation proofs 1 and 2 (lines 86 and 114), AC-08.5 mutation proofs 1, 2 and 3 (lines 139, 158 and 178).
- **No Flyway migration or rule file changed.** `git diff --stat d9a5694 -- backend/src/main/resources/db/migration backend/src/main/resources/rules` is empty (0 lines).
- **`backend/src/main` changes: none.** `git diff --stat d9a5694 -- backend/src/main` is empty, so nothing needs a justification.
- Whole sprint: 53 files changed (6,574 insertions, 4,215 deletions; the deletions are mostly the untracked `frontend/coverage/lcov.info`). By area: `backend` 10 (9 under `backend/src`, tests, fixtures and the CSV only, plus `mvnw` mode only), `scripts` 21 (10 under `scripts/tests`, 5 under `scripts/ci`, plus README, `CLAUDE.md`, the oracle generator, the audit glob, the redaction pattern and the requirements file), `frontend` 13, `.github/workflows` 2, `specs` 3, plus `.gitlab-ci.yml`, `.mcp.json`, `.gitignore` and `docs/architecture.md`.

### Pins versus C3 (step 9)
Every row was compared by a script against the file text; result EQUAL for all 29 table rows below, 0 mismatches. No floating
reference (`latest`, `@main`, `ubuntu-latest`) and no `NODE_TLS_REJECT_UNAUTHORIZED` in any CI file.

| Item | Pin | Found in | Result |
|---|---|---|---|
| actions/checkout | `3d3c42e5aac5ba805825da76410c181273ba90b1` # v7.0.1 | ci.yml, linux-snapshots.yml (7 uses) | EQUAL |
| actions/setup-java | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` # v6.1.0 | both (4 uses) | EQUAL |
| actions/setup-node | `949feb2413d6458794dcd2491c4babbbce0c15c1` # v7.1.0 | ci.yml (2 uses) | EQUAL |
| actions/setup-python | `6d4fd22b20465ccc0f7354476588e8df7c9ca0e0` # v7.1.0 | ci.yml (1 use) | EQUAL |
| actions/cache | `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` # v6.1.0 | both (4 uses) | EQUAL |
| actions/upload-artifact | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` # v7.0.2 | both (12 uses) | EQUAL |
| actions/download-artifact | `9000827ccba6bdab643e8b6fd33ac0654aef8333` # v8.0.2 | both (2 uses) | EQUAL |
| any other action | none allowed | none | EQUAL |
| Claude Code CLI | `@anthropic-ai/claude-code@2.1.311` | ci.yml, .gitlab-ci.yml | EQUAL |
| Playwright MCP | `@playwright/mcp@0.0.84` | .mcp.json | EQUAL |
| Playwright image | `mcr.microsoft.com/playwright:v1.64.1-noble@sha256:2f05f8a734509a7b3f26a912639488ee08f58a7b292d14d62271bcff9038768f` | ci.yml, linux-snapshots.yml, .gitlab-ci.yml | EQUAL |
| Temurin JDK image | `eclipse-temurin:21.0.13_9-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d` | .gitlab-ci.yml | EQUAL |
| Temurin on GitHub | `distribution: temurin`, `java-version: 21.0.13+9` | ci.yml, linux-snapshots.yml | EQUAL |
| Temurin JRE tarball URL | `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_linux_hotspot_21.0.13_9.tar.gz` | .gitlab-ci.yml | EQUAL |
| Temurin JRE tarball sha256 | `a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd` | .gitlab-ci.yml | EQUAL |
| Node | `node-version: 22.11.0` = pom `frontend.node.version` v22.11.0 | ci.yml, pom.xml | EQUAL |
| GitLab Node image | `node:22.11.0-bookworm@sha256:ec878c763e9fad09d22aae86e2edcb7a05b397dfe8411c16e2b90158d595e2ce` | .gitlab-ci.yml | EQUAL |
| Python | `python-version: 3.14.10` | ci.yml | EQUAL |
| GitLab Python image | `python:3.14.10-trixie@sha256:72628634e335b4b0a40727f398a73ae7ba983c319ca901fc034f92a216f7a781` | .gitlab-ci.yml | EQUAL |
| actionlint 1.7.12 | linux `8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8`, windows `6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9` | scripts/ci/fetch_tools.py | EQUAL |
| shellcheck v0.11.0 | linux `8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198`, windows `8a4e35ab0b331c85d73567b12f2a444df187f483e5079ceffa6bda1faa2e740e` | scripts/ci/fetch_tools.py | EQUAL |
| check-jsonschema / PyYAML | 0.39.0 / 6.0.3 | scripts/requirements-ci.txt | EQUAL |
| `@playwright/test` | lock 1.64.1 = `package.json` 1.64.1 = image tag v1.64.1 | frontend/package*.json | EQUAL |
| GitHub runner | `ubuntu-24.04` | ci.yml, linux-snapshots.yml | EQUAL |

(`.mcp.json` is `{"mcpServers":{"playwright":{"command":"npx","args":["-y","@playwright/mcp@0.0.84"]}}}`.)

### Remote and GitHub state (read-only, step 10)
- `dev` = `d9a5694017b5fb0b9f50a07623c1f477f12caa35` and `main` = `8e265d6e5f05ebb5b768562fe82db25273fc37e7`, both locally and in `git ls-remote origin`. `v0.1.0` is `1a019de...` (annotated, peels to `8e265d6`).
- `origin/chore/ci-hardening` does not exist (`git ls-remote` 0 matches, `git branch -r` 0 matches). Nothing pushed. `gh pr list --head chore/ci-hardening` is empty.
- Labels: only the ten GitHub defaults. **`update-linux-snapshots` does not exist.** Repository secrets: none. Repository variables: none. Workflows: 0. So `ANTHROPIC_API_KEY` is not yet set (POST-PUSH step 1 is still open).
- Ruleset `Safety Guard Rails` (26032911): `active`, `include` main and dev, rules `deletion`, `non_fast_forward`, `pull_request` (0 approvals), no bypass actors, `updated_at` 2026-10-28T16:36:23+05:30. It is unchanged, with no `required_status_checks`. Default workflow permissions are `read`, and Actions cannot approve pull request reviews.
- Observation: the ruleset's `allowed_merge_methods` is `merge`, `squash`, `rebase`, and the repository allows all three, while root `CLAUDE.md` says "Create a merge commit" only. The rule is held by process, not by the ruleset.

### Port and processes
Port 8080 has no LISTENING socket and no Java process is running (checked before, between and after the runs, and again at the end of this task).

## Checkpoint outcome G-1 to G-8 (2026-11-04, user approved the plan with every default)
| ID | Outcome as built |
|---|---|
| G-1 Linux baselines | Applied: `linux-snapshots.yml` (label `update-linux-snapshots` or manual dispatch), GitLab manual job `e2e-update-linux-snapshots`, `expectVisual` helper and `visual-baseline-policy.ts` (NFR-09.5). Baselines are generated in CI after the push |
| G-2 Claude review | Applied: pinned CLI 2.1.311 in `--bare -p` mode through `scripts/ci/claude_review.py` on both platforms. The action is not used |
| G-3 Passes and cost | Applied: two passes (`security-reviewer` on opus, `test-engineer` on sonnet), `--max-turns 40`, `--max-budget-usd 5.00` (override `PF_REVIEW_MAX_BUDGET_USD`). Not exercised live (P-15) |
| G-4 Gate semantics | Applied: only a BLOCKER fails, plus fail-closed on a missing key, wrong CLI version, cap hit or invalid output. No bypass |
| G-5 Spec IDs | Applied by amendment A: NFR-08.1 and NFR-09.1 to NFR-09.6 (92 IDs in total) |
| G-6 AC-08.5 oracle | Applied: `gen_refund_oracle_cases.py`, 1,000-row CSV, `ProRataRefundOracleCrossCheckTest`, `--check` as quality gate 6 |
| G-7 E2E server | Applied: `PF_E2E_JAR` starts the packaged JAR (`playwright.config.ts`) |
| G-8 Required checks | Not applied yet by design: the ruleset change is POST-PUSH step 10 after a fully green run |

## Spec gaps recorded, not implemented (SG-1 to SG-7 are Sprint 4's)
- **SG-8.** A PR from a fork gets no secrets, so its review fails closed and the PR cannot pass the required check. The repository has no outside contributors.
- **SG-9.** Model verdicts are not deterministic, so a re-run can differ. A false BLOCKER is fixed or argued in the PR and the job re-run. There is no bypass.
- **SG-10.** A same-repo PR author can put text in the diff, or edit `ci.yml`, `claude_review.py` or the agent files, and steer the review or read the key in the `claude-review` job (the `pull_request` run uses the PR's own files). The prompt marks the diff untrusted, the tools are read-only, and the authors are trusted. Control: limit write access and review changes to `.github/`, `scripts/ci/` and `.claude/agents/`.
- **SG-11.** There is no GitLab remote, so `.gitlab-ci.yml` is checked by its schema and the parity tests only. The first real GitLab run is the user's.
- **SG-12.** Node 22.11.0 (from the pom) dates from October 2024 and Node 22 support ends in April 2027. Upgrading needs the pom property, a rebuild and a new image pin. A separate decision.
- **SG-13.** Only the direct pip dependencies are pinned. The transitive ones are not hash-locked. The npm dependencies of the Claude CLI are not locked either (TASK-004 finding F3).
- **Candidate SG-14 (not numbered by the plan, for the orchestrator).** `backend/.mvn/wrapper/maven-wrapper.properties` has no `distributionSha256Sum`, and `frontend-maven-plugin` and `setup-node` fetch Node without a repository-side checksum, which is narrower than the wording of NFR-09.2 ("every downloaded tool ... with a SHA-256 checksum"). The config test covers what the CI files download. Closing it is a backend build change, outside this sprint.

## POST-PUSH runbook (orchestrator; steps 1 to 13)
Known values: repository `wushandarshana/Policy-Forge` (public, default branch `main`), branch `chore/ci-hardening`, PR base
`dev` (`d9a5694`), `main` `8e265d6`, ruleset id `26032911`, label `update-linux-snapshots`, check names `build`,
`frontend-unit`, `e2e`, `quality-gates`, `claude-review`. HEAD is `7c7fdf3` at the time of writing, and amendment C, the
review-gate fixes and `/sprint-archive` will add commits before the push. Unknown until then: `<n>` (PR number) and `<run-id>`.

1. **The secret (user).** Confirm `ANTHROPIC_API_KEY` exists. Today `gh secret list --repo wushandarshana/Policy-Forge` is empty.
   The user runs `gh secret set ANTHROPIC_API_KEY --repo wushandarshana/Policy-Forge` and pastes the key at the prompt (never on a command line). Optional: `gh variable set PF_REVIEW_MAX_BUDGET_USD --repo wushandarshana/Policy-Forge --body 5.00`. Check with `gh secret list --repo wushandarshana/Policy-Forge` (names only). Without the secret `claude-review` fails closed.
2. **Push and open the PR.** Precondition: amendment C committed, review gate passed, `/sprint-archive` done, `git branch --show-current` is `chore/ci-hardening`, `git status` clean. `/pr` runs `git push -u origin chore/ci-hardening` and `gh pr create --repo wushandarshana/Policy-Forge --base dev --head chore/ci-hardening`. This is the first real Actions run (`pull_request`). Expected: `build`, `frontend-unit`, `quality-gates` green; `e2e` green with 20 `visual-baseline-missing` entries (all 20 Linux baselines absent, so the policy skips only the visual assertions); `claude-review` green with the first live review using the secret.
3. **Follow the run.** `gh pr checks <n> --watch --repo wushandarshana/Policy-Forge`. On a failure: `gh run list --repo wushandarshana/Policy-Forge --branch chore/ci-hardening --limit 5`, then `gh run view <run-id> --log-failed --repo wushandarshana/Policy-Forge`.
4. **Fix loop.** A Linux-only failure gets a local fix commit by a fix agent under the usual TDD rules (red test first where code changes), then `git push`. Record it in `docs/fix-loops/` and `docs/post-mortems/` (environment first). Things most likely to need it, from the open issues: `--max-turns` rejected by CLI 2.1.311 (drop it from `run_pass`, update the argv test), `setup-java` or the `actions/cache` restore inside the Playwright container, the hook suite and script tests on Linux (line endings, `python3`), a Python 3.11 syntax problem on GitLab only.
5. **The label.** `gh label create update-linux-snapshots --description "Generate -linux Playwright baselines in CI" --repo wushandarshana/Policy-Forge`, then `gh pr edit <n> --add-label update-linux-snapshots --repo wushandarshana/Policy-Forge`. This starts the first `Linux snapshots` run (`gh run list --workflow "Linux snapshots" --repo wushandarshana/Policy-Forge --limit 1` gives `<run-id>`). Do not use a `workflow_dispatch` run yet: the file is not on `main`.
6. **Download.** `gh run download <run-id> --name linux-snapshot-baselines --dir frontend/e2e/__snapshots__ --repo wushandarshana/Policy-Forge`. Confirm exactly these 20 new files and no other new or changed file (`git status --short` shows 20 untracked PNGs and nothing else), then view every image (masked dates and numbers, no clipped or blank panels):
   ```
   apply-underwrite.spec.ts-application-status-desktop-chromium-linux.png
   apply-underwrite.spec.ts-application-status-mobile-chromium-linux.png
   apply-underwrite.spec.ts-apply-form-desktop-chromium-linux.png
   apply-underwrite.spec.ts-apply-form-mobile-chromium-linux.png
   dashboard.spec.ts-dashboard-kpi-tiles-desktop-chromium-linux.png
   dashboard.spec.ts-dashboard-kpi-tiles-mobile-chromium-linux.png
   policy-lifecycle.spec.ts-cancel-refund-preview-desktop-chromium-linux.png
   policy-lifecycle.spec.ts-cancel-refund-preview-mobile-chromium-linux.png
   policy-lifecycle.spec.ts-endorse-sum-insured-preview-desktop-chromium-linux.png
   policy-lifecycle.spec.ts-endorse-sum-insured-preview-mobile-chromium-linux.png
   policy-lifecycle.spec.ts-my-policies-card-desktop-chromium-linux.png
   policy-lifecycle.spec.ts-my-policies-card-mobile-chromium-linux.png
   policy-lifecycle.spec.ts-policy-versions-tab-desktop-chromium-linux.png
   policy-lifecycle.spec.ts-policy-versions-tab-mobile-chromium-linux.png
   policy-lifecycle.spec.ts-refunds-tab-desktop-chromium-linux.png
   policy-lifecycle.spec.ts-refunds-tab-mobile-chromium-linux.png
   policy-lifecycle.spec.ts-renew-panel-offer-desktop-chromium-linux.png
   policy-lifecycle.spec.ts-renew-panel-offer-mobile-chromium-linux.png
   workbench.spec.ts-workbench-case-detail-desktop-chromium-linux.png
   workbench.spec.ts-workbench-case-detail-mobile-chromium-linux.png
   ```
   The artifact is flat (the upload path is the glob `frontend/e2e/__snapshots__/*-linux.png`), so `--dir` puts the files in place. These are the same 10 names times 2 projects as the 20 committed `-win32` files.
7. **Commit the baselines** with explicit paths (`git add frontend/e2e/__snapshots__/<each of the 20 names>`; never `git add -A`) as `test: Linux visual baselines generated in CI run <run-id> (mcr.microsoft.com/playwright:v1.64.1-noble)`, then `git push`. Then `gh pr edit <n> --remove-label update-linux-snapshots --repo wushandarshana/Policy-Forge`. Note: the first `-linux` file makes Linux a baselined platform, so from this commit on a missing or differing Linux visual fails.
8. **The second CI run.** `e2e` must assert the Linux visuals: 0 `visual-baseline-missing` entries (the job summary and artifact `visual-baselines-missing` must be absent or empty) and both projects green. `claude-review` reviews the baseline commit. If one visual flakes by 1 px (`my-policies-card`, desktop, seen once locally), `retries: 1` should absorb it; if a baseline is wrong, regenerate by removing the label and adding it again, never by hand.
9. **GitLab (user).** If the user mirrors to GitLab: `ANTHROPIC_API_KEY` masked and protected, with Environment scope `claude-review` (the job uses `environment: claude-review`); optional `PF_GITLAB_REVIEW_TOKEN` (masked, protected, same scope) and `PF_REVIEW_MAX_BUDGET_USD`; protect the branch patterns `feat/*`, `fix/*` and `chore/*`; enable "Pipelines must succeed". The first pipeline is theirs (SG-11).
10. **Required checks (G-8).** After a fully green run the user approves, then the orchestrator runs the PUT below, which keeps the three existing rules exactly as read on 2026-11-04 and adds `required_status_checks`:
    ```
    gh api -X PUT repos/wushandarshana/Policy-Forge/rulesets/26032911 --input ruleset.json
    ```
    with `ruleset.json` (write it to a scratch path, not into the repository):
    ```json
    {
      "name": "Safety Guard Rails",
      "target": "branch",
      "enforcement": "active",
      "bypass_actors": [],
      "conditions": {"ref_name": {"include": ["refs/heads/main", "refs/heads/dev"], "exclude": []}},
      "rules": [
        {"type": "deletion"},
        {"type": "non_fast_forward"},
        {"type": "pull_request", "parameters": {
          "allowed_merge_methods": ["merge", "squash", "rebase"],
          "dismiss_stale_reviews_on_push": false,
          "require_code_owner_review": false,
          "require_extra_approval_for_unattributed_changes": true,
          "require_last_push_approval": false,
          "required_approving_review_count": 0,
          "required_review_thread_resolution": false,
          "required_reviewers": []}},
        {"type": "required_status_checks", "parameters": {
          "strict_required_status_checks_policy": false,
          "do_not_enforce_on_create": false,
          "required_status_checks": [
            {"context": "build"}, {"context": "frontend-unit"}, {"context": "e2e"},
            {"context": "quality-gates"}, {"context": "claude-review"}]}}
      ]
    }
    ```
    Check with `gh api repos/wushandarshana/Policy-Forge/rulesets/26032911`. Optionally set `allowed_merge_methods` to `["merge"]` in the same call, which would make root `CLAUDE.md`'s "merge commit only" rule a ruleset rule (user decision; not part of G-8). The check contexts are the job names, which equal the job IDs. `build-jar` and `linux-snapshots` are not required checks.
11. **Lessons.** Commit amendment D (the KD rows below, plus lessons from steps 2 to 8) with the message `docs: KD-016.. lessons from Sprint 5 CI`, then `git push`.
12. **Merge (user).** The user merges the PR with "Create a merge commit". The `push` to `dev` then runs CI again, with a push-mode review of the merge commit (P-7: `before..after`, falling back to `after^1..after`).
13. **Release.** A later `/release` puts the workflows on `main`. Only then can `workflow_dispatch` (`Linux snapshots`, and manual `CI` runs) be used.

## Knowledge-deposit drafts (amendment D)
The last row of `docs/knowledge-deposits.md` is KD-015, so these start at KD-016. Dates are the day of discovery. The
"Encoded into" cells name files that exist on the branch; the orchestrator may adjust them after the real runs.

| ID | Date | Mistake / lesson | Encoded into |
|---|---|---|---|
| KD-016 | 2026-11-04 | `anthropics/claude-code-action` accepts only issue, pull request, review, dispatch, schedule and `workflow_run` events and throws `Unsupported event type` on `push`, but the spec requires the review on pushes to `dev` and `main`. The review therefore runs the pinned Claude Code CLI (`--bare -p`) through one shared script on both platforms, which also lets a stub CLI test the gate | `scripts/ci/claude_review.py`; `scripts/tests/test_claude_review.py` (NFR-09.4); plan G-2 |
| KD-017 | 2026-11-04 | A `workflow_dispatch` trigger only works for a workflow file that is on the default branch (`main`), and `main` gets no workflow until `/release`. A snapshot generator that only has a dispatch trigger cannot run in the sprint that adds it. Use a `pull_request` `labeled` trigger (it runs the PR branch's own file) and keep dispatch as the later manual path | `.github/workflows/linux-snapshots.yml`; `test_ci_config.py` (`LinuxSnapshotGeneratorTests`); POST-PUSH steps 5 and 13 |
| KD-018 | 2026-11-04 | `backend/mvnw` was tracked as mode 100644 (the repository was created on Windows), so `./mvnw` fails with "Permission denied" on a Linux runner. `git update-index --chmod=+x backend/mvnw` fixes it without changing content, and a test reads the mode from the index so it cannot regress. Check file modes, not only contents, when the first Linux consumer of a Windows-made repository appears | `backend/mvnw` (100755); `test_ci_config.py` `test_backend_mvnw_is_executable_in_the_index` (NFR-09.1) |
| KD-019 | 2026-11-04 | Playwright's missing-snapshot handling depends on `--update-snapshots`: in the default mode a missing baseline is written and the test FAILS, with `missing`, `changed` or `all` it is written and the test passes, with `none` it fails. A Linux run with only `-win32` baselines would therefore fail every visual test, and a naive `--update-snapshots` in the regular job would hide real regressions. All visual assertions go through `expectVisual`, whose pure policy skips only the visual assertion (and records it by name) on a platform with no baselines, and asserts everywhere else. `-linux` files are generated only in update mode, in CI, and committed after review | `frontend/e2e/visual-baseline-policy.ts`; `frontend/e2e/support.ts` (`expectVisual`); `frontend/src/visualBaselinePolicy.test.ts` (NFR-09.5); `frontend/e2e/CLAUDE.md` |
| KD-020 | 2026-11-04 | On GitLab a "protected" variable reaches only protected refs, so merge request pipelines from unprotected `feat/*`, `fix/*` and `chore/*` branches get no `ANTHROPIC_API_KEY` and the required review fails closed. Protect those branch patterns. Also, GitLab hands every job of a pipeline all project variables, so `build` and `e2e`, which run the merge request's code, would receive the key too. Scope the variable to the environment `claude-review`, which only the review job declares | `.gitlab-ci.yml` (`environment: claude-review`); `test_ci_config.py` (NFR-09.3); `docs/harness-setup.md` "Add the API key" (amendment C) |
| KD-021 | 2026-11-04 | An AC-07.7 race test that holds the losing request at the policy `saveAndFlush` cannot work: both requests append policy version n + 1, and with IDENTITY ids the held request has already inserted its own uncommitted `policy_version` row, so the other request's insert waits for the H2 lock timeout and fails with a 500 instead of the expected 409. Hold the loser just before `policyVersionRepository.save` (test-configuration `BeanPostProcessor`, no production hook). The race is then decided by the unique version key, mapped to 409 `POLICY_UPDATE_CONFLICT`, and the policy `@Version` check stays covered by `RenewalRaceIT` | `RenewalRepriceRaceIT` (class Javadoc); `TASK-001-red.log` "DESIGN EXPERIMENT" |
| KD-022 | 2026-11-04 | A characterisation test that passes on unchanged code proves nothing until it is made to fail: AC-07.7 and AC-08.5 were committed as `test:` only after temporary, never-committed faults (unique-violation mapping off, `repriceOffer` skipped, HALF_EVEN rounding, `<=` at the cooling-off edge, fee on a cooling-off refund) each failed a named assertion. `premium_check.py refund` has no cooling-off or effective-date flag, so cooling-off rows are cross-checked through `refund(..., cooling_off=N)`, and the 1,000-row CSV is regenerated and diffed by `--check` so it cannot be edited by hand | `docs/tdd.md` P-3 practice; `scripts/gen_refund_oracle_cases.py --check` (gate `oracle-fixture`); `ProRataRefundOracleCrossCheckTest` |
| KD-023 | 2026-11-04 | `ac_audit.py` counts ANY reference to an ID, even in a docstring or comment, as coverage, so naming NFR-09.1, NFR-09.2 or NFR-09.5 in a test before its red commit made a vacuous 89/92 look like progress. Reference an ID only in the test that proves it. The offline audit prints only the summary under `--no-write`, so per-ID status needs the module's `collect_criteria` and `collect_references` | `scripts/tests/test_ac_audit_globs.py` (NFR-09.6); `scripts/README.md` |
| KD-024 | 2026-11-04 | Tests that parse CI YAML must fail, never skip, when PyYAML or a CI file is missing: with the system Python (no PyYAML) 22 of 138 script tests fail on purpose. YAML 1.1 also reads the GitHub key `on:` as boolean `True`. Run the gates and the tests with an interpreter that has `scripts/requirements-ci.txt` installed (`.venv/Scripts/python.exe` locally; CI runs `pip install -r scripts/requirements-ci.txt` first) | `scripts/requirements-ci.txt`; `scripts/tests/test_ci_config.py` (handles `True`); `scripts/ci/quality_gates.py` |
| KD-025 | 2026-11-04 | `git commit -m ... -- <path>` re-adds a path that was removed from the index with `git rm --cached` while the file still exists on disk, so the untracking commit did not delete `frontend/coverage/lcov.info` from the index. Commit an untracking with no pathspec (after `git rm --cached`) and verify with `git ls-files` | `git ls-files frontend/coverage` (empty); `.gitignore` (`frontend/coverage/`) |
| KD-026 | 2026-11-04 | A 1 px visual flake exists on `my-policies-card` (desktop): Playwright checks the image SIZE before `maxDiffPixelRatio`, so a card that lays out 289 px instead of 290 px fails regardless of the tolerance. It appeared once, on the first start of a fresh JAR, and never in the three later runs of TASK-003 or in this task's two. CI has `retries: 1`. If it recurs on Linux, fix the layout (a fixed height) or mask the element rather than raising a tolerance | `frontend/playwright.config.ts` (`retries`); `TASK-003.md` open issues |
| KD-027 | 2026-11-04 | `./mvnw package` does not clean `target/classes/static`, so a local JAR accumulates old `assets/index-*.js` bundles (a long-lived local JAR held several). `index.html` names the current one, so it is harmless, but a JAR meant for E2E or for evidence is built with `clean package`. CI builds from a clean checkout | `TASK-005.md`; `.github/workflows/linux-snapshots.yml` (`build-jar`) |
| KD-028 | 2026-11-04 | With `needs`, GitHub shows `claude-review` as skipped when a required job fails, and a skipped required check counts as passing. The merge is still blocked because the failed job is itself a required check, so all five job names must be in the ruleset (job `name:` equals the job ID so the contexts are stable). Also, the ruleset allows merge, squash and rebase while root `CLAUDE.md` allows only a merge commit | `docs/harness-setup.md` "Required checks" (amendment C); POST-PUSH step 10 |
| KD-029 | 2026-11-04 | Same-repo pull requests run their own copy of `ci.yml`, `scripts/ci/claude_review.py` and `.claude/agents/*.md`, so anyone with push access can read `ANTHROPIC_API_KEY` in the `claude-review` job (accepted, SG-10), and the CLI's own npm dependencies are not locked (SG-13, finding F3). The controls are who has write access, reviewing changes to those three paths before they run with the key, a key scoped to review only and a spend cap | `docs/harness-setup.md`; plan SG-10 and SG-13 |

## Open issues
- **Nothing in CI ran on Linux or on GitHub yet.** There is no Docker or WSL here. The container jobs, `setup-java` inside the
  Playwright container, the `actions/cache` restore there, the Linux behaviour of the hook suite and of the script tests, and
  the real Claude CLI flags (`--max-turns` is hidden in 2.1.318 and untested on 2.1.311) are first exercised by POST-PUSH
  step 2. The static layers pass: actionlint with shellcheck, both JSON schemas, the 27 config tests and the pin comparison.
- **The system `python` fails 22 script tests** because PyYAML is missing (by design, KD-024). The criterion
  "`python -m unittest discover -s scripts/tests -v` passes" is met with the `.venv` interpreter, which equals what the
  `quality-gates` job does after `pip install -r scripts/requirements-ci.txt`. A fresh clone on this machine needs the `.venv`.
- **`ac_audit.py` prints a mis-encoded separator on Windows** when piped (`·` shows as `?`); the numbers are right.
  Not changed (out of scope).
- **The 1 px flake of `my-policies-card`** did not occur in this task's two Playwright runs (22 + 22 passed first time).
- **Stale local JAR assets** (KD-027) were removed for this task by `clean package`; `backend/target/` now holds only the fresh build.
- **Commit trailer.** TASK-002 and TASK-004 used `Co-Authored-By: Claude Sonnet 5.5`, the host's attribution rule, instead of
  the "Opus 5.5" text in the task prompt. The orchestrator should decide whether the history needs one name.
- **Amendment C needs one adjustment** (from TASK-004): in `docs/harness-setup.md` "Add the API key", the GitLab bullet should
  say to set the Environment scope of `ANTHROPIC_API_KEY` (and `PF_GITLAB_REVIEW_TOKEN`) to `claude-review`.
- **Candidate SG-14** (Maven distribution checksum) is described above and not numbered by the plan.
- TASK-005 did not start the `security-reviewer` or `test-engineer` agents (this generator has no subagent tool). The
  planned review gate before `/sprint-archive` and `/pr` still has to run them.
