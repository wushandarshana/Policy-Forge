# scripts/

| Script | What it does | Claude usage |
|---|---|---|
| `ac_audit.py` | AC → test traceability matrix plus semantic drift review; writes `specs/reviews/ac-audit-*.md`; exits 1 if any AC lacks a test. It scans `scripts/tests/test_*.py` as well as the Java, Vitest and Playwright tests | **Claude Agent SDK** (`claude_agent_sdk.query`) with read-only tools (`Read`, `Glob`, `Grep`), except with `--offline` |
| `redact_evidence.py` | Redacts Basic and Bearer credentials, Spring's generated passwords and Anthropic API keys (`sk-ant-…`) from evidence files; `--check` only reports and exits 1 | none |
| `gen_refund_oracle_cases.py` | Writes the AC-08.5 refund oracle fixture from `premium_check.py`; `--check` exits 1 when the committed CSV differs from a fresh generation | none |
| `ci/quality_gates.py` | Runs the eight CI quality gates, never stops early, prints a summary table and exits 1 if any gate failed | none |
| `ci/claude_review.py` | The required CI review: two read-only passes (`security-reviewer`, `test-engineer`) with the pinned Claude Code CLI; `comment` posts the report to the PR or MR | **Claude Code CLI 2.1.311** in `--bare -p` mode (CI only) |
| `ci/fetch_tools.py` | Downloads actionlint and shellcheck at pinned versions, verifies their SHA-256 and prints the executable path | none |
| `ci/check_line_endings.py` | Fails when a matched tracked file has a CR in the index or the working tree | none |

The `ci/` scripts use the Python standard library only and run on Python 3.11 or later. The same scripts run in
`.github/workflows/ci.yml`, in `.gitlab-ci.yml` and on a developer machine.

## Set up

```bash
pip install -r scripts/requirements.txt       # ac_audit.py full audit (Claude Agent SDK)
python -m venv .venv                          # the tests and gates need PyYAML and check-jsonschema
.venv/Scripts/python -m pip install -r scripts/requirements-ci.txt   # Linux/macOS: .venv/bin/python
```

`requirements-ci.txt` pins `PyYAML==6.0.3` and `check-jsonschema==0.39.0`. Only the direct packages are pinned, not
their dependencies.

## Run

```bash
python scripts/ac_audit.py --offline --no-write       # deterministic matrix only (the CI gate)
python scripts/ac_audit.py                            # full audit (ANTHROPIC_API_KEY or Claude Code login)
python scripts/ci/quality_gates.py                    # all eight gates; --list, --only <name>, --report <file>
python -m unittest discover -s scripts/tests -v       # the CI script tests (a few seconds to a minute)
python scripts/ci/fetch_tools.py actionlint           # prints .ci-tools/<tool>-<version>-<platform>/<exe>
python scripts/ci/check_line_endings.py '.claude/hooks/*.sh'
python scripts/gen_refund_oracle_cases.py --check
```

PARENT-ONLY coverage (a test that names only `AC-NN`) counts as not covered. The `ac-audit` quality gate runs
`ac_audit.py --offline --no-write` and fails on any NOT COVERED or PARENT-ONLY ID. CI does not run the full agent
audit: the `test-engineer` pass of the Claude review covers test quality.

## The quality gates

`quality_gates.py` runs these gates in this order and reports each one. A missing tool is a failure.

| Gate | Command |
|---|---|
| `ac-audit` | `ac_audit.py --offline --no-write` |
| `redact-evidence` | `redact_evidence.py --check sprint-contracts specs/reviews docs/fix-loops` |
| `hook-tests` | `.claude/hooks/tests/test_hooks.py` |
| `hook-line-endings` | `ci/check_line_endings.py '.claude/hooks/*.sh'` |
| `ci-script-tests` | `python -m unittest discover -s scripts/tests -v` |
| `oracle-fixture` | `gen_refund_oracle_cases.py --check` |
| `actionlint` | the fetched actionlint 1.7.12 with the fetched shellcheck v0.11.0 on `.github/workflows/` |
| `ci-schema` | `check-jsonschema` with `vendor.github-workflows` on the workflows and `vendor.gitlab-ci --data-transform gitlab-ci` on `.gitlab-ci.yml` |

The gates never receive `ANTHROPIC_API_KEY`. `actionlint` downloads the two tools on first use into `.ci-tools/`
(ignored by git) and reuses a copy only if its recorded digests still match.

## The Claude review

```bash
python scripts/ci/claude_review.py run --base <sha> --head <sha> --event pull_request --out ci-out/review
python scripts/ci/claude_review.py comment --platform github --report ci-out/review/claude-review.md
```

`run` needs `ANTHROPIC_API_KEY` and the Claude Code CLI **2.1.311** (any other version exits 2). It reviews the range
twice, once per agent file in `.claude/agents/`, each pass on the agent's frontmatter `model`, and writes
`security-reviewer.json`, `test-engineer.json` and `claude-review.md` to `--out`.

| `--event` | Reviewed range |
|---|---|
| `pull_request`, `merge_request` | `merge-base(base, head)..head` |
| `push` | `base..head`; `head^1..head` when `base` is all zeros, unknown or not an ancestor |
| `manual` | `merge-base(origin/dev, HEAD)..HEAD`, or `HEAD^1..HEAD` on `dev` or `main` |
| `auto` (default) | `base..head` for an ancestor `base`, else the merge base |

| Exit | Meaning |
|---|---|
| 0 | No BLOCKER finding. MAJOR and MINOR findings are published but do not gate; an empty diff passes without calling the CLI |
| 1 | At least one BLOCKER finding |
| 2 | The review could not run or returned nothing usable (key unset, wrong CLI version, CLI error or cap hit, missing or schema-invalid verdict) |

Variables: `PF_REVIEW_MAX_TURNS` (default 40), `PF_REVIEW_MAX_BUDGET_USD` (default 5.00) and
`PF_REVIEW_TIMEOUT_SECONDS` (default 1080) apply to each pass; `PF_CLAUDE_CLI` replaces the `claude` executable and
exists for the tests, which use the stub `tests/fixtures/fake_claude.py`. The CLI runs with read-only tools, without
`GITHUB_TOKEN`, `GH_TOKEN`, `CI_JOB_TOKEN` and `PF_GITLAB_REVIEW_TOKEN`, and every output is redacted. `comment`
needs `GH_TOKEN` on a GitHub `pull_request` event, or `PF_GITLAB_REVIEW_TOKEN` and `CI_MERGE_REQUEST_IID` on GitLab;
otherwise it does nothing.

## Tests

`scripts/tests/` holds the `unittest` suite for the CI scripts. Each test's docstring starts with the ID it proves
(`NFR-09.4: …`), which `ac_audit.py` counts. Fake credentials are built by concatenation so that no key-shaped
literal is ever committed. The tests call no API and read no real credential.
