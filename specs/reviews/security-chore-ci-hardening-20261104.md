# Security review: chore/ci-hardening (Sprint 5, CI), 2026-11-04

Reviewer: security-reviewer agent (review only, no code changes). The orchestrator saved this file from the agent's
returned report, because the agent's rules forbid it writing report files.

Scope: `git diff d9a5694..HEAD` (dev base, 23 commits, 53 files). The spec commit came first (`4730168`, NFR-08.1,
NFR-09.1–09.6). Files reviewed:
- `.github/workflows/ci.yml`, `.github/workflows/linux-snapshots.yml` and `.gitlab-ci.yml`;
- `scripts/ci/*`, `scripts/tests/*`, and the `sk-ant-` pattern in `scripts/redact_evidence.py`;
- RepriceOfferCallerArchTest and its fixtures, RenewalRepriceRaceIT, ProRataRefundOracleCrossCheckTest and the AC-08.5
  CSV;
- the E2E `expectVisual` policy and `PF_E2E_JAR`;
- the lcov untracking and `.mcp.json` pinned to `@playwright/mcp@0.0.84`.

Evidence:
- `python -m unittest discover -s scripts/tests` with the `.venv` Python: 138 run, OK. With the system Python, 22 fail
  only because PyYAML is not installed.
- `quality_gates.py` (venv): 8/8 gates pass.
- A scratch repository confirmed that `git log -1 --format='%s' --output=<file>` writes text the caller chooses to any
  path (the basis of BLOCKER-1).
- No Maven run, and port 8080 was not used.

## Checklist

| # | Item | Result | Evidence |
|---|---|---|---|
| 1–3 | Auth boundary, ownership, audit | N/A | No controller, `SecurityConfig` or `backend/src/main` change. The race IT acts only as the synthetic `customer1`. |
| 4 | PII in logs (NFR-03) | PASS | No new log calls. Review output is redacted (claude_review.py:76-88, 499-512). |
| 5 | SQL injection | PASS | The IT uses bound parameters (RenewalRepriceRaceIT.java:145). |
| 5a | Script injection (`${{ github.event.* }}` in `run:`) | PASS | Event values reach scripts only through step `env` (ci.yml:236-243). The label name appears only in `if:`. |
| 6 | Append-only (NFR-02) | PASS | No migration, rule file or setter changed. |
| 7 | Secrets and data | PASS | No key literal. Data is synthetic. Money is built from integer cents. |
| 8 | Error leakage | N/A | CLI diagnostics in the report are redacted and capped at 2000 characters (claude_review.py:485-492). |
| C1 | Pins: actions to SHAs, images to digests, downloads to checksums | PASS (MINOR-2) | **Actions:** every `uses:` is pinned to a 40-character SHA. **Images:** tag plus `@sha256`. **Downloads:** the JRE is checked with `sha256sum -c`. fetch_tools.py requires https, checks the hash before extracting and extracts one member. |
| C2 | `permissions`, `pull_request_target`, `persist-credentials` | PASS | **Default:** `contents: read`. **Write:** only claude-review has `pull-requests: write`. **Triggers:** no `pull_request_target` or `workflow_run`. **Checkout:** every checkout sets `persist-credentials: false`. |
| C3 | Key scope; key never printed | PARTIAL | **GitHub:** the key is in one step's `env`. **GitLab:** it is environment-scoped. **Echo:** no echo and no `set -x`. **Gaps:** MAJOR-1, BLOCKER-1 and MAJOR-2. |
| C4 | Child environment drops platform tokens | FAIL (GitLab) | **Denylist:** `TOKEN_VARS` (claude_review.py:48) drops four names only. **Still reaching the CLI:** `CI_REPOSITORY_URL` (contains the job token), `CI_REGISTRY_PASSWORD` and `CI_DEPENDENCY_PROXY_PASSWORD`. See MAJOR-2. |
| C5 | CLI is read-only | FAIL | **Flags:** `--bare`, the tool list, the disallowed tools, `--permission-mode dontAsk` and `--strict-mcp-config` are correct, and the run does not skip permissions (claude_review.py:419-422). **Gap:** the allowed `Bash(git log/show/diff *)` accept `--output=<path>`, which writes files. See BLOCKER-1. |
| C6 | Prompt-injection resistance | PARTIAL | **Preamble:** marks the diff as untrusted (:348-349). **Verdict:** must pass the schema. See the residual risk section. |
| C7 | Fail-closed BLOCKER gate | PASS (MINOR-4) | **Exit codes:** exit 2 for no key, wrong version, CLI error, invalid or missing verdict, or timeout; exit 1 for any BLOCKER (:551-601). **Stale output:** old verdicts are deleted first. **GitLab:** keeps the status and sets `allow_failure: false`. |
| C8 | `linux-snapshots` label trigger | PASS | **Trigger:** `pull_request` `labeled`, and only users with triage access or higher can add labels. **Fork code:** runs with no secrets and a read-only token. **Artifacts:** only `*-linux.png` files and the report, reviewed by a person before commit. **Cache:** PR-scoped. |
| C9 | Fork PRs | PASS (fails closed) | No secrets, so the review exits 2. See MINOR-5. |
| C10 | GitLab environment scope and protected branches | PARTIAL | See MAJOR-1 and MAJOR-3. |
| C11 | No `NODE_TLS_REJECT_UNAUTHORIZED` | PASS | It is asserted by a test. `ssl.create_default_context()` is used throughout. |
| C12 | Comment path: redirects and token scope | PASS (MINOR-1, MINOR-3) | **Redirects:** `_NoRedirect` makes a 3xx raise, so the Authorization header is never resent (:606-619). **Token:** limited to `pull-requests: write` on one step. |
| C13 | Non-negotiables in the non-CI code | PASS | **Test names:** carry the full sub-criterion. **Data and money:** synthetic data, `BigDecimal`. **Migrations:** none edited. **Pins and coverage:** `.mcp.json` is pinned, and lcov is ignored. |

## Findings

### BLOCKER-1: the "read-only" reviewer can write files, and a later step that holds a token runs them
Where: `scripts/ci/claude_review.py:50-51` (`Bash(git diff *)`, `Bash(git log *)`, `Bash(git show *)`). The writes
then run in later steps:
- ci.yml:262-266: the comment step runs `scripts/ci/claude_review.py` from the same workspace with `GH_TOKEN`.
- claude_review.py:69-73: `Redactor` runs `scripts/redact_evidence.py` through `exec_module`.
- .gitlab-ci.yml:201-202: the comment runs in the review job with `PF_GITLAB_REVIEW_TOKEN`, `CI_JOB_TOKEN` and the key.

The permission rules match by prefix, and `git log`, `git show` and `git diff` accept `--output=<file>`.

Scenario:
1. Text in a reviewed diff carries a prompt injection that tells the reviewer to run
   `git log -1 --format='format:<python payload>' --output=scripts/redact_evidence.py`.
2. `dontAsk` allows it.
3. The comment step then runs the payload with `GH_TOKEN`. On GitLab it runs with the key and the review token.
4. The payload can also rewrite the report before it is posted.

Nothing checks that the working tree is unchanged after the CLI runs.

Caveat: this was not confirmed against the real CLI's rule matcher, because the tests use a stub. The design should
not rely on the matcher.

Fix options:
- Remove the git Bash rules, and give the reviewer the diff and log as a file or on stdin.
- After each pass, check that the tree is unchanged, and fail closed.
- Run the comment from a separate job or step that uses pristine scripts.
- On GitLab, split `comment` into its own job that takes only the report artifact.

### MAJOR-1: the code under review controls the gate and the key (insider and same-repo PR threat)
Where:
- ci.yml:235-261: `pull_request` runs the PR's own workflow and scripts with the secret.
- claude_review.py:249-258: runs the PR's `ac_audit.py` with the key in its environment.
- claude_review.py:269-286: the reviewer's instructions come from the PR's `.claude/agents/*.md`.
- .gitlab-ci.yml:6-8 and :178.

Scenarios:
- A same-repo PR edits the scripts or the workflow to send the key out, or edits `security-reviewer.md` to "always
  APPROVE".
- On GitLab, an MR can add `environment: claude-review` to `build`.
- Protecting `feat/*`, `fix/*` and `chore/*` gives every developer's MR the protected key.

The review is advisory for any author with write access. Human PR review is the real control.

Fix:
- Use a GitHub Environment `claude-review` with protection rules, and a GitLab protected environment with deployment
  approval instead of protected feature branches.
- Add CODEOWNERS with required code-owner review for `.github/**`, `.gitlab-ci.yml`, `scripts/ci/**`,
  `scripts/redact_evidence.py`, `scripts/ac_audit.py` and `.claude/agents/**`.
- Record any accepted residual risk as an SG item.

### MAJOR-2: secrets are readable through the reviewer's tools, and the GitLab token denylist is incomplete
Where: claude_review.py:48, 148-154; .gitlab-ci.yml:183.

The `Read` tool has no path limit, and `git diff --no-index` reads any file, so `/proc/self/environ` and
`/proc/$PPID/environ` are readable. Through them, these are exposed:
- the key;
- on GitLab, `CI_REPOSITORY_URL`, `CI_REGISTRY_PASSWORD` and `CI_DEPENDENCY_PROXY_PASSWORD`;
- through the parent process, `PF_GITLAB_REVIEW_TOKEN` and `CI_JOB_TOKEN`.

Scenario: an injection makes the model put the key, encoded (spaced, reversed or base64), into `finding.detail`. The
redactor matches only literals and `sk-ant-…`, so the encoded key is posted in the comment and the 14-day artifact.

The docstring "The CLI never receives a GitHub or GitLab token" (:19) is false on GitLab.

Fix:
- Build the child environment from an allowlist (`PATH`, `HOME`, `LANG`, `ANTHROPIC_API_KEY`, `CLAUDE_*`, `TMPDIR`).
- Deny `Read(//proc/**)` and every path outside the repository.
- Split the GitLab comment job.
- Add a test that a token-bearing `CI_REPOSITORY_URL` never reaches the child.

### MAJOR-3: `PF_GITLAB_REVIEW_TOKEN` is project-wide and reaches the jobs that run MR code
Where: .gitlab-ci.yml:6-8 documents the variable as masked and protected, but not environment-scoped.

Scenario: with `feat/*` protected, an MR's `build` (`./mvnw verify`) and `e2e` (`npm ci` lifecycle scripts) run with
the token in their environment.

Fix: scope the token to the comment job's environment, document the minimum token scope, and pair it with a protected
environment.

### MINOR
- **MINOR-1.** The comment upsert doesn't filter by author (:644-646). A foreign comment containing the marker blocks
  posting, though the gate is unaffected. Fix: filter to the bot user, or the token user on GitLab.
- **MINOR-2.** Transitive dependencies aren't integrity-pinned. `npm install -g @anthropic-ai/claude-code@2.1.311` has
  no lockfile, `pip install` has no `--require-hashes`, and `.mcp.json` runs `npx -y`. Fix: install from a committed
  lockfile with `npm ci`, and add pip hashes.
- **MINOR-3.** The API URLs (`CI_API_V4_URL`, `GITHUB_API_URL`) have no https check (:613-619). Fix: refuse any scheme
  other than `https://`.
- **MINOR-4.** A required check that was skipped counts as passing. Fix: require all five checks by name, or add an
  `if: always()` aggregator job.
- **MINOR-5.** Fork PRs can never pass the required review, and no documented path exists. Document how a maintainer
  re-pushes the branch.
- **MINOR-6.** GitLab: protected feature branches share the dev and main caches, so a feature branch could poison
  them. Another reason to use a protected environment.
- **MINOR-7.** `Bash(rg *)` allows `rg --pre <command>`, which runs an arbitrary command. Fix: drop the rule, because
  the Grep tool covers the need.
- **Notes:**
  - Correct as is: `_NoRedirect`, TLS verification, stale-verdict cleanup and `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`.
  - The visual-baselines summary writes test names into the job summary, which has a markdown-only effect.
  - The non-CI code raises no concerns.

## Residual risk (prompt injection)
- **False approval:** a PR author doesn't need an injection, because they can edit the instructions or the script
  directly (MAJOR-1). For text that slips through human review, the schema and the preamble reduce the risk but don't
  remove it. Treat the Claude gate as defence in depth, not as the merge authority.
- **Exfiltration:** the channels are the comment and the artifact (MAJOR-2), plus file writes that lead to code
  running in a step that holds a token (BLOCKER-1). After those fixes, what remains is a key leak in encoded form,
  bounded by `--max-budget-usd` and key rotation.

VERDICT: REQUEST CHANGES. One BLOCKER (BLOCKER-1). The MAJORs are strongly recommended before `/pr`. Any MAJOR that is
accepted must be recorded as an SG item in `specs/app_spec.md`.

## Re-review (2026-11-05)

The orchestrator saved this section from the agent's returned text.

Scope: `git diff be6b9f3..HEAD` (`b43bdac`, `a56f01a`, `14d1541`, `e21e813`, `ddc25dd`, `63e0fdf`, `9d3fdc8`), checked
against NFR-09.7 and the amended NFR-09.3. Evidence: 170 script tests OK and all 8 quality gates pass. The only
`secrets.ANTHROPIC_API_KEY` reference is ci.yml:245, in the job with `environment: claude-review`. On GitLab, only
`claude-review` declares that environment.

| ID | Status | Evidence |
|---|---|---|
| BLOCKER-1 | **CLOSED** | **Tools:** only `Read,Grep,Glob`, with Bash, Edit and Write denied (claude_review.py:69-71). **Inputs:** the diff and log are written to `ci-out/review/input/` (:300-317). **Tree check:** fails closed on any change after each pass (:320-347, :704-713). **Comment:** a separate job runs the base-branch script with `python3 -I`. **Redactor:** loaded before the CLI starts. |
| MAJOR-1 | **CLOSED, residual accepted** | **Key:** in the `claude-review` environment with a required reviewer, which GitHub enforces whatever the workflow says. **GitLab:** a protected environment, and the feature branches are no longer protected. **CODEOWNERS:** present. **Residual:** recorded in NFR-09.7 as an accepted v1 limitation. |
| MAJOR-2 | **CLOSED** (the real-CLI check is pending) | **Environment:** an allowlist, tested with a token-bearing `CI_REPOSITORY_URL`. **Reads:** `Read(./**)` is the only allow rule. **Deny rules:** `//proc/**`, system roots, `.git` and home credentials. **Settings:** `--setting-sources user` and `--strict-mcp-config`. **GitLab:** the remote URL is reset before the review. |
| MAJOR-3 | **CLOSED** (config) | The token is scoped to the `claude-review-comment` environment, and the minimum scope is documented. The review job holds no token. |
| MINOR-1–7 | **CLOSED** | Bot-author filter. Lockfiles with integrity hashes. `safe_api_url`. The `ci-gate` aggregator on both platforms. The fork path is documented. No protected feature branches. Bash removed. |

### New findings (no BLOCKER, no MAJOR)
- **NEW-MINOR-1:** a symlink in the checkout could point outside it (for example to `/proc/<ppid>/environ`), and
  `Read(./**)` matches the link path. Only the PR author can add one, behind the environment approval. Fix: fail closed
  if any symlink resolves outside the repository.
- **NEW-MINOR-2:** the tree check doesn't cover `.git/` (`core.fsmonitor`, hooks), compares ignored paths such as the
  CLI's `node_modules` by presence only, and excludes the review input files. This matters only if the no-write sandbox
  fails. Fix: `-c core.fsmonitor=false -c core.hooksPath=/dev/null`, hash `.git/config` and the CLI binary, and
  re-verify the inputs before each pass.
- **NEW-MINOR-3:** harness-setup told the operator to mark the GitLab variables as protected, which contradicts the CI
  header. Protected variables never reach MR pipelines.
- **NEW-MINOR-4:** the controls depend on settings. CODEOWNERS isn't enforced with a single owner, and GitLab ignores
  `.github/CODEOWNERS`. The environment approvals must exist before the first run. Record the MAJOR-1 residual.
- **NEW-MINOR-5:** `ANTHROPIC_API_KEY` also reaches the `git` and `ac_audit.py` child processes, which don't need it.
- **Open points:**
  - The CLI flags are proven only on the first real run. An unknown flag fails closed, and a `/proc` canary pass is
    suggested.
  - The allowlist drops `HTTPS_PROXY` (availability only).

VERDICT: APPROVE

## Resolution log (orchestrator, 2026-11-05)
- **BLOCKER-1, MAJOR-1..3, MINOR-1..7:** the user decided the spec change (protected environment plus CODEOWNERS,
  commit `be6b9f3`). They are fixed in `b43bdac` (red) → `e21e813` (green), `ddc25dd` and `63e0fdf` (ci), and the
  re-review closed them.
- **NEW-MINOR-3:** fixed in harness-setup. The GitLab variables are masked and environment-scoped, not protected.
- **NEW-MINOR-4:** NFR-09.7 now says that code-owner review is not enforced while there is a single owner, and records
  the accepted residual. Environment setup is a pre-run step for the owner.
- **NEW-MINOR-1, -2 and -5, the `/proc` canary and proxy allowlisting:** deferred to a follow-up `chore/` branch after
  the first real CI run.
