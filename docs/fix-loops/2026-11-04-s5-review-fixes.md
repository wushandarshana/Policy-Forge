# Fix loop: Sprint 5 review findings (writable "read-only" reviewer, token reach, comment job, gate, lockfiles)

- **Detected by:** the post-sprint reviews of `chore/ci-hardening`:
  - [`specs/reviews/security-chore-ci-hardening-20261104.md`](../../specs/reviews/security-chore-ci-hardening-20261104.md) (REQUEST CHANGES)
  - [`specs/reviews/test-chore-ci-hardening-20261104.md`](../../specs/reviews/test-chore-ci-hardening-20261104.md) (APPROVE with minors)
- **Findings fixed in this loop:**

  | ID | Severity | Issue | Spec decision (`be6b9f3`) |
  |---|---|---|---|
  | SEC-BLOCKER-1 | BLOCKER | `Bash(git log/show/diff *)` accept `--output=<file>`, so the reviewer could write `scripts/redact_evidence.py`, which a later step with a token ran | NFR-09.7: no Bash; diff and log as files; fail closed on any tree change outside `ci-out/`; comment from a separate job with base-branch code |
  | SEC-MAJOR-1 | MAJOR | The code under review controls the gate and the key (same-repo PR, protected feature branches on GitLab) | NFR-09.3 / 09.7: key in the protected environment `claude-review` (owner approval), CODEOWNERS on the CI and review files |
  | SEC-MAJOR-2 | MAJOR | `Read` had no path limit (`/proc/*/environ`), the token denylist missed `CI_REPOSITORY_URL` and the registry passwords, and the docstring claimed otherwise | NFR-09.7: allowlisted environment, Read only inside the checkout |
  | SEC-MAJOR-3 | MAJOR | `PF_GITLAB_REVIEW_TOKEN` was project-wide and reached jobs that run MR code | NFR-09.7: token scoped to the environment `claude-review-comment` of a separate job |
  | SEC-MINOR-1 | MINOR | The comment upsert edited any comment with the marker | Only the token's bot user (`github-actions[bot]`, type `Bot`; GitLab note author = `GET /user`) |
  | SEC-MINOR-2 | MINOR | `npm install -g` and `pip install` without lockfiles or hashes | `npm ci` from `scripts/ci/claude-cli/package-lock.json`, `pip install --require-hashes` |
  | SEC-MINOR-3 | MINOR | API URLs had no scheme check | Anything but `https://` is refused before a request |
  | SEC-MINOR-4 | MINOR | A skipped required check counts as passing | `ci-gate` aggregator on both platforms |
  | SEC-MINOR-5 | MINOR | No documented path for fork PRs | Header of `ci.yml` (re-push to `origin`) |
  | SEC-MINOR-6 | MINOR | Protected feature branches share the dev and main caches | Header of `.gitlab-ci.yml`: protect the environment, not the branches |
  | SEC-MINOR-7 | MINOR | `Bash(rg *)` allows `rg --pre <command>` | Gone with Bash |
  | TEST-MINOR-1 | MINOR | The PyYAML precondition was hidden in one test message | `scripts/README.md` and the test message name `pip install --require-hashes -r scripts/requirements-ci.txt` |
  | TEST-MINOR-2 | MINOR | `expectVisual` itself had no automated test | Playwright self-check in both projects |
  | TEST-MINOR-4 | MINOR | Negative-fixture display name lacked the colon after the ID | `NFR-08.1: negative fixture …` |

The `red.log` and `green.log` linked below are copies of `.sprint/20261104-075010/sprint-progress/FIX-S5-{red,green}.log`.
Both pass `redact_evidence.py --check`.

## 1. Spec first: `be6b9f3`
NFR-09.7 and the amended NFR-09.3 were committed on their own before any test or code.

## 2. Reproduce (red): `b43bdac`
Log: [`red.log`](2026-11-04-s5-review-fixes/red.log). `python -m unittest discover -s scripts/tests -v`: **170 run,
50 failures, 0 errors**, every failure an assertion. The only production change is a `read_deny_rules` stub returning
`[]`; `scripts/ci/claude-cli/` was moved aside for the run.

| Test group (`scripts/tests/`) | Before the fix |
|---|---|
| `ToolSandboxTests`: `--tools Read,Grep,Glob`, Bash denied, `Read(./**)` the only allow rule, `/proc` `/sys` `/dev` `/etc` denied but never an ancestor of the checkout, GitHub and GitLab runner layouts | `Read,Grep,Glob,Bash` and the git/rg Bash rules; no Read deny rule |
| `ReviewInputTests`: `ci-out/review/input/diff.patch` equals `git diff base head`, `log.txt` has the range, `changed-files.txt` lists the files; a stale input file is removed | no input folder |
| `ReviewPromptTests`: preamble names the three files and says there is no shell; argv has `--setting-sources user` | preamble told the model to run `git diff` |
| `EnvironmentAllowlistTests`: `CI_REPOSITORY_URL` with a token, `CI_REGISTRY_PASSWORD`, `GITHUB_TOKEN` and random variables never reach the stub CLI; only allowlisted names do; docstring | every variable but four token names reached the CLI |
| `UnchangedTreeTests`: a modified `scripts/redact_evidence.py`, a new untracked file, a new ignored file and a changed pre-existing untracked file exit 2 with one pass only; writes under `ci-out/` and changes inside pre-existing ignored paths pass | exit 0, both passes ran |
| `LockedCliTests`: default CLI is `scripts/ci/claude-cli/node_modules/.bin/claude` | `claude` on PATH |
| `CommentTests`: foreign marked comments (person, other bot, a `User` named like the bot) are never edited; GitLab edits only the note of `GET /user`, posts nothing when `/user` fails; `http://` and `ftp://` API URLs refused before any request | the first marked comment was edited; no scheme check |
| `test_ci_config.py`: comment job, `ci-gate`, status files, CODEOWNERS, lockfile and hashed requirements, header text; amended NFR-09.1 to 09.3 tests | jobs and files absent |

Existing tests changed in the red commit (no assertion weakened): the stub CLI reads its scenario from a config file on
its command line (the allowlist strips `FAKE_CLAUDE_*`), the argv contract, the comment fixtures (bot authors, `GET
/user`, `PF_REVIEW_ALLOW_HTTP_LOOPBACK=1` for the local fake API), `pull-requests: write` moving to
`claude-review-comment`, the install commands, and the PyYAML message (TEST-MINOR-1).

## 3. Test-only commits: `a56f01a`, `14d1541`
- `a56f01a`: the RepriceOfferCallerArchTest negative-fixture display name starts with `NFR-08.1: negative fixture`.
- `14d1541`: `frontend/e2e/visual-baseline.spec.ts`, two NFR-09.5 tests on a `setContent` page in both projects.
  `expectVisual` delegates to the exported `applyVisualPolicy(locator, name, options, {snapshotFile, mode,
  recordFile})`, so the self-check points it at an empty folder and its own skip list and never touches
  `e2e/__snapshots__/` or the shared `visual-baselines-missing.txt`. 4 passed.

## 4. Fix (green): `e21e813`
- **No Bash:** `--tools Read,Grep,Glob`, `--allowedTools Read(./**)`, `--disallowedTools Bash Edit Write NotebookEdit
  WebFetch WebSearch Read(./.git/**)` plus `read_deny_rules(repo, HOME)`: every system root that does not hold the
  checkout (`/proc` always), and `~/**`, or, when the checkout is under HOME (GitHub runners), `~/.ssh/**`,
  `~/.config/**`, `~/.npmrc`, `~/.git-credentials` and the other credential files. `--setting-sources user` keeps
  the reviewed checkout's `.claude/settings.json` from adding allow rules. `--bare`, `--permission-mode dontAsk` and
  `--strict-mcp-config` stay; no skip-permissions flag.
- **Input files:** `write_review_input` empties and writes `ci-out/review/input/` (`git diff --no-ext-diff
  --no-textconv`, `git log --format=fuller --stat`, the file list). The preamble tells the model to Read them.
- **Allowlist:** `child_environment` keeps `PATH`, `HOME`, `LANG`, `LC_ALL`, `TMPDIR`, `TEMP`, `TMP`, `SYSTEMROOT`,
  `ANTHROPIC_API_KEY` and `CLAUDE_*` for the CLI and every git and Python child. The docstring now says so.
- **Unchanged tree:** `tree_snapshot` parses `git status --porcelain=v1 -z --no-renames --untracked-files=all
  --ignored`, skips `ci-out/`, and keeps a SHA-256 of every changed or untracked file; ignored paths are compared by
  presence. Any difference after a pass raises a fail-closed error (exit 2) that names the paths; the next pass never
  starts.
- **Comment:** `_upsert` takes an ownership predicate; `safe_api_url` refuses anything but `https://`.
- **Locked installs:** `scripts/ci/claude-cli/package.json` pins `@anthropic-ai/claude-code` **2.1.311**;
  `package-lock.json` (lockfile v3, generated with `npm install --package-lock-only`) holds sha512 integrity for it and
  all eight platform packages. `cli_command()` defaults to its `node_modules/.bin/claude`. `requirements-ci.txt` pins
  16 packages (PyYAML, check-jsonschema and every transitive dependency, `typing-extensions` for Python < 3.13) with
  the sha256 of every file of each release (from the PyPI JSON API).

## 5. CI definitions: `ddc25dd` (GitHub), `63e0fdf` (GitLab)
- GitHub `claude-review`: `environment: claude-review`, no write permission and no token, `npm ci --prefix
  scripts/ci/claude-cli`, uploads only `claude-review`. `claude-review-comment`: `needs: claude-review`, `if: always()
  && github.event_name == 'pull_request'`, `pull-requests: write`, no environment or key, checks out
  `github.base_ref`, downloads the artifact to `runner.temp`, runs `python3 -I scripts/ci/claude_review.py comment`
  from the base checkout, or prints a `::notice::` and exits 0 when the base branch has no script. `ci-gate`: needs
  the five gate jobs, `if: always()`, fails unless every `needs.<job>.result` is `success`.
- GitLab: `claude-review` in the protected environment `claude-review`, no review token, no comment.
  `claude-review-comment` (MR pipelines, `when: always`, environment `claude-review-comment`) fetches the target
  branch and runs its `claude_review.py` from a `git worktree`. Each gate job writes `ci-out/status/<job>.ok`
  (the pipeline id) as its last command and keeps it as an artifact; `ci-gate` (stage `gate`, `when: always`,
  `GIT_STRATEGY: none`) requires all five for this pipeline. The header documents the protected environments, the
  token's minimum scope (project access token, Reporter, `api`, with an expiry) and drops the protected feature
  branches.
- `.github/CODEOWNERS`: `*` plus `/.github/**`, `/.gitlab-ci.yml`, `/scripts/ci/**`, `/scripts/redact_evidence.py`,
  `/scripts/ac_audit.py`, `/.claude/agents/**`, all `@wushandarshana`.

## 6. Validate
Log: [`green.log`](2026-11-04-s5-review-fixes/green.log).
- `.venv` rebuilt with `pip install --require-hashes -r scripts/requirements-ci.txt` (CPython 3.14, Windows): OK.
  The hash set was checked against PyPI for the wheel pip picks on Linux x86_64 CPython 3.11, 3.12 and 3.14 and on
  Windows CPython 3.14: 0 missing.
- `python -m unittest discover -s scripts/tests -v`: **170 run, OK**.
- `quality_gates.py`: **8/8** (ac-audit, redact-evidence, hook-tests, hook-line-endings, ci-script-tests,
  oracle-fixture, actionlint, ci-schema).
- `ac_audit.py --offline --no-write`: **93/93** covered, 0 parent-only, 0 not covered (NFR-09.7 COVERED).
- `./mvnw -o verify -Dskip.frontend=true`: **BUILD SUCCESS**, surefire **490** and failsafe **570** tests, 0 failures,
  0 errors, 0 skipped. It ran on JDK 21.0.13 (`jdk-21.0.13.9-hotspot`): the 21.0.12 folder named in the task no longer
  exists on this machine (environment, not code).
- `npm test`: 17 files, **173/173**; `npm run build`: OK.
- `npx playwright test --update-snapshots=none`: **48 passed**, desktop-chromium 24 and mobile-chromium 24 (22 journey
  tests plus the 2 self-check tests per project). Port 8080 free afterwards.
- `redact_evidence.py --check` on the sprint progress folder and on this folder: exit 0.

## 7. Only the first real CI run can prove
| Item | Why |
|---|---|
| The pinned CLI 2.1.311 accepts `--tools`, `--setting-sources user`, `Read(...)` rules in `--disallowedTools` and `dontAsk` as used | Verified only against the 2.1.318 `--help`; the 2.1.311 Windows binary could not be downloaded here. An unknown flag fails closed (exit 2) |
| `Read(./**)` lets Grep and Glob work inside the checkout under `dontAsk`, and the deny rules block `/proc` | Permission matching runs inside the real CLI; the tests use a stub |
| The CLI writes nothing inside the checkout during a pass (for example a `.claude/` folder) | Otherwise the unchanged-tree check fails the run closed |
| The CLI needs nothing outside the allowlist (no proxy variables, `USER`, `SHELL`) | A runner behind a proxy would need `HTTPS_PROXY`, which the allowlist drops |
| `npm ci --prefix scripts/ci/claude-cli` installs and links `node_modules/.bin/claude` on Linux (the package has an install script) | Not run locally |
| `pip install --require-hashes` on the Linux runners | Checked by hash coverage against PyPI, not by an install on Linux |
| Environment approvals, environment-scoped secrets and variables, the base-branch comment job and its notice on the first PR, `ci-gate` as the required check, CODEOWNERS enforcement | Repository and project settings |

## 8. Deferred follow-ups (not in this loop)
| Item | Source | Note |
|---|---|---|
| `scripts/CLAUDE.md` still describes the Bash rules of the CLI | This loop | Human substrate: no Bash, input files, allowlist, unchanged tree, comment job |
| `docs/harness-setup.md`: environments `claude-review` and `claude-review-comment`, `ci-gate` as the required check, CODEOWNERS rule | This loop | Human substrate |
| GitLab reads CODEOWNERS from the root, `docs/` or `.gitlab/`, not `.github/` | This loop | Add `.gitlab/CODEOWNERS` (or a root file) if GitLab code-owner approval is wanted |
| A single owner cannot approve their own PR, so "Require review from Code Owners" blocks the owner's PRs | This loop | Needs a second code owner or a bypass |
| On `pull_request` the workflow file itself comes from the PR, so a PR can still rewrite the comment job | SEC residual risk | Environment approval and human review remain the control |
| `-linux` visual baselines | TEST-MINOR-3 | After the first `update-linux-snapshots` run |

## Lesson
A tool allowlist by command prefix is not a sandbox: flags such as `--output` and `--pre` turn read commands into
writes and execs. Give the model data, not a shell, and check the outcome (the tree) instead of trusting the rules.

## Round 2: first real CI run (2026-11-05)
GitHub Actions run 41960130498 on PR #5 passed build, frontend-unit, e2e and quality-gates. `claude-review` then
failed closed: the pinned CLI 2.1.311 exited 1 at start-up with `bubblewrap is required for subprocess env scrubbing
and isolation`. The runner always sets `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`, and that stays: the fix makes bubblewrap
available instead of turning scrubbing off.

| Commit | What |
|---|---|
| `a337a5f` test(red) | Config tests: both `claude-review` jobs install bubblewrap (`apt-get update`, then `apt-get install -y --no-install-recommends bubblewrap`), record the version with `dpkg-query -W bubblewrap` (NFR-09.2: distro archive, not a pinned string), run `bwrap --version` and the `--unshare-all` smoke test that fails closed with a user-namespace message, all before the review command; the GitHub step has no `ANTHROPIC_API_KEY`; the GitLab header names the user-namespace requirement. Runner tests: no `bwrap` on PATH means exit 2 with "bubblewrap missing" and no CLI call; a `bwrap` on PATH reaches the CLI through the allowlisted PATH; the test switch counts only with `PF_CLAUDE_CLI` |
| `ca63794` fix(green) | `check_bubblewrap()` runs before the version check. `PF_REVIEW_SKIP_BWRAP_CHECK=1` skips it only together with `PF_CLAUDE_CLI` (the stub tests, which also run on Windows); the real CLI is always checked |
| `3ea7249` ci | GitHub: a step "Install and smoke-test bubblewrap" before the review step. GitLab: the same script before the review command, plus the header note (allow user namespaces or privileged mode, otherwise the review fails closed) |

Validated: `unittest discover -s scripts/tests` 175/175 OK, `quality_gates.py` 8/8 (actionlint included),
`ac_audit.py --offline --no-write` 93/93, 0 not covered. Still to prove on the next real run: the bwrap smoke test under
Ubuntu 24.04's AppArmor user-namespace restriction, and that the CLI's own sandboxed subprocesses work under bwrap.

## Round 3: second real CI run (2026-11-05)
GitHub Actions run 41973681887 installed bubblewrap 0.9.0-1ubuntu0.3 (recorded by `dpkg-query`), but the smoke test
`bwrap --ro-bind / / --dev /dev --proc /proc --unshare-all true` failed with `bwrap: loopback: Failed RTM_NEWADDR:
Operation not permitted`. `--unshare-all` also creates a network namespace, and the runner does not let bwrap set up
its loopback there. That is a network-namespace limit, not proof that user namespaces are missing. `claude-review`
then failed before it uploaded a report, and `claude-review-comment` failed at `actions/download-artifact` with
"Artifact not found".

| Commit | What |
|---|---|
| `5a5a596` test(red) | `BubblewrapInstallTests` expect `--unshare-all --share-net` and the message "bwrap cannot create a sandbox on this runner". `MissingReportTests`: the GitHub download step has an id and `continue-on-error: true`, and the comment step exits 0 with a `::notice::` when `steps.download.outcome` is not `success` or the report is missing. On GitLab the comment job exits 0 with a notice when `ci-out/review/claude-review.md` is missing, before `git fetch`, and is `allow_failure: true`. `ci-gate` needs neither comment job |
| `cce144b` test(red) | The NFR-09.1 rule "no continue-on-error" now applies to every job except one: it is allowed only on the report download of `claude-review-comment`. GitLab still has none |
| `1167f9e` ci | Both smoke tests use `--share-net`, and the fail-closed messages are corrected. The comment jobs tolerate a missing report |

Open question: we can't tell from here which namespaces the pinned CLI 2.1.311 asks bwrap for when it scrubs
subprocess environments. If it unshares the network as well, the CLI hits the same loopback error, and the next real
run shows it as a CLI failure, not a smoke-test failure. `--share-net` proves only that user, mount, PID, IPC and UTS
namespaces work on the runner.

## Round 4: third real CI run (2026-11-05)
GitHub Actions run 41979264606 got past the loopback problem. The `--unshare-all --share-net` smoke test then failed
with `bwrap: setting up uid map: Permission denied`. That is Ubuntu 24.04's AppArmor restriction on unprivileged user
namespaces (`kernel.apparmor_restrict_unprivileged_userns=1`), and the profile from the bubblewrap package did not lift
it on the runner. The user decided to lift the restriction on the GitHub-hosted runner and keep the full sandbox.
Scrubbing stays on.

| Commit | What |
|---|---|
| `1cc8956` test(red) | GitHub: the bubblewrap step runs `sudo sysctl -w kernel.apparmor_restrict_unprivileged_userns=0` only when `/proc/sys/kernel/apparmor_restrict_unprivileged_userns` exists, echoes the resulting value before the smoke test, documents the ephemeral VM and has no key. GitLab: no sysctl, and the header names the executor options. Neither file sets `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB` to 0 |
| `ffa72a5` ci | The guarded sysctl and its echo in the GitHub step, with the ephemeral-VM comment. The GitLab header says to use a shell or VM executor with the sysctl at 0, or a privileged Docker executor; otherwise the review fails closed by design |

Why it is acceptable on GitHub: the hosted runner is a fresh VM for each job and is destroyed afterwards, so the
change lasts only for this run. Still to
prove on the next real run: the smoke test passes, and the CLI's own bwrap use, whose namespace set is unknown,
works with it.

## Round 5: fourth real CI run (2026-11-05)
GitHub Actions run 41981193751 got past bubblewrap. The sysctl reported `kernel.apparmor_restrict_unprivileged_userns
= 0`, the smoke test passed, and the security-reviewer pass ran for about 90 s. The unchanged-tree check then failed
closed: `.env`, `.env.development`, `.env.development.local`, `.env.local`, `.env.production`,
`.env.production.local`, `.env.test`, `.env.test.local`, `.gitmodules`, `.npmrc` "and 7 more" had appeared in the
checkout. These are almost certainly the empty placeholder files the CLI's bubblewrap sandbox creates in its working
folder so it can mount over sensitive paths, not writes by the model. The check worked as designed, but the CLI must
not run in the real checkout.

| Commit | What |
|---|---|
| `ea25943` test(red) | The stub CLI accepts a list of writes, deletes and a follow-up scenario. New `DisposableWorktreeTests`: the CLI's cwd is a worktree outside the checkout and holds the input files; the placeholders (13 of them) are tolerated and every path is logged, with no contents; a modified or deleted tracked file in the worktree exits 2; the worktree is removed after success and after failure; a post-checkout hook in the checkout does not run. `UnchangedTreeTests` now write into the real checkout by absolute path (a write that escapes the worktree), and a 14-path change is listed in full |
| `c7a709d` fix(green) | `claude_review.py`: `create_worktree` adds a detached worktree of HEAD under `RUNNER_TEMP`, `TMPDIR` or the system temp folder (refusing a location inside the checkout). Every git call runs with `-c core.fsmonitor=false -c core.hooksPath=<null device>`. The input files go to the worktree, and each pass runs with the worktree as cwd, so `Read(./**)` and the deny rules are computed for the copy. Around each pass, the unchanged-tree check of the real checkout is unchanged, a SHA-256 snapshot of the worktree's tracked files fails closed on any change or deletion, and new untracked or ignored worktree paths are printed in full (paths only, redacted). A `finally` runs `git worktree remove --force`, removes the folder and runs `git worktree prune`. Tree-change errors list every path. The redactor is still loaded from the real checkout before any pass |

What still holds: anything the CLI writes outside the worktree that lands in the real checkout fails the job, and the
real checkout is what later steps run code from. The worktree is thrown away, so placeholders there cannot reach
another step. Still to prove on the next real run: the CLI works from a worktree under `RUNNER_TEMP`
(`/home/runner/work/_temp`, inside the home folder, so the home credential files are denied one by one), and both
passes finish.

## Pull request
Landed in [PR-011](../prs/PR-011.md) = GitHub [#5](https://github.com/wushandarshana/Policy-Forge/pull/5) (`chore/ci-hardening` → `dev`), merge commit `f3a5bc9`. The real CI rounds 2 to 5 above ran on that PR, and its first fully green run was 41983857770. The `-linux` baselines followed in [#6](https://github.com/wushandarshana/Policy-Forge/pull/6) (`943359a`).
