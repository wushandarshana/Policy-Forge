> Archived from `.sprint/20261104-075010/` on 2026-11-05 · branch `chore/ci-hardening` · harness agent-harness 2.8.0. Content below is unchanged from the harness artifact.

# Sprint Plan

## Spec
PolicyForge Sprint 5 — CI hardening (branch chore/ci-hardening, cut from dev @ d9a5694). Read root CLAUDE.md (Stack: 'GitLab CI (.gitlab-ci.yml, added in Sprint 5) with a Claude Code review stage'), AGENTS.md, docs/harness-setup.md, docs/tdd.md, docs/knowledge-deposits.md, .claude/** (agents, skills, commands, hooks, plugin.json), scripts/ac_audit.py, scripts/redact_evidence.py, backend/pom.xml, frontend/package.json and playwright.config.ts, and the Sprint 4 follow-ups in docs/prs/PR-010.md. USER DECISIONS (2026-11-04): build BOTH (1) `.gitlab-ci.yml` meeting the capstone's GitLab CI requirement and (2) an equivalent GitHub Actions workflow under `.github/workflows/` that actually runs on this GitHub repo (origin https://github.com/wushandarshana/Policy-Forge) for pushes and PRs into dev and main. Same jobs and gates in both. The Claude Code review stage is REQUIRED to pass and uses the `ANTHROPIC_API_KEY` secret (GitHub repository secret / GitLab masked protected variable) that the user is adding; never echo the key, never commit a key, never set NODE_TLS_REJECT_UNAUTHORIZED=0. GitHub: use the official `anthropics/claude-code-action` pinned to a released version (or a pinned `@anthropic-ai/claude-code` CLI in headless `-p` mode) with least-privilege `permissions:` (contents: read, pull-requests: write only where it comments); GitLab: pinned Claude Code CLI in headless mode, posting its review as a job artifact (and an MR note when a token exists). The review prompt must reuse the project's own `.claude/agents/security-reviewer.md` and `test-engineer.md` criteria and fail the job on any BLOCKER. Pipeline stages (both platforms): build (./mvnw -B verify with JaCoCo thresholds unchanged, SPA packaged), frontend unit (npm ci, npm test with coverage, npm run build), e2e (Playwright on Linux using the official mcr.microsoft.com/playwright image at the version matching package.json, both projects, with HTML report and traces as artifacts on failure), quality gates (python scripts/ac_audit.py --offline --no-write must report 0 NOT COVERED and 0 PARENT-ONLY; python scripts/redact_evidence.py --check over sprint-contracts specs/reviews docs/fix-loops must exit 0; the .claude hook regression suite python .claude/hooks/tests/test_hooks.py must pass; an LF line-ending check on .claude/hooks/*.sh), and the Claude review. Cache Maven and npm. Java 21 Temurin, Node matching the repo. Pin every action/image by version (or SHA). LINUX SNAPSHOTS: baselines exist only as -win32. No Docker/WSL is available locally, so Linux baselines can only be generated in CI. Planner must propose (as a G-n checkpoint item) how: e.g. a manually-dispatched GitHub workflow that runs the Playwright container with --update-snapshots and uploads the `-linux` PNGs as an artifact that the orchestrator downloads with `gh run download` and commits in a separate commit after review, while the regular e2e job runs visual assertions only when the platform's baselines exist (no silent skip of the functional E2E assertions). Generators never push; anything that requires the branch to be on GitHub must be a post-push orchestrator step listed explicitly in the plan. OTHER SPRINT 5 ITEMS (Sprint 4 follow-ups): stop tracking frontend/coverage/lcov.info (git rm --cached, .gitignore, and adjust any script, test or doc that expects it committed); an ArchUnit rule that only EndorsementService may call PolicyTermEntity.repriceOffer, with a negative fixture (security MINOR-A); an IT racing an endorsement re-price against a renewal payment (exactly one wins, no paid term with a re-priced premium); an oracle cross-check for the AC-08.5 property test (an independent BigDecimal recomputation or premium_check.py-generated cases, not the production calculator); pin `@playwright/mcp` in .mcp.json to a fixed version instead of @latest. Record SG-3, SG-5, SG-6 and SG-7 from the Sprint 4 plan as accepted v1 limitations in the relevant specs (a separate `spec:` commit by the orchestrator at the checkpoint, text proposed by the planner); SG-2 and SG-4 are already closed by AC-08.6 and AC-07.8. Update root CLAUDE.md Stack/Commands, AGENTS.md and docs/harness-setup.md to describe both pipelines and how to add the secret (substrate text proposed in the plan; the orchestrator commits substrate). Add a KD entry to docs/knowledge-deposits.md for anything learned. Every test named AC-NN.M or NFR-NN.M per root rule 8 (use NFR-08.x / NFR-02.x / AC-06.x / AC-07.x / AC-08.5 as fits; CI config itself needs a validation step, e.g. actionlint for GitHub and `gitlab-ci-local`-free YAML schema/lint check, run locally by the generator and in CI). Constraints: root CLAUDE.md non-negotiables; no edits to existing Flyway migrations or published rule files; synthetic data only. Commits: generators commit locally on chore/ci-hardening only, TDD order where code/tests change (`test(red)` before `feat(green)`/`fix(green)`; CI config in `ci:` commits), explicit paths, never push. Hard rule KD-014: run long commands in the FOREGROUND. Environment: prefix Maven with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`; Windows host, Git Bash. Expected base: dev = d9a5694, main = 8e265d6, both unchanged; nothing pushed.

### Interpretation (read this at the plan checkpoint)

**How to read this plan.** The spec above and the root `CLAUDE.md` non-negotiables are the source of truth.
- **G-1 to G-8** are the user's decisions at this checkpoint. Each has a planner default, and approving the plan
  approves every default. G-1 is the Linux snapshot strategy that the spec asks for.
- **Amendments A to D** give the exact substrate text. The main session commits it, because generators never commit
  substrate. The only exception is the TASK-001 fallback for A and B.
- **P-1 to P-16** are planner calls on what the spec leaves open. Veto any of them here.
- **SG-8 to SG-13** are new spec gaps. This sprint records them but does not fill them. SG-1 to SG-7 are Sprint 4's:
  SG-3, SG-5, SG-6 and SG-7 become accepted limitations in amendment A, and SG-1, SG-2 and SG-4 are already closed.
- **C1 to C12** fix names, commands, pins and shapes, so every task can start cold.
- The **POST-PUSH orchestrator steps** at the end can only happen once the branch is on GitHub. No generator criterion
  depends on them.

If a criterion seems to disagree with the spec, the spec wins and the criterion is a plan defect.

**Readiness check (planner, 2026-11-04).**
- **Git.** `chore/ci-hardening` = `dev` = `origin/dev` = `d9a5694`, and the tree is clean. `main` = `origin/main` =
  tag `v0.1.0` = `8e265d6` (checked with `git ls-remote origin`). No `origin/chore/ci-hardening` exists.
- **No CI yet.** There is no `.github/` and no `.gitlab-ci.yml`. GitHub reports 0 workflows. The repository is
  **public**, the default workflow token is `read`, Actions allows all actions (SHA pinning is not enforced), and no
  Actions secret exists yet. The user is adding `ANTHROPIC_API_KEY`.
- **Ruleset.** `Safety Guard Rails` (id 26032911) covers `refs/heads/main` and `refs/heads/dev` with deletion,
  non-fast-forward and pull request (0 approvals) rules. It has **no required status checks** (G-8).
- **Gates are green today.**
  - `python scripts/ac_audit.py --offline --no-write` gives 85/85 covered, 0 parent-only.
  - `redact_evidence.py --check sprint-contracts specs/reviews docs/fix-loops` gives total 0.
  - `python .claude/hooks/tests/test_hooks.py` prints ALL PASSED.
  - All six `.claude/hooks/*.sh` files are `i/lf w/lf`.
- **`backend/mvnw` is tracked as mode 100644.** The hooks are 100644 too, but they run through `bash`. On Linux,
  `./mvnw` fails with "Permission denied", so TASK-004 sets it to 100755. The wrapper downloads Maven 3.9.9.
- **lcov.** `frontend/coverage/lcov.info` is tracked through the `.gitignore` exception `!frontend/coverage/lcov.info`.
  - No script or test reads it. Only `frontend/CLAUDE.md` says it is committed, and amendment B changes that.
  - Historical records mention it and stay unchanged (P-8): `docs/prs/PR-002.md`, `docs/releases/RELEASE-v0.1.0.md`,
    the reviews and the sprint contracts.
  - `coverage/backend/jacoco.xml` is also tracked. It is out of scope.
- **Playwright.** `@playwright/test` is `^1.64.1` in `package.json` and `1.64.1` in the lock.
  - There are 20 baselines, all `-win32` (10 names × 2 projects), and none `-linux`.
  - The 10 names are `apply-form` and `application-status` (apply-underwrite), `dashboard-kpi-tiles` (dashboard),
    `my-policies-card`, `policy-versions-tab`, `endorse-sum-insured-preview`, `renew-panel-offer`,
    `cancel-refund-preview` and `refunds-tab` (policy-lifecycle), and `workbench-case-detail` (workbench).
  - All 10 call `toHaveScreenshot` directly.
- **Missing snapshots in Playwright 1.64.1** (`handleMissing`):
  - With `--update-snapshots=missing`, `changed` or `all`, a missing snapshot is written and the assertion passes.
  - In the default mode it is written and the test fails (a soft error).
  - With `none` it fails.

  So a Linux run with no baselines fails every visual test unless an explicit policy decides what to do (G-1, NFR-09.5).
- **Node.** `frontend.node.version` in `backend/pom.xml` is `v22.11.0`, which frontend-maven-plugin uses. This machine
  runs Node 24.20.0 and npm 11.19.0. There is no `.nvmrc` and no `engines` field.
- **Java.** Locally Temurin 21.0.12.1+1. The latest Temurin 21 GA is 21.0.13+9 (2026-10-22).
- **MCP.** `.mcp.json` runs `@playwright/mcp@latest`. The local npx cache holds 0.0.84, installed 2026-11-03 and used
  by the Sprint 4 evaluator. 0.0.84 is also the current latest (2026-10-31).
- **`anthropics/claude-code-action` can't review pushes.**
  - v1.0.268 is the latest release (2026-11-04).
  - Its `src/github/context.ts`, read at v1.0.266, accepts only `issues`, `issue_comment`, `pull_request`,
    `pull_request_review`, `pull_request_review_comment`, `workflow_dispatch`, `repository_dispatch`, `schedule` and
    `workflow_run`.
  - For any other event, `push` included, it throws `Unsupported event type` (G-2).
- **`workflow_dispatch` needs the workflow on the default branch.** `main` gets no workflow before `/release`, so a
  snapshot workflow that only has a dispatch trigger can't run this sprint (G-1).
- **The Claude Code CLI.**
  - `@anthropic-ai/claude-code` has dist-tag `stable` = 2.1.311 (2026-10-26) and `latest` = 2.1.318 (2026-11-03). It
    requires Node ≥ 22.
  - The headless docs and the CLI reference document: `--bare` (recommended for CI, no hooks, CLAUDE.md, MCP or
    plugins, key only from `ANTHROPIC_API_KEY`), `--output-format json` with `--json-schema` (the result is in
    `structured_output`, which is absent when there is no valid output), `--tools`, `--allowedTools`,
    `--disallowedTools`, `--permission-mode dontAsk`, `--max-turns`, `--max-budget-usd`, `--no-session-persistence`
    and `--strict-mcp-config`.
  - The installed 2.1.318 lists them in `--help`. `--max-turns` is documented but hidden.
  - The planner couldn't execute the 2.1.311 win32 binary: the npm download was not runnable here. TASK-002 re-checks
    2.1.311, and the runner fails closed on any version mismatch.
- **`repriceOffer`.** `PolicyTermEntity.repriceOffer` has exactly one production caller, `EndorsementService` line 150.
  - `PolicyTermEntityTest` also calls it, but ArchUnit imports production classes only.
  - `PolicyTermEntity` has no `@Version`. The policy row's `@Version` serialises the re-price and the pay, because
    both `saveAndFlush` the policy after writing their rows.
  - No IT races the two (Sprint 4 security re-review note).
- **AC-08.5 and the oracle.** `ProRataRefundPropertyTest` checks invariants only. The oracle's
  `premium_check.py refund(premium, term_start, term_end, cancel, fee, effective=None, cooling_off=0)` has no
  first-term flag, so a renewal term is `cooling_off=0`.
- **Oracle values for the race** (`premium_check.py`): the AC-07.7 re-price gives 9490.00. The current-term adjustment
  6850.00 → 9490.00, effective 2027-10-05, in term 2026-11-01..2027-10-31, is 195.29 (27/365). `spec-examples` passes.
- **Pins.** Every pinned version and checksum was verified on 2026-11-04 (C3).

**Sprint base commit: `d9a5694`.** All git-history checks count the commits after it.

**Primary deliverable.** CI that gates every change into `dev` and `main`, on both platforms the spec names:
- A GitHub Actions workflow that really runs on `origin`, and an equivalent `.gitlab-ci.yml`. Both have the same five
  jobs: `build`, `frontend-unit`, `e2e`, `quality-gates` and a required `claude-review`. They use the same scripts, so
  the gates can't drift apart.
- A repeatable path to `-linux` visual baselines that never silently skips functional E2E assertions.
- The Sprint 4 follow-ups closed:
  - lcov is untracked.
  - An ArchUnit rule keeps `repriceOffer` with `EndorsementService` only.
  - A deterministic re-price versus pay race IT.
  - The AC-08.5 oracle cross-check.
  - `@playwright/mcp` is pinned.
  - SG-3, SG-5, SG-6 and SG-7 are recorded as accepted limitations.

**Who depends on it.**
- `/pr` and `/release`: merges become "green CI only" once G-8 is applied.
- The capstone's GitLab CI requirement.
- Every later sprint's evaluator and reviewers: Linux baselines and the required review.
- The v0.2.0 release, which ships the Sprint 4 follow-ups.

**Done looks like (local, before any push):**
- The three CI files exist. actionlint (with shellcheck) reports 0 findings. The GitHub-workflow and GitLab-CI JSON
  schemas validate. The config tests (NFR-09.1 to 09.3) pass.
- `python scripts/ci/quality_gates.py` passes every gate on this machine. `python -m unittest discover -s scripts/tests -v`
  passes.
- A full `./mvnw verify` (SPA packaged) is green, with the JaCoCo thresholds unchanged at 80% domain and 70% overall.
- `npm test` and `npm run build` are green, and the tree stays clean afterwards (lcov is ignored).
- Playwright passes on `desktop-chromium` and `mobile-chromium`: once with the default server, and twice with the
  packaged JAR (C8). No snapshot changes.
- `python scripts/ac_audit.py --offline --no-write` reports **92/92** (the 85 existing IDs + NFR-08.1 + NFR-09.1–09.6)
  and 0 parent-only.
- History after `d9a5694`:
  - Amendments A and B come first.
  - Every new ID has a red commit before its green, except the two characterisation IDs of P-3.
  - No Flyway migration or published rule file changed. Nothing was pushed.

**Done after push** (POST-PUSH steps, not generator criteria):
- The first CI run on the PR is green, with Linux visuals skipped and listed.
- The `-linux` baselines are generated in CI, reviewed and committed.
- The second run is green with Linux visuals asserted.
- The required checks are configured (G-8).

**Scope.** Each ID is owned by at least one task:

| ID | Owned by |
|---|---|
| NFR-08.1 (repriceOffer caller rule) | TASK-001 |
| AC-07.7 (re-price versus pay race) | TASK-001 |
| AC-08.5 (oracle cross-check) | TASK-001 |
| NFR-09.1, NFR-09.2 | TASK-004 (TASK-003 pins `@playwright/test`; amendment B pins `@playwright/mcp`) |
| NFR-09.3 | TASK-002 (redaction, review runner's environment), TASK-004 (CI definitions) |
| NFR-09.4 | TASK-002 |
| NFR-09.5 | TASK-003 |
| NFR-09.6 | TASK-002 (`quality_gates.py`, LF check), TASK-001 (oracle `--check`), TASK-004 (wiring, lint) |
| Untrack lcov | TASK-003 |
| `-linux` baselines | POST-PUSH steps (G-1). The tooling is in TASK-003 and TASK-004 |

**Not in this sprint:**
- creating the GitLab project or running a real GitLab pipeline (SG-11);
- changing the GitHub ruleset or creating labels and secrets (POST-PUSH steps and the user);
- upgrading Node 22.11.0 (SG-12);
- running the full agent mode of `ac_audit.py` in CI (P-10);
- hash-locking the transitive pip dependencies (SG-13);
- untracking `coverage/backend/jacoco.xml`;
- `/release` to `main`;
- any change to hook code under `.claude/hooks/` (human-only substrate; optional O-1 below).

#### Checkpoint decisions (G-1 to G-8). Approving the plan approves each default; veto any of them here.

- **G-1. Linux snapshot baselines** (the spec asks for a checkpoint decision).
  - **Planner default: a CI-only generator, triggered by a PR label, plus a baseline-aware E2E helper.**
  - **The generator.** A second GitHub workflow, `Linux snapshots` (`.github/workflows/linux-snapshots.yml`), has two
    triggers:
    - `pull_request` of type `labeled` into `dev` or `main`, which runs only when the label is
      `update-linux-snapshots`;
    - `workflow_dispatch`, usable only once the file is on `main` (readiness check).

    It builds the packaged JAR and runs the same Playwright image and server as the `e2e` job (C3, C8), with
    `PF_VISUAL_BASELINES=update` and `--update-snapshots=missing`. It uploads only the written
    `frontend/e2e/__snapshots__/*-linux.png` files as the artifact `linux-snapshot-baselines`, plus the HTML report.
  - **GitLab parity.** GitLab gets the same job, `e2e-update-linux-snapshots`, with `when: manual` and
    `allow_failure: true`.
  - **The commit.** The orchestrator downloads the artifact with `gh run download`, checks for exactly the 20 expected
    files, views them, and commits them in a separate commit after review (POST-PUSH steps 6–8).
  - **The E2E helper.** Every visual assertion goes through one helper that applies the C7 policy. On a platform with
    no committed baselines it skips only the visual assertion and records it by name, and the test's functional
    assertions run as usual. On a platform with at least one baseline, a missing or differing baseline fails.
  - The regular `e2e` job therefore passes before the Linux baselines exist, and needs no edit afterwards: the first
    `-linux` file makes Linux a baselined platform.
  - *If vetoed:*
    - **Dispatch only:** the baselines then can't be made before `/release` puts the workflow on `main`.
    - **Commit the e2e job's failure output instead:** this needs the default mode, where every visual test fails until
      the baselines exist, so the first CI runs are red.
- **G-2. How the Claude review runs.**
  - **Planner default: the pinned `@anthropic-ai/claude-code@2.1.311` CLI** (the npm `stable` tag) in headless
    `--bare -p` mode, through one shared runner, `scripts/ci/claude_review.py`, on **both** platforms.
  - The official action can't run on `push` (readiness check), and the spec requires the review on pushes into
    `dev` and `main`.
  - One runner also keeps the prompt, the verdict schema and the BLOCKER gate identical on both platforms, and lets
    them be unit-tested with a stub CLI.
  - *If vetoed:* `anthropics/claude-code-action@c0949c50a78c1ec8ce2647db36629b870e715d0c` (v1.0.268) for
    `pull_request` events, plus the CLI runner for `push`. That gives two code paths to keep equal.
- **G-3. Review passes, models and cost.**
  - **Planner default: two passes per run.**
    - Each pass uses its agent file's frontmatter `model`: `security-reviewer` → `opus`, `test-engineer` → `sonnet`.
    - Each is capped by `--max-turns 40` and `--max-budget-usd 5.00`, overridable through the CI variable
      `PF_REVIEW_MAX_BUDGET_USD`.
    - A pass that hits a cap returns no verdict, so the job fails closed.
    - A run costs at most about 10 USD (client-side estimate). A large `dev` → `main` PR may need a higher variable.
  - *If vetoed:* one combined pass on `sonnet` with both checklists and a 5.00 cap.
- **G-4. Gate semantics.**
  - **Planner default: the job fails if any finding in either pass has severity `BLOCKER`, and only then**, as the
    spec says.
    - A `CHANGES_REQUESTED` verdict without a BLOCKER passes, but its MAJOR and MINOR findings are published and
      listed in the job summary.
    - A missing key, a CLI error, a wrong CLI version, a cap hit, or missing or schema-invalid output also fails the
      job (fail closed).
    - There is no bypass variable.
  - Fork PRs get no secrets, so their review fails (SG-8).
  - On GitLab, the "masked protected variable" the user named reaches only protected refs. The setup text therefore
    protects `feat/*`, `fix/*` and `chore/*`, so MR pipelines receive it (amendment C).
  - *If vetoed:* `CHANGES_REQUESTED` also fails the job.
- **G-5. Spec IDs for the new tests** (root rule 8).
  - The spec suggests NFR-08.x, but app_spec defines no testable NFR-08 or NFR-09 sub-criteria today, and `ac_audit.py`
    treats unknown IDs as orphans.
  - **Planner default:**
    - Add **NFR-08.1** (the repriceOffer caller rule, an architecture rule).
    - Add a new **NFR-09** row ("CI gates") with **NFR-09.1–09.6**.
    - Use the existing AC-07.7 for the race and AC-08.5 for the oracle.
  - The exact text is in amendment A.
  - *If vetoed:* NFR-09.1–09.6 become NFR-08.2–08.7, there is no NFR-09 row, and the NFR-08 row reads "Architecture
    and CI rules are automated tests".
- **G-6. The AC-08.5 oracle cross-check.**
  - **Planner default: generate the cases from `premium_check.py`.**
    - `scripts/gen_refund_oracle_cases.py` imports the oracle's `refund()` read-only from
      `.claude/skills/premium-calc-evaluator/`.
    - It writes 1,000 cases with a fixed seed, plus boundary cases, to a committed CSV (C10).
    - A JUnit test asserts that the production calculator gives exactly each row's values. No expected value comes from
      production code.
    - `--check` regenerates the CSV and diffs it in the quality gates, so the fixture can't be edited by hand.
  - Python `Decimal` against Java `BigDecimal` gives cross-language independence, from the project's sanctioned
    oracle (KD-003).
  - *If vetoed:* an independent BigDecimal recomputation of the spec formula inside the test (same JVM, no fixture,
    no gate).
- **G-7. The E2E server in CI.**
  - **Planner default: the `build` job's packaged JAR.**
    - `playwright.config.ts` starts `java -jar <jar> --policyforge.jobs.renewal-cycle.any-date=true` when
      `PF_E2E_JAR` is set (C8).
    - The JAR is the artifact users run, it starts in seconds, and it doesn't rebuild the SPA or run `npm ci` inside
      the Playwright container.
    - Without the variable, local behaviour is unchanged.
  - *If vetoed:* `./mvnw spring-boot:run` inside the Playwright container. That needs a JDK, a Maven cache and a full
    SPA rebuild in the e2e job, and the `npm ci` in that build replaces `node_modules` while the test runner uses it.
- **G-8. Making CI required.**
  - **Planner default: after the first fully green run** (POST-PUSH step 10), the user approves a change to ruleset
    26032911 that adds `required_status_checks` for `build`, `frontend-unit`, `e2e`, `quality-gates` and
    `claude-review`. The orchestrator prepares the exact `gh api` call.
  - On GitLab, enable "Pipelines must succeed" once a project exists.
  - Until then, "required" is enforced by process: `/pr` waits for green checks (amendment C).
  - *If vetoed:* the checks stay advisory.

#### Amendments (exact substrate text). Approving the plan approves this text.

The main session makes these commits. Generators never commit `specs/`, `docs/`, `CLAUDE.md`, `AGENTS.md`, `.claude/**`,
`plugin.json` or `.mcp.json`, with one exception: if A or B is missing when TASK-001 starts, TASK-001 makes it first,
with exactly this text adjusted for any veto. No other task commits substrate.

**A. At the checkpoint, before any generator.** One commit with the message `spec: Sprint 5 CI criteria NFR-08.1 and
NFR-09.1-09.6, accepted v1 limitations SG-3, SG-5, SG-6 and SG-7 (plan G-5, approved at the Sprint 5 checkpoint)`. It
touches only `specs/app_spec.md`, `specs/renewal-cancellation_spec.md` and `specs/portfolio-dashboard_spec.md`.
- **`app_spec.md` §5 table:** a new row after NFR-08:
  `| NFR-09 | Every push to and pull or merge request into dev and main runs the same automated gates on GitHub Actions and GitLab CI, ending with a required Claude Code review | scripts/tests/ CI tests; actionlint; GitLab CI schema check; the pipelines |`
- **`app_spec.md` "Acceptance Criteria (NFR, testable form)":** new bullets after NFR-04.4:
  - "**NFR-08.1** Given the production classes, when any class other than `EndorsementService` calls
    `PolicyTermEntity.repriceOffer`, then the ArchUnit suite fails. A negative fixture in which a class other than the
    fixture's endorsement service calls it is rejected, and the fixture's endorsement service is not (Sprint 4
    security review MINOR-A)."
  - "**NFR-09.1** Given `.github/workflows/ci.yml` and `.gitlab-ci.yml`, then each defines the jobs `build`,
    `frontend-unit`, `e2e`, `quality-gates` and `claude-review` with the same gate commands. Each runs for pushes to
    `dev` and `main` and for pull or merge requests into them. `claude-review` runs only after the other four jobs
    pass and is never allowed to fail."
  - "**NFR-09.2** Given the CI definitions and `.mcp.json`, then every GitHub action is pinned to a full 40-character
    commit SHA, every container image to an exact version tag with an `@sha256:` digest, every downloaded tool to an
    exact version with a SHA-256 checksum, and every npm package run by CI or MCP to an exact version. Nothing uses
    `latest` or a floating tag. The Playwright image's version equals the `@playwright/test` version locked in
    `frontend/package-lock.json`, and the CI Node version equals `frontend.node.version` in `backend/pom.xml`."
  - "**NFR-09.3** Given the CI definitions, then the GitHub workflows default to `permissions: contents: read`, and
    only `claude-review` adds `pull-requests: write`. No workflow uses `pull_request_target`, and every checkout sets
    `persist-credentials: false`. No `run:` script contains a `${{ github.event.… }}` expression. `ANTHROPIC_API_KEY`
    reaches only the review step, from the repository secret or the masked GitLab variable, and is never printed. No
    CI file sets `NODE_TLS_REJECT_UNAUTHORIZED`. `redact_evidence.py` redacts an Anthropic API key (`sk-ant-…`) in
    evidence and review output."
  - "**NFR-09.4** Given a Claude review run, then each pass's prompt contains the body of
    `.claude/agents/security-reviewer.md` or `.claude/agents/test-engineer.md` verbatim, and the pinned Claude Code CLI
    runs in bare, read-only mode against the diff under review. The job fails when any finding has severity BLOCKER,
    when a pass returns no structured verdict or one that fails the verdict schema, when the CLI exits non-zero or is
    not the pinned version, or when `ANTHROPIC_API_KEY` is absent. With no BLOCKER it passes and still publishes every
    MAJOR and MINOR finding. An empty diff passes without calling the CLI."
  - "**NFR-09.5** Given an E2E run on a platform with no committed visual baselines, then each visual assertion is
    skipped and recorded by name, and every functional assertion still runs. Given a platform with at least one
    committed baseline, a missing or differing baseline fails the test. A run in update mode writes only the missing
    baselines of its own platform."
  - "**NFR-09.6** Given the quality gates, then the job fails when the offline AC audit reports any NOT COVERED or
    PARENT-ONLY ID, when `redact_evidence.py --check` finds a credential in `sprint-contracts/`, `specs/reviews/` or
    `docs/fix-loops/`, when the hook regression suite fails, when a `.claude/hooks/*.sh` file has a CR byte in the
    index or the working tree, when the AC-08.5 oracle fixture differs from a fresh generation, or when actionlint or
    the CI schema checks report an error. It runs every gate and reports each result before it fails."
  - *Under a G-6 veto,* drop the oracle-fixture clause from NFR-09.6. *Under a G-5 veto,* renumber per G-5.
- **`renewal-cancellation_spec.md`:** a new section just before `## Acceptance Criteria`:

  ```
  ## Accepted v1 limitations (user decision 2026-11-04, Sprint 5)
  - **Renewal re-rates the stored inputs unchanged (SG-3).** The refreshed premium re-quotes the current version's
    stored risk inputs with the active rule version. Ages (TERM_LIFE `age`, MOTOR `driverAge` and `vehicleAgeYears`,
    HOUSEHOLD `propertyAgeYears`) are not advanced, and underwriting is not re-run at renewal.
  - **An early renewal payment closes the rest of the old term (SG-5).** The current paid term becomes the renewal term
    as soon as it is paid (G-5). After an early payment, the remainder of the previous term can no longer be endorsed
    or cancelled, and only the renewal term's premium is refundable.
  - **A policy that the active rule version rejects gets no offer (SG-7).** When the active rule version rejects a
    policy's stored risk inputs, the cycle creates no offer and logs only the policy number. Such a policy never has an
    unpaid renewal term, so the cycle never lapses it.
  ```
- **`portfolio-dashboard_spec.md`:** a new paragraph after the "Lapse forecast" definition bullet:
  "**Accepted v1 limitation (SG-6, user decision 2026-11-04, Sprint 5).** Terms, endorsement adjustments and refunds
  have no paid-date history, so `premiumCollected` sums every paid term premium, adjustment and refund stored when the
  request is made. It isn't cut at `asOf`. Active counts use each policy's current status, so `asOf` changes only the
  renewal pipeline and the lapse forecast."

**B. At the checkpoint, right after A.** One commit with the message `chore(substrate): pin @playwright/mcp 0.0.84,
repriceOffer caller rule, lcov not committed, visual-baseline policy, CI script rules (Sprint 5 plan, approved at the
checkpoint)`. It touches only the five files below.
- **`.mcp.json`**, whole file:

  ```
  {
    "mcpServers": {
      "playwright": {
        "command": "npx",
        "args": ["-y", "@playwright/mcp@0.0.84"]
      }
    }
  }
  ```
- **`docs/architecture.md` §2**, a new bullet after `ControllerPlacementArchTest`: "- `RepriceOfferCallerArchTest`:
  only `EndorsementService` calls `PolicyTermEntity.repriceOffer`, the one mutator an unpaid renewal offer has besides
  `markPaid` (NFR-08.1, AC-07.7; Sprint 4 security review MINOR-A)."
- **`frontend/CLAUDE.md`:** the last bullet becomes "- Coverage: `vitest --coverage` (v8) writes `coverage/lcov.info`.
  It is **not committed** (`.gitignore`). CI publishes it as the `frontend-coverage` artifact of the `frontend-unit`
  job."
- **`frontend/e2e/CLAUDE.md`:**
  - The "Visual snapshots" bullet becomes "- Visual snapshots: call the visual-baseline helper in `support.ts`, never
    `toHaveScreenshot` directly. Baselines live in `e2e/__snapshots__/` (committed), suffixed by project and platform
    (`-win32`, `-linux`). Mask dynamic values (dates, policy numbers). On a platform with no committed baselines, the
    helper skips only the visual assertion and records it by name (NFR-09.5). On a platform that has baselines, a
    missing or differing one fails. `-linux` baselines are generated only in CI (the `Linux snapshots` workflow or the
    GitLab manual job `e2e-update-linux-snapshots`) and committed in their own commit after review. Never generate or
    edit them by hand."
  - A new bullet: "- When `PF_E2E_JAR` is set (CI), `webServer` starts that packaged JAR with the browser-test setting
    instead of `mvnw spring-boot:run`."
- **`scripts/CLAUDE.md`:**
  - The bullet "Scripts must be read-only with respect to production code. Allowed tools: `Read`, `Glob`, `Grep`."
    gains the prefix "`ac_audit.py`: ".
  - These bullets are appended:
    - "- `ci/` holds the CI entry points, which `.github/workflows/` and `.gitlab-ci.yml` run in the same way:
      - `quality_gates.py` runs every quality gate and reports each result.
      - `claude_review.py` runs the required Claude Code review.
      - `fetch_tools.py` downloads actionlint and shellcheck at pinned versions and verifies their checksums.
      - `check_line_endings.py` checks hook line endings.

      They use the Python standard library only and run on Python 3.11 or later."
    - "- The CI review runs the pinned Claude Code CLI in `--bare` print mode with read-only tools. These are `Read`,
      `Grep` and `Glob`, plus `Bash` limited to `git diff`, `git log`, `git show`, `git ls-files`, `grep` and `rg`. It
      never edits files, never receives a GitHub or GitLab token, and fails closed."
    - "- Tests live in `scripts/tests/` and run with `python -m unittest discover -s scripts/tests -v`. Each test's
      docstring starts with the ID it proves (`NFR-09.4: …`), and `ac_audit.py` counts it."
    - "- `requirements-ci.txt` pins the packages that only the tests and gates need (PyYAML, check-jsonschema)."

**C. After the evaluator PASS, before `/sprint-archive` and `/pr`.** One commit with the message `docs: CI pipelines,
API-key setup, required checks and Linux baselines (Sprint 5)`. Adjust names only if the evaluator found a recorded,
approved deviation.
- **Root `CLAUDE.md`, Stack table:** the CI row becomes `| CI | GitHub Actions (.github/workflows/ci.yml, runs on
  origin) and GitLab CI (.gitlab-ci.yml) with the same jobs: build, frontend-unit, e2e, quality-gates and a required
  Claude Code review (docs/harness-setup.md) |`.
- **Root `CLAUDE.md`, Commands table:** two new rows, `| All CI quality gates | python scripts/ci/quality_gates.py |`
  and `| CI script tests | python -m unittest discover -s scripts/tests -v |`.
- **Root `CLAUDE.md`, Workflow step 4:** it becomes "The `security-reviewer` and `test-engineer` agents review the work,
  then `/sprint-archive` and `/pr` open the PR. It merges only when every CI job is green, including `claude-review`."
- **`AGENTS.md`, `## CI` section:** the whole section becomes:

  ```
  ## CI
  - [`.github/workflows/ci.yml`](.github/workflows/ci.yml): GitHub Actions on `origin` (build, frontend-unit, e2e, quality-gates, claude-review)
  - [`.github/workflows/linux-snapshots.yml`](.github/workflows/linux-snapshots.yml): generates `-linux` visual baselines (PR label `update-linux-snapshots`; manual dispatch once on `main`)
  - [`.gitlab-ci.yml`](.gitlab-ci.yml): the same jobs on GitLab CI
  - CI scripts: [`scripts/ci/`](scripts/ci/) · tests: [`scripts/tests/`](scripts/tests/)
  - Hook regression tests: [`.claude/hooks/tests/test_hooks.py`](.claude/hooks/tests/test_hooks.py)
  ```
- **`docs/harness-setup.md`:**
  - Sprint cycle step 6 becomes "`/sprint-archive` → `/pr` (opens a GitHub PR into `dev`; it is merged with a merge
    commit once every CI job is green). After QA, `/release` promotes `dev` → `main`."
  - This section is appended:

  ```
  ## CI pipelines (Sprint 5)

  GitHub Actions runs on `origin` (`.github/workflows/ci.yml`). `.gitlab-ci.yml` is the equivalent for a GitLab
  project. Both run the same five jobs through the same scripts, for pushes to `dev` and `main` and for pull or merge
  requests into them.

  | Job | What it gates |
  |---|---|
  | `build` | `./mvnw -B verify` with the SPA packaged: unit and integration tests, ArchUnit, JaCoCo (80% domain, 70% overall). Publishes the JAR |
  | `frontend-unit` | `npm ci`, `npm test` (Vitest with coverage; lcov is an artifact, not committed), `npm run build` |
  | `e2e` | Playwright on both projects in `mcr.microsoft.com/playwright` at the locked `@playwright/test` version, against the packaged JAR. The report and traces are kept on failure |
  | `quality-gates` | `python scripts/ci/quality_gates.py`: offline AC audit (0 NOT COVERED, 0 PARENT-ONLY), evidence redaction check, hook regression suite, LF check on hooks, CI script tests, AC-08.5 oracle fixture, actionlint, CI schemas |
  | `claude-review` | Runs after the other four pass. The pinned Claude Code CLI reviews the diff twice, with the `security-reviewer` and `test-engineer` criteria. Any BLOCKER fails the job. The report is an artifact and a PR or MR comment |

  ### Add the API key
  - **GitHub:** run `gh secret set ANTHROPIC_API_KEY --repo wushandarshana/Policy-Forge` and paste the key at the
    prompt. Never put the key on a command line and never echo it. The optional repository variable
    `PF_REVIEW_MAX_BUDGET_USD` sets the cap per review pass (default `5.00`).
  - **GitLab:** in Settings → CI/CD → Variables, add `ANTHROPIC_API_KEY`, **masked** and **protected**. Protected
    variables reach only protected refs, so also protect the branch patterns `feat/*`, `fix/*` and `chore/*`.
    Otherwise merge request pipelines get no key, and the review fails. Optionally add `PF_GITLAB_REVIEW_TOKEN` (a
    project access token with `api` scope, masked and protected) to post the review as an MR note.
  - Never set `NODE_TLS_REJECT_UNAUTHORIZED=0` (KD-015). A PR from a fork gets no secrets, so its review fails closed.

  ### Required checks
  `dev` and `main` accept PRs only (ruleset "Safety Guard Rails"). After the first green run, the ruleset also requires
  the five jobs. On GitLab, enable "Pipelines must succeed". There is no switch that skips the review.

  ### Linux visual baselines
  Baselines are per platform: `-win32` locally, `-linux` in CI. On a platform with no baselines, E2E skips only the
  visual assertions and lists them (NFR-09.5). To create or refresh the `-linux` set:
  1. Add the label `update-linux-snapshots` to the PR. You can also run the `Linux snapshots` workflow by hand once it
     is on `main`, or the GitLab manual job `e2e-update-linux-snapshots`.
  2. Download the result with `gh run download <run-id> --name linux-snapshot-baselines --dir frontend/e2e/__snapshots__`.
  3. Review the images, and commit them in their own commit.
  ```
- **`.claude/hooks/CLAUDE.md`:**
  - "The backstop is a protected `main` on the GitLab remote." becomes "The backstop is the GitHub ruleset on `dev`
    and `main` (PRs only) plus the required CI jobs."
  - A new last bullet: "- CI runs the regression suite and the LF check on `*.sh` in the `quality-gates` job."
- **`.claude/commands/ac-coverage.md`, step 2:** a new bullet "- `scripts/tests/test_*.py` (each test's docstring starts
  with the ID)."
- **`.claude/commands/pr.md`, section 5:** a new bullet after "Print the PR URL": "- Wait for CI with
  `gh pr checks <number> --watch`. The five jobs (`build`, `frontend-unit`, `e2e`, `quality-gates`, `claude-review`)
  must all pass before anyone merges. If `claude-review` reports a BLOCKER, fix it on the branch and push again."
- **`.claude/commands/release.md`:** the same wait-for-CI bullet, after its PR is opened.

**D. After the first CI runs (POST-PUSH step 11).** The `docs/knowledge-deposits.md` rows that TASK-005 drafts (KD-016
onwards), plus any lesson from the real runs, in a commit with the message `docs: KD-016.. lessons from Sprint 5 CI`.

**O-1 (optional, human-only, not needed to pass).** Extend the `ac-tag-check` hook's `TEST_FILE` pattern to
`scripts/tests/test_*.py`, and add one regression case to `.claude/hooks/tests/test_hooks.py`.

#### Planner calls (P-1 to P-16). Veto any of these here.

- **P-1. Decomposition and scheduling.**
  - The exec workflow runs the parallel batch to completion, then the sequential tasks one at a time.
  - **Parallel batch:** TASK-001 (backend tests, plus one new script) and TASK-002 (`scripts/` only, no Maven and no
    npm).
  - **Then:** TASK-003 (frontend and E2E, which needs `backend/target` to itself), TASK-004 (CI definitions, which
    needs every script and helper contract in place), and TASK-005 (verification).
  - A `.git/index.lock` error means another generator is committing. Retry the commit and never delete the lock.
- **P-2. Build flags.**
  - TASK-001 runs Maven with `-Dskip.frontend=true`.
  - TASK-003 is the first task to start the app (Playwright's `webServer` and the JAR build), and it runs alone.
  - TASK-005 runs the full build (SPA packaged).
- **P-3. TDD evidence shape** (Sprints 2–4, plus two additions).
  - A red commit compiles. Its new tests fail on an assertion, not a compile error, and stubs are allowed.
  - **Characterisation IDs: AC-07.7 (race) and AC-08.5 (oracle).** If such a test passes against unchanged production
    code, commit it as `test: AC-NN.M …`. The red log must show a **mutation proof**: a temporary, never-committed
    fault (for example, the policy `@Version` removed, or the refund's final rounding changed to HALF_EVEN) makes the
    new test fail on a named assertion. Once the fault is reverted, the test passes and `git status` is clean.
  - If either test finds a real defect, the usual `test(red)` and `fix(green)` apply.
  - **NFR-08.1:** the red is the rule stubbed to allow every caller, so the negative-fixture test fails. `feat(green)`
    is the real rule, as for Sprint 4's backdated-issuance rule.
  - **CI definitions** turn their config tests green in `ci:` commits that name the IDs, which the spec requires.
  - **Scripts** use `feat(green)`.
  - Untracking lcov and the exact Playwright pin are `chore:` commits.
- **P-4. Test names.**
  - JUnit: `@Tag("NFR-08")` with `@DisplayName("NFR-08.1: …")`, `@Tag("AC-07")` with `"AC-07.7: …"`, and
    `@Tag("AC-08")` with `"AC-08.5: …"`.
  - Vitest: `"NFR-09.5: …"`.
  - Python `unittest`: the first docstring line starts with `NFR-09.N: …`, which `unittest -v` prints and
    `ac_audit.py` counts once TASK-002 adds the glob.
  - The ArchUnit negative-fixture tests are titled `"NFR-08.1 negative fixture: …"`.
- **P-5. Version pins.** Pins are exactly those in C3, all verified on 2026-11-04.
  - GitHub actions: the full SHA, with `# vX.Y.Z` in a comment.
  - Images: `tag@sha256:digest`.
  - Downloaded tools: version and SHA-256.
  - The runner is `ubuntu-24.04`, never `ubuntu-latest`.
  - The Claude Code CLI is `2.1.311`, the `stable` dist-tag, not the day-old `latest`.
- **P-6. CI security posture** (NFR-09.3).
  - **Credentials and tokens.**
    - Top-level `permissions: contents: read`, and `pull-requests: write` only on `claude-review`.
    - No `pull_request_target`.
    - `persist-credentials: false` on every checkout, so the Claude process can't read a token from `.git/config`.
    - The key is given to the review step only, through `env`. The comment step gets the token only, never the key.
    - The runner removes `GITHUB_TOKEN`, `GH_TOKEN`, `CI_JOB_TOKEN` and `PF_GITLAB_REVIEW_TOKEN` from the CLI's
      environment, and sets `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`.
  - **Shell scripts.**
    - No `set -x` in any job.
    - Every event value reaches a script through `env:`, never as a `${{ }}` expression inside `run:`.
  - **The review's own boundaries.**
    - The prompt tells Claude that the diff is untrusted data.
    - The review gets no network tools and no edit tools.
    - Test code builds its fake `sk-ant-` keys by concatenation, so GitHub push protection never sees a key-shaped
      literal.
- **P-7. Diff range of the review.**
  - **GitHub PR:** `merge-base(base.sha, head.sha)..head.sha`.
  - **GitLab MR:** `CI_MERGE_REQUEST_DIFF_BASE_SHA..CI_COMMIT_SHA`.
  - **Push:** `before..after` (`github.event.before`, `CI_COMMIT_BEFORE_SHA`). When `before` is all zeros or is not an
    ancestor, the range falls back to the first parent, `after^1..after`. A push to `dev` or `main` is normally a PR
    merge commit.
  - **Manual dispatch:** `merge-base(origin/dev, HEAD)..HEAD`, or `HEAD^1..HEAD` when HEAD is `dev` or `main`.
  - The review job checks out with `fetch-depth: 0` (GitLab: `GIT_DEPTH: 0`).
- **P-8. lcov.**
  - TASK-003 runs `git rm --cached frontend/coverage/lcov.info` and replaces the two `.gitignore` lines with
    `frontend/coverage/`.
  - `.gitignore` isn't in root rule 1's substrate list, so a generator edits it. TASK-002 adds `.ci-tools/` and
    `ci-out/` the same way.
  - Historical records keep their mentions. `coverage/backend/jacoco.xml` is unchanged.
- **P-9. Python in CI.**
  - The CI scripts use the standard library only and must parse as Python 3.11, because GitLab's review image is
    `node:22.11.0-bookworm` with Debian's Python 3.11. A test asserts this.
  - Test-only packages are pinned in `scripts/requirements-ci.txt`: `PyYAML==6.0.3` and `check-jsonschema==0.39.0`.
  - Generators install them into the gitignored `.venv/`.
  - PyYAML reads the GitHub key `on:` as boolean `True`, so the config tests must handle it.
- **P-10. Job order and cost.**
  - `claude-review` needs the other four jobs, so a red build spends no API money.
  - `e2e` needs `build` (the JAR). `quality-gates` and `frontend-unit` start at once.
  - The full agent mode of `ac_audit.py` isn't run in CI, because the test-engineer pass covers test quality.
    `scripts/README.md` (not substrate) is updated to say so.
- **P-11. Triggers and concurrency (C2).**
  - Pull requests share a concurrency group per ref, with `cancel-in-progress` on PRs only. Pushes to `dev` or `main`
    are never cancelled.
  - Job `name:` equals the job ID, so the check names are stable for G-8.
  - Every job has a timeout: build 40, frontend-unit 15, e2e 45, quality-gates 20 and claude-review 40 minutes.
- **P-12. Caches.**
  - **GitHub:**
    - `setup-java` uses `cache: maven`, which also caches the wrapper's Maven and frontend-maven-plugin's Node download.
    - `setup-node` uses `cache: npm` keyed on `frontend/package-lock.json`.
    - `actions/cache` keeps `~/.npm` for the build job's plugin-run `npm ci` and the container job.
    - `setup-python` uses `cache: pip` keyed on `scripts/requirements-ci.txt`.
  - **GitLab:**
    - `.m2/repository` through `MAVEN_OPTS=-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository`, keyed on
      `backend/pom.xml`.
    - `.npm` through `npm_config_cache=$CI_PROJECT_DIR/.npm`, keyed on `frontend/package-lock.json`.
- **P-13. Effort overrides.** TASK-001, TASK-002 and TASK-004 are raised from `high` to `xhigh`:
  - TASK-001: deterministic concurrency and money exactness.
  - TASK-002: secret handling, a fail-closed gate and checksum verification.
  - TASK-004: the permission model, supply-chain pinning and secret exposure.

  If the orchestrator clamps sonnet to `high`, nothing else changes.
- **P-14. Exact Playwright pin.** `package.json` pins `"@playwright/test": "1.64.1"` with no caret, so the locked
  version and the image (NFR-09.2) can't drift through a later `npm install`. The lock still resolves 1.64.1.
- **P-15. No live API calls by generators.**
  - Generators never send a prompt to the Anthropic API and never use the user's login or a key.
  - The runner is tested with a stub CLI (`PF_CLAUDE_CLI`). Real CLIs are run with `--version` and `--help` only.
  - The first real review is the CI run with the user's secret (POST-PUSH).
- **P-16. No GitHub or GitLab writes by generators.**
  - No `git push`, no `gh` call that writes (labels, secrets, rulesets, PRs, workflow runs), and no GitLab API call.
  - Read-only `gh api` and `git ls-remote` are allowed.

**Spec gaps. Recorded, not implemented:**
- **SG-8.** A PR from a fork gets no secrets, so its review fails closed and the PR can't pass the required check. The
  repository has no outside contributors.
- **SG-9.** Model verdicts aren't deterministic, so a re-run can differ. A false BLOCKER is fixed or argued in the PR
  and the job re-run. There is no bypass.
- **SG-10.** A same-repo PR author can put text in the diff that steers the review (prompt injection). The prompt marks
  the diff as untrusted, the tools are read-only, and the authors are trusted.
- **SG-11.** There is no GitLab remote, so `.gitlab-ci.yml` is validated by its schema and the parity tests only. The
  first real GitLab run is the user's.
- **SG-12.** Node 22.11.0 (from the pom) dates from October 2024, and Node 22 support ends in April 2027. Upgrading
  needs the pom property, a rebuild and a new image pin. That is a separate decision.
- **SG-13.** Only the direct pip dependencies are pinned. The transitive ones aren't hash-locked.

**Cross-task contracts.**

- **C1. Jobs and gate commands.** The names and commands are identical on both platforms, and the config tests assert
  them.

  | Job | Environment | Commands | Artifacts |
  |---|---|---|---|
  | `build` | GitHub: `ubuntu-24.04` + `setup-java` (Temurin 21.0.13+9). GitLab: the Temurin JDK image | `cd backend && ./mvnw -B verify` (SPA packaged, JaCoCo checks) | `policyforge-jar` (`backend/target/policyforge-*.jar`, 7 days). `jacoco-report` (`backend/target/site/jacoco/`, always, 14 days). `backend-test-reports` (surefire and failsafe reports, on failure) |
  | `frontend-unit` | GitHub: `setup-node` 22.11.0. GitLab: the Node image | `cd frontend && npm ci && npm test && npm run build` | `frontend-coverage` (`frontend/coverage/lcov.info`, always, 14 days) |
  | `e2e` | The Playwright image (GitHub `container:` with `--ipc=host`), plus a Temurin 21.0.13+9 JRE (GitHub `setup-java`; GitLab the checksum-verified tarball). Needs `build` | `cd frontend && npm ci && npx playwright test` with `PF_E2E_JAR` set, then print the count and names in `test-results/visual-baselines-missing.txt` | `playwright-report` and `playwright-traces` (`frontend/test-results/`) on failure. `visual-baselines-missing` always, when the file exists |
  | `quality-gates` | GitHub: `setup-python` 3.14.10. GitLab: the Python image | `pip install -r scripts/requirements-ci.txt`, then `python scripts/ci/quality_gates.py` | `quality-gates-report` (always) |
  | `claude-review` | GitHub: `ubuntu-24.04` + `setup-node` 22.11.0 + system `python3`. GitLab: the Node image. Needs the other four | `npm install -g @anthropic-ai/claude-code@2.1.311`, then `python3 scripts/ci/claude_review.py run --base <sha> --head <sha> --out ci-out/review`, then, on a PR or MR, `python3 scripts/ci/claude_review.py comment …` | `claude-review` (`ci-out/review/`, always, 14 days) |

  The GitLab file also has `e2e-update-linux-snapshots` (G-1) and stages `build`, `test`, `e2e` and `review`.
  `quality-gates` and `frontend-unit` are in `test` with `needs: []`, and `claude-review` has `allow_failure: false`.
- **C2. Triggers.**
  - **GitHub `ci.yml`** (name `CI`): `push` to `dev` and `main`, `pull_request` into `dev` and `main` (default types),
    and `workflow_dispatch`.
  - **GitHub `linux-snapshots.yml`** (name `Linux snapshots`): `pull_request` of type `labeled` into `dev` and `main`,
    with job `if: github.event_name == 'workflow_dispatch' || github.event.label.name == 'update-linux-snapshots'`,
    and `workflow_dispatch`.
  - **GitLab `workflow:rules`:**
    - a merge request event whose target is `dev` or `main`;
    - a push whose `CI_COMMIT_BRANCH` is `dev` or `main`;
    - a `web` source.

    Nothing else runs, so there are no duplicate branch and MR pipelines.
- **C3. Pins** (verified 2026-11-04):

  | Item | Pin |
  |---|---|
  | `actions/checkout` | `3d3c42e5aac5ba805825da76410c181273ba90b1` # v7.0.1 |
  | `actions/setup-java` | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` # v6.1.0 |
  | `actions/setup-node` | `949feb2413d6458794dcd2491c4babbbce0c15c1` # v7.1.0 |
  | `actions/setup-python` | `6d4fd22b20465ccc0f7354476588e8df7c9ca0e0` # v7.1.0 |
  | `actions/cache` | `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` # v6.1.0 |
  | `actions/upload-artifact` | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` # v7.0.2 |
  | `actions/download-artifact` | `9000827ccba6bdab643e8b6fd33ac0654aef8333` # v8.0.2 (a digest mismatch now fails by default) |
  | `anthropics/claude-code-action` | not used (G-2). Latest checked: v1.0.268 = `c0949c50a78c1ec8ce2647db36629b870e715d0c` |
  | Claude Code CLI | `@anthropic-ai/claude-code@2.1.311` (npm `stable`; `latest` is 2.1.318) |
  | Playwright MCP | `@playwright/mcp@0.0.84` (amendment B) |
  | Playwright image | `mcr.microsoft.com/playwright:v1.64.1-noble@sha256:2f05f8a734509a7b3f26a912639488ee08f58a7b292d14d62271bcff9038768f` |
  | Temurin JDK image (GitLab `build`) | `eclipse-temurin:21.0.13_9-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d` |
  | Temurin on GitHub | `setup-java` `distribution: temurin`, `java-version: 21.0.13+9` (`java-package: jre` in `e2e`) |
  | Temurin JRE tarball (GitLab `e2e`) | `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_linux_hotspot_21.0.13_9.tar.gz`, sha256 `a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd` |
  | Node | 22.11.0 (= pom `frontend.node.version`). GitLab image `node:22.11.0-bookworm@sha256:ec878c763e9fad09d22aae86e2edcb7a05b397dfe8411c16e2b90158d595e2ce` |
  | Python | 3.14.10. GitLab image `python:3.14.10-trixie@sha256:72628634e335b4b0a40727f398a73ae7ba983c319ca901fc034f92a216f7a781` (includes git and curl) |
  | actionlint | 1.7.12: `actionlint_1.7.12_linux_amd64.tar.gz` sha256 `8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8`; `actionlint_1.7.12_windows_amd64.zip` sha256 `6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9` |
  | shellcheck | v0.11.0: `shellcheck-v0.11.0.linux.x86_64.tar.xz` sha256 `8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198`; `shellcheck-v0.11.0.zip` (Windows) sha256 `8a4e35ab0b331c85d73567b12f2a444df187f483e5079ceffa6bda1faa2e740e` (both match GitHub's asset digests) |
  | check-jsonschema | 0.39.0 (`--builtin-schema vendor.github-workflows`, and `vendor.gitlab-ci` with `--data-transform gitlab-ci`) |
  | PyYAML | 6.0.3 |
  | GitHub runner | `ubuntu-24.04` |
- **C4. Artifacts.** Named in C1 and C9, with retention 7 days for the JAR and 14 days for the rest. Upload steps that
  must survive a failure use `if: always()` or `if: failure()` (GitLab: `when: always` or `on_failure`).
- **C5. Review runner (`scripts/ci/claude_review.py`).**
  - **`run --base <sha> --head <sha> --out <dir>`:**
    1. Fails with exit 2 and a fixed message when `ANTHROPIC_API_KEY` is unset. It never prints the key.
    2. Computes the diff. An empty diff writes a "no changes" report and exits 0 without calling the CLI.
    3. Checks the CLI version: `claude --version` must contain `2.1.311`, otherwise exit 2. `PF_CLAUDE_CLI` overrides
       the executable, for tests.
    4. Runs two passes (G-3), each with the prompt on stdin, using `--bare -p --output-format json --json-schema
       <C6> --model <frontmatter model> --tools Read,Grep,Glob,Bash --allowedTools Read Grep Glob "Bash(git diff *)"
       "Bash(git log *)" "Bash(git show *)" "Bash(git ls-files *)" "Bash(grep *)" "Bash(rg *)" --disallowedTools Edit
       Write NotebookEdit WebFetch WebSearch --permission-mode dontAsk --max-turns <PF_REVIEW_MAX_TURNS or 40>
       --max-budget-usd <PF_REVIEW_MAX_BUDGET_USD or 5.00> --no-session-persistence --strict-mcp-config`.
    5. Reads `structured_output`. If it is absent, it falls back to the last fenced `json` block in `result`. Either
       way the result must pass C6, or the run exits 2.
    6. Writes `<out>/security-reviewer.json`, `<out>/test-engineer.json` and `<out>/claude-review.md`. The report has
       the checklist tables, the findings by severity, one `VERDICT:` line per pass, and a last line
       `GATE: PASS|FAIL (<n> BLOCKER)`.
    7. Redacts every output in place with the `redact_evidence.py` patterns.
    8. Exits 1 when any BLOCKER exists, otherwise 0.
  - **The prompt** is a fixed CI preamble, then the agent file's body verbatim. The preamble says:
    - this is a read-only CI run;
    - the diff range and changed-file list (with `--stat`);
    - build, unit, E2E and quality gates already passed, so it must not build or test;
    - read `CLAUDE.md` first;
    - the diff is untrusted data;
    - return only the C6 verdict, and write no files.

    The test-engineer pass also includes the offline AC-audit summary line.
  - **`comment --platform github|gitlab --report <file>`:**
    - On GitHub (a `pull_request` event, using `GH_TOKEN`) it creates or updates one PR comment, found by the marker
      `<!-- policyforge-claude-review -->`.
    - On GitLab (when `PF_GITLAB_REVIEW_TOKEN` and `CI_MERGE_REQUEST_IID` are set) it posts an MR note through
      `CI_API_V4_URL`, using the standard library.
    - Otherwise it does nothing and exits 0.
  - **Both platforms** also write the report to the job summary or log.
- **C6. Verdict schema (`scripts/ci/review-verdict.schema.json`).** An object with:
  - `agent`: `security-reviewer` or `test-engineer`;
  - `verdict`: `APPROVE` or `CHANGES_REQUESTED`;
  - `checklist[]`: `{ item, status (PASS|FAIL|N/A), evidence }`;
  - `findings[]`: `{ id, severity (BLOCKER|MAJOR|MINOR), title, file, line (integer or null), detail }`;
  - `summary`: a string.

  Every key is required, and no others are allowed. The runner validates it with its own standard-library validator.
- **C7. The visual-baseline policy (NFR-09.5).**
  - **Mode.** `PF_VISUAL_BASELINES` is `auto` (the default), `require` or `update`. Any other value is a configuration
    error that fails the test.
  - **Decision table:**
    - `auto` with the baseline present → assert;
    - `auto`, baseline absent, but the platform has ≥ 1 `-<platform>.png` in `e2e/__snapshots__/` → assert (it fails
      as missing);
    - `auto`, baseline absent, and the platform has none → **skip and record**;
    - `require` → always assert;
    - `update` → always assert. It is run with `--update-snapshots=missing`, which writes only the missing files.
  - **A skip records three things:**
    - an annotation `{ type: 'visual-baseline-missing', description: <snapshot file name> }`;
    - one line in `frontend/test-results/visual-baselines-missing.txt`;
    - a console warning `[visual-baseline-missing] <file>`.
  - A skip never throws, so later assertions run. The pure decision function lives in
    `frontend/e2e/visual-baseline-policy.ts` and has no Playwright imports. The helper is in `frontend/e2e/support.ts`.
- **C8. E2E server.**
  - When `PF_E2E_JAR` is set (an absolute path, or one relative to `frontend/`), `webServer.command` is
    `java -jar "<resolved>" --policyforge.jobs.renewal-cycle.any-date=true`.
  - The URL (`/actuator/health`), `cwd`, timeout and `reuseExistingServer: !process.env.CI` are unchanged.
  - When it is unset, the existing Windows and POSIX commands are unchanged.
- **C9. Linux snapshot artifacts.**
  - The label is `update-linux-snapshots`.
  - The artifact `linux-snapshot-baselines` holds the `*-linux.png` files, flat. `linux-snapshot-report` holds the HTML
    report.
  - **Expected:** 20 files, `<spec>.ts-<name>-<project>-linux.png` for the 10 names and the 2 projects in the readiness
    check.
- **C10. The AC-08.5 oracle fixture.**
  - **File:** `backend/src/test/resources/oracle/ac-08-5-refund-cases.csv` with LF endings and the header
    `case,termStart,termEnd,cancellationDate,firstTerm,coolingOffDays,premium,fee,termDays,unexpiredDays,coolingOff,amount`.
  - **Rows:** 1,000 data rows. Money has scale 2, dates are ISO, and booleans are `true` or `false`.
  - **Generator:** `scripts/gen_refund_oracle_cases.py [--check]` with seed `20261104`.
  - **Boundary rows come first**:
    - the term start and last day;
    - cooling-off at −1, 0 and +1 days for a first term, and the same dates for a renewal term;
    - a fee above the pro-rata amount;
    - fee 0.00;
    - premiums 0.00, 0.01 and 9999999.99;
    - 1-month, 24-month and 366-day leap terms.

    Random rows fill the rest.
  - **The expected values** come from the oracle's `refund()`:
    - `effective` = `termStart`, and `cooling_off` = `coolingOffDays` for a first term, or 0 for a renewal term;
    - `coolingOff` = `firstTerm && days(termStart, cancellationDate) < coolingOffDays`;
    - `termDays` and `unexpiredDays` per the spec.
  - `--check` compares with LF normalised. On a difference it exits 1 and prints the first differing row.
- **C11. Quality gates (`scripts/ci/quality_gates.py`).** The gates run in this order, each as a subprocess with the
  current interpreter. All of them run, a summary table follows, and the exit code is 1 if any gate failed:
  1. `ac-audit`: `scripts/ac_audit.py --offline --no-write`
  2. `redact-evidence`: `scripts/redact_evidence.py --check sprint-contracts specs/reviews docs/fix-loops`
  3. `hook-tests`: `.claude/hooks/tests/test_hooks.py`
  4. `hook-line-endings`: `scripts/ci/check_line_endings.py '.claude/hooks/*.sh'`
  5. `ci-script-tests`: `-m unittest discover -s scripts/tests -v`
  6. `oracle-fixture`: `scripts/gen_refund_oracle_cases.py --check`
  7. `actionlint`: the fetched actionlint 1.7.12 with the fetched shellcheck v0.11.0 on `.github/workflows/`
  8. `ci-schema`: `check-jsonschema --builtin-schema vendor.github-workflows .github/workflows/*.yml` and
     `--builtin-schema vendor.gitlab-ci --data-transform gitlab-ci .gitlab-ci.yml`

  `--list` prints the gates, and `--only <name>` runs one. A missing tool is a failure, never a skip.
- **C12. Secrets and variables.**
  - **GitHub:** the secret `ANTHROPIC_API_KEY`, and the optional variable `PF_REVIEW_MAX_BUDGET_USD`.
  - **GitLab:** `ANTHROPIC_API_KEY` (masked, protected), and the optional `PF_GITLAB_REVIEW_TOKEN` (masked, protected)
    and `PF_REVIEW_MAX_BUDGET_USD`.
  - No other secret is read anywhere.

**Execution notes for every generator.**
- **Maven.** Run `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export
  PATH="$JAVA_HOME/bin:$PATH"` first. Selective ITs run under failsafe (KD-009): `./mvnw verify -Dskip.frontend=true
  -Dit.test=<Pattern> -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`.
- **KD-014.** Run every long command in the **foreground**, with a timeout of at most 600000 ms: Maven, `npm test`,
  Playwright and its `webServer`, tool downloads. Split anything longer. Never end the turn while a command is pending.
- **Commits.**
  - Commit only on `chore/ci-hardening`, in P-3 order.
  - Stage explicit paths and use `git commit -m "…" -- <paths>`.
  - Never push, never commit on `dev` or `main`, and never commit substrate (except the TASK-001 fallback).
  - Save `sprint-progress/<TASK>-red.log` and `<TASK>-green.log`, redacted with
    `python scripts/redact_evidence.py <file>` before saving.
- **Data.**
  - Synthetic only: KYC "Asha Test", `XXXX-XXXX-1234`, `ABCDE1234F`, postcode 411001.
  - Fake keys are built by concatenation and are obviously fake.
  - Log IDs only.
  - Money stays a decimal string or `BigDecimal`, and every new money value must equal `premium_check.py`.
- **Environment.** Windows host with Git Bash. Use Python 3.14 locally, with a `.venv/` for `requirements-ci.txt`.
  Download tools only through `fetch_tools.py`. Never set `NODE_TLS_REJECT_UNAUTHORIZED` (KD-015).
- **Port.** Port 8080 must be free when a task ends. Stop any server you started.
- **Agents and skills:**

  | Task | Agents and skills |
  |---|---|
  | TASK-001 | `archtest-author` skill or agent (NFR-08.1 and its fixture); `test-engineer`; `renewal-orchestrator-agent` and `endorsement-agent` (race scenario); `premium-calc-evaluator` skill (oracle) |
  | TASK-002 | `test-engineer` (red); `security-reviewer`, read-only (runner secret handling) |
  | TASK-003 | `test-engineer` (Vitest and E2E) |
  | TASK-004 | `security-reviewer`, read-only (permissions and secret exposure in the CI files) |
  | TASK-005 | `test-engineer` (final audit) |

**POST-PUSH orchestrator steps.** These need the branch on GitHub. No generator criterion depends on them. They run
after the evaluator PASS, amendment C, the `security-reviewer` and `test-engineer` review gate, and `/sprint-archive`.
1. **The secret.** The user confirms the repository secret `ANTHROPIC_API_KEY` exists (`gh secret list` shows its name
   only). Without it, `claude-review` fails closed.
2. **Push and open the PR.** `/pr` pushes `chore/ci-hardening` and opens the PR into `dev`. This is the **first real
   GitHub Actions run** (`pull_request`). Expected:
   - `build`, `frontend-unit` and `quality-gates` are green;
   - `e2e` is green with 20 `visual-baseline-missing` entries;
   - `claude-review` is green, with the first live review using the user's secret.
3. **Follow the run.** Use `gh pr checks <n> --watch`, and `gh run view <run-id> --log-failed` on a failure.
4. **Fix loop.** A failure caused by the Linux environment (time zone, paths, permissions, image) gets a local fix
   commit by a fix agent, under the usual TDD rules, and another push. Record it in `docs/fix-loops/` and
   `docs/post-mortems/` (environment first).
5. **The label.** `gh label create update-linux-snapshots --description "Generate -linux Playwright baselines in CI"
   --repo wushandarshana/Policy-Forge`, then `gh pr edit <n> --add-label update-linux-snapshots`. This starts the
   **first `Linux snapshots` run**.
6. **Download.** `gh run download <run-id> --name linux-snapshot-baselines --dir frontend/e2e/__snapshots__`. Confirm
   exactly the 20 C9 file names and no other new files. View every image: masked dates and numbers, no clipped or blank
   panels.
7. **Commit the baselines** with explicit paths, as `test: Linux visual baselines generated in CI run <run-id>
   (mcr.microsoft.com/playwright:v1.64.1-noble)`, then push. Remove the label with `gh pr edit <n> --remove-label
   update-linux-snapshots`.
8. **The second CI run.** `e2e` must now assert the Linux visuals: 0 `visual-baseline-missing` entries, both projects
   green. `claude-review` reviews the baseline commit.
9. **GitLab.** If the user mirrors to GitLab, the user configures it (C12, amendment C). The first GitLab pipeline is
   theirs (SG-11).
10. **Required checks (G-8).** After a fully green run, the user approves, and the orchestrator runs, a `gh api -X PUT
    repos/wushandarshana/Policy-Forge/rulesets/26032911` call. It keeps the existing three rules and adds
    `required_status_checks` with contexts `build`, `frontend-unit`, `e2e`, `quality-gates` and `claude-review`. Check
    the result with `gh api repos/wushandarshana/Policy-Forge/rulesets/26032911`.
11. **Lessons.** Commit amendment D, the KD rows drafted by TASK-005 plus lessons from steps 2–8, and push.
12. **Merge.** The user merges the PR with "Create a merge commit". The `push` to `dev` then runs CI again, with a
    push-mode review of the merge commit (P-7).
13. **Release.** A later `/release` puts the workflows on `main`. Only then can `workflow_dispatch` (C2) be used.

**Out-of-orchestrator scope. The user does these manually:**
1. Add `ANTHROPIC_API_KEY` as a GitHub repository secret, and optionally `PF_REVIEW_MAX_BUDGET_USD`.
2. Approve the ruleset change of G-8.
3. Merge the PRs on GitHub.
4. Create or mirror the GitLab project, add the masked and protected variables, protect `feat/*`, `fix/*` and
   `chore/*`, and enable "Pipelines must succeed".
5. Optionally apply O-1.
6. Watch API spend on the Console (G-3).

## Tasks

### TASK-001: Backend hardening: the repriceOffer caller ArchUnit rule (NFR-08.1), the re-price versus pay race IT (AC-07.7) and the AC-08.5 premium_check.py oracle cross-check
> Effort override: high → xhigh. Deterministic two-thread race tests and exact money cross-checks across 1,000 cases (P-13).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: []
- **acceptance_criteria**:
  - Confirms, before its first commit, that amendment A (`spec:`) and amendment B (`chore(substrate):`) exist after `d9a5694`. If either is missing, makes it first with exactly the plan's text adjusted for any checkpoint veto, touching only the files that amendment names. No other task commits substrate.
  - Adds RepriceOfferCallerArchTest (`@Tag("NFR-08")`, production classes only) with a rule factory parameterised by owner class and allowed caller, whose `because` names NFR-08.1. The rule passes on the production code, where EndorsementService is the only caller.
  - Proves "NFR-08.1 negative fixture: …" with fixture classes under `com.horizon.archfixtures.reprice`. The rule rejects a non-endorsement class that calls the fixture's `repriceOffer`, and the message names it. It does not reject the fixture's endorsement service.
  - Shows NFR-08.1 red then green (P-3): a `test(red)` commit with the rule stubbed to allow every caller, so the negative-fixture test fails on its assertion, then a `feat(green)` commit with the real rule.
  - Adds a re-price versus pay race IT that holds one named thread at the policy `saveAndFlush`, in test configuration only, as RenewalRaceIT does. Production code has no hook. The setup is the AC-04.1 TERM_LIFE policy (6850.00, sum insured 5,000,000, term 2026-11-01..2027-10-31), offered on 2027-10-01. Both requests run on 2027-10-05: the payment, and CHANGE_SUM_INSURED 7,000,000 effective 2027-10-05.
  - Proves "AC-07.7: …" when the payment wins and the endorsement is held. The endorsement gets 409 `POLICY_UPDATE_CONFLICT` with a fixed detail, and writes no endorsement row. Term 2 is paid at 6850.00, with its dates and rule version unchanged. The status is RENEWED, and version 2 has premium 6850.00 and sum insured 5000000.00. There is no ENDORSED transition.
  - Proves "AC-07.7: …" when the endorsement wins and the payment is held. The payment gets 409 `POLICY_UPDATE_CONFLICT` and writes nothing. Term 2 is unpaid at 9490.00, with its dates and rule version unchanged. The status is ENDORSED, version 2 has sum insured 7000000.00 and premium 9490.00, and the endorsement adjustment is 195.29. There is no RENEWAL_PAID transition. A fresh payment on 2027-10-06 then gives 200, and version 3 is RENEWED at 9490.00, equal to term 2's premium.
  - Asserts in both race tests that exactly one of the two requests succeeds, and that every paid term's premium equals the premium of the version its payment appended. No paid term ever has a re-priced premium.
  - Adds `scripts/gen_refund_oracle_cases.py` (standard library only). It imports `refund()` read-only from `.claude/skills/premium-calc-evaluator/premium_check.py` and writes the C10 CSV: boundary rows first, then seeded random rows, 1,000 rows in total. `--check` exits 0 on the committed file and 1 with the first differing row after a one-row edit, which is reverted afterwards.
  - Adds an AC-08.5 cross-check test titled "AC-08.5: the production calculator equals the premium_check.py oracle on 1,000 generated cases". For each CSV row it asserts that ProRataRefundCalculator gives an amount string equal to the row's (scale 2), the same termDays, unexpiredDays and coolingOff, and fee 0.00 when cooling off or the row's fee otherwise. It also asserts the row count, and at least one cooling-off row, one renewal-term row and one floor-to-0.00 row. No expected value comes from production code, and the existing invariant test stays.
  - Shows the characterisation evidence of P-3 for AC-07.7 and AC-08.5. Commits them as `test: AC-NN.M …` when they pass against unchanged production code, and the red log holds each mutation proof: a temporary fault that makes the new test fail on a named assertion, then the restored pass and a clean `git status`. If a real defect is found, uses `test(red)` and `fix(green)` instead, and records it in the progress file.
  - Cross-checks three CSV rows (one cooling-off, one renewal-term, one floor-to-zero) with `python .claude/skills/premium-calc-evaluator/premium_check.py refund …`, and records `spec-examples` (all PASS) in the green log.
  - Changes nothing under `backend/src/main/` unless a recorded defect requires it. Adds no Flyway migration, edits no published rule file, and touches only `backend/src/test/**` and `scripts/gen_refund_oracle_cases.py`.
  - Saves `sprint-progress/TASK-001-red.log` and `TASK-001-green.log` naming NFR-08.1, AC-07.7 and AC-08.5.
  - Passes `./mvnw verify -Dskip.frontend=true` with ArchUnit green and the JaCoCo thresholds unchanged at 80% domain and 70% overall.

### TASK-002: CI scripts: the Claude review runner and gate, the quality-gates runner, the pinned tool fetcher, the LF check, the API-key redaction, the AC audit's Python test glob and their unittest suite
> Effort override: high → xhigh. Secret handling, a fail-closed required gate and checksum-verified downloads (P-13).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: []
- **acceptance_criteria**:
  - Touches only `scripts/**` (but not `scripts/gen_refund_oracle_cases.py`, which TASK-001 owns) plus two new `.gitignore` lines, `.ci-tools/` and `ci-out/`. Never commits substrate.
  - Adds `scripts/requirements-ci.txt` pinning exactly `PyYAML==6.0.3` and `check-jsonschema==0.39.0`, and installs it into the gitignored `.venv/`.
  - Adds `scripts/ci/fetch_tools.py` with a manifest of the C3 actionlint 1.7.12 and shellcheck v0.11.0 URLs and SHA-256 values for linux-x86_64 and windows-x86_64. It downloads to `.ci-tools/` with TLS verification on, verifies the checksum before extracting, deletes the download and exits non-zero on a mismatch, reuses a verified copy, and prints the tool path.
  - Adds `scripts/ci/check_line_endings.py`. It fails, listing each file, when a matched file has a CR byte in the working tree, or when `git ls-files --eol` reports `i/crlf` or `i/mixed`, and passes otherwise.
  - Adds `scripts/ci/quality_gates.py` implementing C11. It runs all eight gates in order with the current interpreter, never stops early, prints a summary table of gate, command, exit code and result, supports `--list` and `--only`, treats a missing tool as a failure, and exits 1 if any gate failed.
  - Adds the `sk-ant-[A-Za-z0-9_-]{20,}` pattern (→ `<redacted-api-key>`) to `scripts/redact_evidence.py`. Keeps its existing behaviour, and `--check sprint-contracts specs/reviews docs/fix-loops` still exits 0.
  - Adds `scripts/tests/test_*.py` to the TEST_GLOBS of `scripts/ac_audit.py` and changes nothing else in it.
  - Adds `scripts/ci/review-verdict.schema.json` (C6) and `scripts/ci/claude_review.py` implementing C5 (`run` and `comment`). It reads both agent files and their frontmatter `model`, builds the preamble and passes each agent body verbatim on stdin, sends the exact C5 flag set, removes the token variables of P-6 from the CLI's environment, and validates against C6 with a standard-library validator.
  - Proves "NFR-09.4: …" with `unittest` tests that use a stub CLI (`scripts/tests/fixtures/fake_claude.py` through `PF_CLAUDE_CLI`). Two APPROVE passes exit 0. One BLOCKER exits 1. MAJOR and MINOR findings alone exit 0, and every finding appears in `claude-review.md`. CHANGES_REQUESTED with no BLOCKER exits 0 with a warning. Each of these exits non-zero: no `structured_output` and no fenced JSON, schema-invalid output, a CLI exit of 1, a version other than 2.1.311, and a missing key (the CLI is never called). An empty diff exits 0 without calling the CLI.
  - Proves "NFR-09.4: …" that each pass's stdin contains the agent file's body byte for byte, and that the CLI argv contains `--bare`, `--json-schema`, `--permission-mode dontAsk`, the disallowed tools, the frontmatter model and both caps, and never `--dangerously-skip-permissions`. Proves the P-7 diff ranges, including the all-zeros and non-ancestor fallbacks to the first parent.
  - Proves "NFR-09.3: …": the CLI child environment lacks `GITHUB_TOKEN`, `GH_TOKEN`, `CI_JOB_TOKEN` and `PF_GITLAB_REVIEW_TOKEN`. A fake key never appears in stdout, stderr or any output file. A key-shaped string that the stub CLI echoes is redacted in the outputs. `redact_evidence.py` redacts the key pattern and `--check` reports it. Every fake key is built by concatenation.
  - Proves "NFR-09.6: …": with injected fake gates, all passing exits 0, and a single failure exits 1 after every later gate has still run. A missing tool fails. A CRLF hook file in a temporary git repository fails `check_line_endings.py`, and an LF one passes. The gate list and order equal C11.
  - Proves "NFR-09.4: …" that every `scripts/ci/*.py` file parses with `ast.parse(feature_version=(3, 11))` and imports only the standard library.
  - Starts every test docstring with its ID (P-4). `python -m unittest discover -s scripts/tests -v` passes.
  - Records in the progress file the output of `claude --version` and `--help` for the installed CLI, and for 2.1.311 if it can be fetched without sending a prompt (P-15). Lists any C5 flag that is missing and how the runner handles it.
  - Keeps `python .claude/hooks/tests/test_hooks.py` passing.
  - Updates `scripts/README.md` (not substrate) for the CI scripts, and removes its stale `--report-only` and "full audit in claude-review" text (P-10).
  - Shows a `test(red)` commit with the failing tests before the `feat(green)` commit. Saves `sprint-progress/TASK-002-red.log` and `TASK-002-green.log` naming NFR-09.3, NFR-09.4 and NFR-09.6.

### TASK-003: Frontend and E2E: untrack lcov, the exact Playwright pin, the visual-baseline policy and helper (NFR-09.5), the packaged-JAR server option, and local Playwright runs
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-001]
- **acceptance_criteria**:
  - Touches only `frontend/**` and the lcov lines of `.gitignore`. Never commits substrate.
  - Untracks lcov in a `chore:` commit: `git rm --cached frontend/coverage/lcov.info`, and the `.gitignore` exception lines replaced by `frontend/coverage/`. Afterwards `git ls-files frontend/coverage` is empty, and `npm test` leaves `git status` clean.
  - Pins `"@playwright/test": "1.64.1"` exactly in `package.json` (P-14). The lock still resolves 1.64.1, and the lock diff changes only the root dependency specifier.
  - Adds the pure C7 decision function in `frontend/e2e/visual-baseline-policy.ts`, with no Playwright imports. Proves "NFR-09.5: …" with a Vitest table test covering every C7 row, including a configuration error for an unknown mode.
  - Adds the helper in `frontend/e2e/support.ts`. It resolves the snapshot path from the test info, decides with the policy, and on a skip records the C7 annotation, file line and warning without throwing.
  - Replaces all 10 `toHaveScreenshot` calls with the helper, keeping the same names and options (`stylePath`, `animations`). Afterwards `grep toHaveScreenshot frontend/e2e/*.spec.ts` finds nothing, and only `support.ts` calls it.
  - Adds the C8 `PF_E2E_JAR` option to `playwright.config.ts`, leaving behaviour unchanged when the variable is unset, and a comment on the C7 update mode.
  - Passes `npm test` (`noMoneyArithmetic` included) and `npm run build` with no TypeScript errors.
  - Runs Playwright on both projects with the default `webServer`: everything passes against the `-win32` baselines, and `visual-baselines-missing.txt` is absent or empty.
  - Builds the packaged JAR with `./mvnw -B -DskipTests package` (SPA packaged). Runs both projects twice with `PF_E2E_JAR`: everything passes, no PNG is created or changed, and both runs are recorded.
  - Demonstrates both C7 `auto` paths with temporary moves that are never committed and are restored afterwards, with `git status` clean:
    - All 20 `-win32` baselines moved out, so win32 has none → `workbench.spec.ts` skips its visual assertion and lists it in `visual-baselines-missing.txt`, every functional assertion runs, and the test passes.
    - Only the two `workbench-case-detail` baselines moved out while the other 18 remain → that test fails as a missing baseline.
  - Saves `sprint-progress/TASK-003-red.log` (the failing NFR-09.5 Vitest) and `TASK-003-green.log` (Vitest, build, the three Playwright runs, both demonstrations), naming NFR-09.5. Shows a `test(red)` commit before the `feat(green)` commit.
  - Leaves port 8080 free and no Java process running at the end.

### TASK-004: CI definitions: GitHub `ci.yml` and `linux-snapshots.yml`, `.gitlab-ci.yml`, the executable `mvnw`, the config tests (NFR-09.1–09.3) and a local lint plus quality-gates run
> Effort override: high → xhigh. The token permission model, supply-chain pinning and API-key exposure in public CI logs (P-13).
- **type**: code
- **model**: sonnet
- **effort**: xhigh
- **depends_on**: [TASK-001, TASK-002, TASK-003]
- **acceptance_criteria**:
  - Commits `scripts/tests/test_ci_config.py` first, as `test(red)`. It fails because the CI files are absent. Its tests are titled per P-4 for NFR-09.1, NFR-09.2 and NFR-09.3, plus an NFR-09.6 test checking that both platforms run `scripts/ci/quality_gates.py`.
  - Adds `.github/workflows/ci.yml` (name `CI`) per C1, C2, C4, P-6, P-10, P-11 and P-12:
    - five jobs whose `name:` equals their ID, on `ubuntu-24.04`, with timeouts;
    - `e2e` in the C3 Playwright container with `--ipc=host`, a Temurin JRE and `PF_E2E_JAR`;
    - `claude-review` needing the other four, with job permissions `contents: read` and `pull-requests: write`, the key only in the runner step's `env`, and a separate comment step for `pull_request` that has `GH_TOKEN` only;
    - every artifact of C4.
  - Adds `.github/workflows/linux-snapshots.yml` (name `Linux snapshots`) per G-1, C2 and C9: no secrets, `contents: read`, a packaged-JAR build with tests skipped, the same container and server as `e2e`, `PF_VISUAL_BASELINES=update` with `--update-snapshots=missing`, and uploads of exactly `frontend/e2e/__snapshots__/*-linux.png` as `linux-snapshot-baselines` plus the HTML report.
  - Adds `.gitlab-ci.yml` with the C2 workflow rules and stages `build`, `test`, `e2e` and `review`. It has the same five jobs with the same commands, images pinned per C3, and the P-12 caches and C4 artifacts. `claude-review` needs four jobs with `allow_failure: false`. The `e2e` job installs the C3 JRE tarball after a `sha256sum -c` check. The manual `e2e-update-linux-snapshots` job has `allow_failure: true`. The file uses no `!reference` tags.
  - Sets `backend/mvnw` to mode 100755 with `git update-index --chmod=+x`, in a `ci:` commit.
  - Proves "NFR-09.1: …":
    - both files define the five jobs and the same C1 commands;
    - the C2 triggers are present;
    - `claude-review` needs the other four and can't fail (GitHub has no `continue-on-error`; GitLab has `allow_failure: false`);
    - `git ls-files -s backend/mvnw` shows 100755.
  - Proves "NFR-09.2: …":
    - every `uses:` is `owner/repo@<40 hex>` with a version comment;
    - every image is `tag@sha256:<64 hex>`;
    - the Playwright image tag equals `v` + the locked `@playwright/test` version, and `package.json` has no range for it;
    - the CI Node version equals the pom's `frontend.node.version`;
    - the CLI is `@anthropic-ai/claude-code@2.1.311`, and `.mcp.json` pins `@playwright/mcp@0.0.84`;
    - the strings `@latest`, `:latest` and `ubuntu-latest` occur nowhere;
    - every downloaded file has a SHA-256 next to it.
  - Proves "NFR-09.3: …":
    - top-level `permissions: contents: read`, and `pull-requests: write` only on `claude-review`;
    - no `pull_request_target`;
    - `persist-credentials: false` on every checkout;
    - no `${{ github.event` inside any `run:`;
    - `ANTHROPIC_API_KEY` is referenced only in the review step's `env` (GitHub) or the review job (GitLab);
    - no `set -x`, `echo $ANTHROPIC_API_KEY` or `NODE_TLS_REJECT_UNAUTHORIZED` in any CI file;
    - `git grep` finds no `sk-ant-` literal of 20 or more characters in tracked files.
  - Makes the config tests green in `ci:` commits whose messages name NFR-09.1, NFR-09.2 and NFR-09.3.
  - Runs actionlint 1.7.12 with shellcheck v0.11.0 (fetched through `fetch_tools.py`) on `.github/workflows/` with 0 findings. Runs check-jsonschema 0.39.0 with `vendor.github-workflows` on both workflows and with `vendor.gitlab-ci --data-transform gitlab-ci` on `.gitlab-ci.yml`, with no errors. Records the outputs.
  - Runs `python scripts/ci/quality_gates.py` locally with every one of the eight gates passing, the AC audit at 92/92 and 0 parent-only. Records the summary table.
  - Has the `security-reviewer` agent review the three CI files read-only, and resolves or records every finding in the progress file.
  - Never pushes, never runs a `gh` command that writes, and creates no label, secret or ruleset (P-16).
  - Saves `sprint-progress/TASK-004-red.log` and `TASK-004-green.log` naming NFR-09.1, NFR-09.2, NFR-09.3 and NFR-09.6.

### TASK-005: Sprint-wide verification, the POST-PUSH runbook and the knowledge-deposit drafts
- **type**: code
- **model**: sonnet
- **effort**: high
- **depends_on**: [TASK-004]
- **acceptance_criteria**:
  - Passes a full `./mvnw verify` with the SPA packaged and the frontend not skipped. ArchUnit is green, and the JaCoCo thresholds in `backend/pom.xml` are unchanged from `d9a5694` (80% domain, 70% overall).
  - Passes `npm test` and `npm run build`, and confirms that `git status` is clean afterwards (lcov ignored) and that `git ls-files frontend/coverage` is empty.
  - Passes Playwright on `desktop-chromium` and `mobile-chromium` with `PF_E2E_JAR` against a freshly packaged JAR, with no created or changed PNG and no `visual-baseline-missing` entry on win32.
  - Passes `python scripts/ci/quality_gates.py` (all eight gates) and `python -m unittest discover -s scripts/tests -v`.
  - Reports COVERED, not PARENT-ONLY or MISSING, in `python scripts/ac_audit.py --offline --no-write` for NFR-08.1, NFR-09.1–09.6, AC-07.7 and AC-08.5, and 92/92 overall.
  - Confirms from `git log d9a5694..chore/ci-hardening` that amendment A and amendment B come before every code commit, and that each of NFR-08.1 and NFR-09.1–09.6 has a `test(red)` commit naming it before a `feat(green)`, `fix(green)` or `ci:` commit naming it. Also confirms that AC-07.7 and AC-08.5 have `test:` commits with mutation proofs in the TASK-001 red log, or recorded red and green commits.
  - Confirms that `git diff --stat d9a5694 -- backend/src/main/resources/db/migration backend/src/main/resources/rules` is empty, and lists every `backend/src/main` change with its justification (expected: none).
  - Confirms that every pin in the three CI files and `.mcp.json` equals C3 exactly, and records the table.
  - Confirms that `dev` is `d9a5694` and `main` is `8e265d6`, locally and in `git ls-remote origin`. Confirms that `origin/chore/ci-hardening` doesn't exist, and that GitHub shows no new label, secret or ruleset change (read-only `gh` calls).
  - Confirms that `python scripts/redact_evidence.py --check` exits 0 over `sprint-progress/` and `ci-out/` (if present), and that `git grep` finds no `sk-ant-` literal of 20 or more characters.
  - Writes into the progress file the POST-PUSH runbook (steps 1–13 above), with every placeholder that can be known locally filled in, and the expected 20 Linux baseline file names.
  - Writes into the progress file drafts of `docs/knowledge-deposits.md` rows from KD-016 onwards, in the file's table format, for the orchestrator to commit as amendment D:
    - the claude-code-action `push` limitation;
    - `workflow_dispatch` needing the default branch;
    - `mvnw` mode 100644;
    - Playwright's missing-snapshot modes;
    - GitLab protected variables on MR pipelines;
    - anything else learned in TASK-001 to TASK-004.
  - Records in the progress file every command run and its result, the spec gaps SG-8 to SG-13, the checkpoint outcome of G-1 to G-8, and that port 8080 is free.
  - Saves `sprint-progress/TASK-005-green.log` with the verify, npm, Playwright, quality-gate, audit and git-check outputs.

## Execution Schedule

### Parallel Batch (no dependencies — run simultaneously via Agent Teams)
- TASK-001
- TASK-002

### Sequential Tasks (ordered by dependency)
1. TASK-003 (depends on TASK-001)
2. TASK-004 (depends on TASK-001, TASK-002, TASK-003)
3. TASK-005 (depends on TASK-004)
