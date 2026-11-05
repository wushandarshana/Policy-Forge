# TASK-002 Progress

## Status
DONE

## What was built
The CI scripts of Sprint 5, all under `scripts/` and all standard-library only (Python 3.11 compatible):
- `scripts/ci/claude_review.py` (`run` and `comment`, plan C5) with `scripts/ci/review-verdict.schema.json` (C6) and a
  standard-library validator. `run` reviews a commit range in two passes with the pinned Claude Code CLI 2.1.311
  (`--bare -p`, read-only tools, the exact C5 flag set), sends each agent body byte for byte on stdin behind a fixed
  preamble, fails closed (exit 2) on a missing key, a wrong CLI version, a CLI error or cap hit, or a missing or
  schema-invalid verdict, and exits 1 on any BLOCKER. The CLI never receives `GITHUB_TOKEN`, `GH_TOKEN`,
  `CI_JOB_TOKEN` or `PF_GITLAB_REVIEW_TOKEN` and gets `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`. Every output is redacted
  with the `redact_evidence.py` patterns plus the literal key. `comment` upserts one marked PR comment (GitHub) or MR
  note (GitLab) and never follows a redirect.
- `scripts/ci/quality_gates.py` (C11): the eight gates in order, never stops early, summary table, `--list`,
  `--only`, `--report`; a missing tool or an environment without check-jsonschema fails the gate (exit 127 shown).
- `scripts/ci/fetch_tools.py`: the C3 actionlint 1.7.12 and shellcheck v0.11.0 manifest for linux-x86_64 and
  windows-x86_64; https only with TLS verification; SHA-256 checked before extraction; digest-checked reuse.
- `scripts/ci/check_line_endings.py`: fails on a CR byte in the working tree or `i/crlf` or `i/mixed` in the index.
- `scripts/redact_evidence.py` gained `sk-ant-[A-Za-z0-9_-]{20,}` -> `<redacted-api-key>`; `scripts/ac_audit.py` gained
  the `scripts/tests/test_*.py` glob (one line, nothing else); `scripts/requirements-ci.txt` pins `PyYAML==6.0.3` and
  `check-jsonschema==0.39.0` (installed into the gitignored `.venv/`); `.gitignore` ignores `.ci-tools/` and `ci-out/`;
  `scripts/README.md` documents all of it and no longer mentions `--report-only` or a full audit in claude-review.
- 111 `unittest` tests in `scripts/tests/` (docstring of every test starts with `NFR-09.3`, `NFR-09.4` or `NFR-09.6`)
  with the stub CLI `scripts/tests/fixtures/fake_claude.py`. The ac_audit offline matrix now reports NFR-09.3, NFR-09.4
  and NFR-09.6 as COVERED; NFR-09.1, NFR-09.2 and NFR-09.5 remain for TASK-004 and
  TASK-003, so the audit is 89/92 until they land.

## Files changed
- .gitignore (`.ci-tools/`, `ci-out/`; plus a comment line and a blank line, 4 insertions in total)
- scripts/requirements-ci.txt
- scripts/ac_audit.py (one glob)
- scripts/redact_evidence.py (one pattern, docstring)
- scripts/README.md
- scripts/ci/claude_review.py
- scripts/ci/review-verdict.schema.json
- scripts/ci/quality_gates.py
- scripts/ci/fetch_tools.py
- scripts/ci/check_line_endings.py
- scripts/tests/ci_test_support.py (helpers, not collected)
- scripts/tests/fixtures/fake_claude.py (stub CLI)
- scripts/tests/test_claude_review.py
- scripts/tests/test_quality_gates.py
- scripts/tests/test_fetch_tools.py
- scripts/tests/test_check_line_endings.py
- scripts/tests/test_redact_evidence.py
- scripts/tests/test_ac_audit_globs.py
- scripts/tests/test_ci_script_hygiene.py
- .sprint/20261104-075010/sprint-progress/TASK-002.md, TASK-002-red.log, TASK-002-green.log,
  TASK-002-claude-2.1.318-help.txt, TASK-002-claude-2.1.318-version.txt (workspace, not committed)

Nothing else was touched: no substrate, no `backend/`, no `frontend/`, and `scripts/gen_refund_oracle_cases.py` is
TASK-001's. The untracked `backend/src/test/resources/oracle/` and `scripts/gen_refund_oracle_cases.py` that appeared
in `git status` during the work were TASK-001's and were never staged by this task.

## Commits (all on `chore/ci-hardening`, explicit paths, nothing pushed)
Amendments A (`4730168`) and B (`f9a1664`) exist after `d9a5694` and were not recreated.
1. `2b307af` chore(ci): pin CI-only Python packages and ignore `.ci-tools/` and `ci-out/`
2. `9e9b20c` test(red): NFR-09.3, NFR-09.4, NFR-09.6 tests plus `scripts/ci` stubs. Red result: 110 tests, 97
   failures, 21 errors, 15 passes. The 15 passes are negative or characterisation checks that a do-nothing stub
   satisfies (no request is made, the key is not in the outputs, the existing Basic/Bearer patterns, the hygiene
   checks on the stub files, the unchanged globs). The errors come from stubs that return `None` (for example
   `ssl_context()`); the rest fail on an assertion. Log: `TASK-002-red.log`.
3. `11fd0f9` feat(green): NFR-09.3 redact pattern, NFR-09.6 ac_audit glob
4. `12dc531` feat(green): NFR-09.6 check_line_endings, fetch_tools, quality_gates
5. `10fae5e` feat(green): NFR-09.3, NFR-09.4 claude_review.py and the verdict schema
6. `3da04da` feat(green): NFR-09.6 quality_gates prints UTF-8
7. `61f20f3` feat(green): README, and the no-redirect hardening of `comment`

Green result: `python -m unittest discover -s scripts/tests -v` runs 111 tests, all OK (about 45 s on this Windows
host). `python .claude/hooks/tests/test_hooks.py` prints ALL PASSED. `redact_evidence.py --check sprint-contracts
specs/reviews docs/fix-loops` gives total 0. `git grep` finds no `sk-ant-` literal of 20 or more characters. Log:
`TASK-002-green.log`.

Two edits touched `test_claude_review.py` after the red commit, both recorded in commit messages: the GitLab and GitHub
header assertions compare header names case-insensitively (urllib capitalises `PRIVATE-TOKEN` to `Private-token`), and
one test was added for the no-redirect hardening, with its mutation proof in the green log (section 12).

## Interface for TASK-004 (what the workflows must call)
- Review step (GitHub `claude-review`, GitLab `claude-review`), the key only in this step's `env`:
  `python3 scripts/ci/claude_review.py run --base "$BASE" --head "$HEAD" --event "$EVENT" --out ci-out/review`.
  Values go through `env:`, never through `${{ }}` inside `run:`. `--event` is an addition to the C1 command line
  (default `auto`, so the C1 form still works); it selects the P-7 rule:
  - pull request: `--event pull_request`, BASE = `github.event.pull_request.base.sha`, HEAD = `...head.sha`
  - GitLab MR: `--event merge_request`, BASE = `CI_MERGE_REQUEST_DIFF_BASE_SHA`, HEAD = `CI_COMMIT_SHA`
  - push: `--event push`, BASE = `github.event.before` (`CI_COMMIT_BEFORE_SHA`), HEAD = `github.sha` (`CI_COMMIT_SHA`);
    an all-zero or non-ancestor BASE falls back to `HEAD^1..HEAD`
  - manual: `--event manual` (BASE ignored, may be empty)
  Needs `fetch-depth: 0` (`GIT_DEPTH: 0`). The step must not be `continue-on-error`: exit 1 (BLOCKER) and exit 2
  (fail closed) both fail the job. It prints the report to the log and appends it to `$GITHUB_STEP_SUMMARY` when set.
  Outputs in `ci-out/review/`: `claude-review.md` (last line `GATE: PASS|FAIL (...)`), `security-reviewer.json`,
  `test-engineer.json`. The runner needs `git` and Python 3.11+ on the runner, and the CLI on `PATH` (or `PF_CLAUDE_CLI`).
- Comment step (a separate step with `GH_TOKEN` only, no key): `python3 scripts/ci/claude_review.py comment
  --platform github --report ci-out/review/claude-review.md`. It posts only when `GITHUB_EVENT_NAME` is
  `pull_request` and `GH_TOKEN` is set, reads `GITHUB_EVENT_PATH`, `GITHUB_REPOSITORY`, `GITHUB_API_URL`; GitLab:
  `--platform gitlab` with `PF_GITLAB_REVIEW_TOKEN`, `CI_MERGE_REQUEST_IID`, `CI_PROJECT_ID`, `CI_API_V4_URL`. Without
  those it does nothing and exits 0. An API failure is a warning and exits 0. Use `if: always()` so the comment also
  appears when the gate failed, but note the runner already wrote the failure report.
- Quality gates: `pip install -r scripts/requirements-ci.txt` into the interpreter that then runs
  `python scripts/ci/quality_gates.py --report ci-out/quality-gates/report.md` (artifact `quality-gates-report`).
  The `ci-schema` gate runs `python -m check_jsonschema` with that interpreter. The job needs network access to
  github.com (actionlint and shellcheck archives, verified against the C3 SHA-256) and `git` (the LF check
  and the script tests build temporary git repositories). Gate 5 runs the whole script test suite, so the job
  should not run it separately. The gates fail until `.github/workflows/*.yml` and `.gitlab-ci.yml` exist
  (actionlint, ci-schema) and until NFR-09.1, NFR-09.2 and NFR-09.5 have tests (ac-audit).
- Direct tool use: `python scripts/ci/fetch_tools.py actionlint|shellcheck` prints the executable path on stdout and
  nothing else; `actionlint -shellcheck <that path> .github/workflows/*.yml` is the lint command.
- Do not name NFR-09.1, NFR-09.2 or NFR-09.5 in any test or docstring before the red commit that is meant to
  prove them: `ac_audit.py` counts any reference to an ID as coverage.
- `scripts/ci/*.py` are tested for Python 3.11 syntax and stdlib-only imports; keep them that way if you edit them.

## P-15 record: the Claude Code CLI
- Installed: `claude --version` prints `2.1.318 (Claude Code)`. Full `--help` is in
  `TASK-002-claude-2.1.318-help.txt` and in the appendix below. Every C5 flag is listed there except one:
  `--max-turns` is not in `--help` (the plan notes it is documented but hidden). Flag lines found: `--bare`,
  `--output-format <format>` ("json" single result), `--json-schema <schema>` (inline JSON string, print mode),
  `--model <model>`, `--tools <tools...>`, `--allowedTools`, `--disallowedTools`, `--permission-mode <mode>`
  (includes `dontAsk`), `--max-budget-usd <amount>`, `--no-session-persistence`, `--strict-mcp-config`, `-p, --print`.
- Handling of the missing flag: the runner passes `--max-turns` as the plan says. If CLI 2.1.311 rejects an unknown or
  hidden flag, each pass exits non-zero and the gate fails closed at the first real CI run. If that happens, the
  one-line fix is to drop `--max-turns` from `run_pass` (the `--max-budget-usd` cap still bounds the spend) and update
  the argv test. The live run is the first place this can be seen (POST-PUSH step 2).
- 2.1.311: npm metadata confirms `@anthropic-ai/claude-code@2.1.311` exists, is the `stable` tag (`latest` and `next` are
  2.1.318) and requires Node 22 or later. Its Windows binary package is 298,871,002 bytes unpacked. `npm install` into a
  throwaway directory outside the repository was cut off by this host's download speed (about 54 KB/s): `claude.exe`
  stopped at 24,747,426 bytes and fails with "Exec format error", so `--version` and `--help` of 2.1.311 could not be
  run. No prompt was sent and no key or login was used. The temporary directory was deleted.
- What was exercised live with the real CLI: `claude_review.py run` with a dummy key and no `PF_CLAUDE_CLI` starts the
  installed `claude`, reads `--version`, and exits 2 with "the Claude Code CLI must be version 2.1.311, found: 2.1.318
  (Claude Code)" before any prompt (green log section 8).

## Decisions and deviations
- `check_line_endings.py` fails when a pattern matches no tracked file (the criterion lists the failing conditions
  and says "passes otherwise"; a typo in the gate pattern must not turn the gate into a silent no-op, which the
  hooks' own CLAUDE.md calls worse than a failing guardrail). Easy to relax in `main`.
- The commit trailer is `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`, the host's attribution rule for
  the model that actually wrote the commits, not the "Opus 5.5" text of the task prompt. Say if the sprint log
  needs the other name.
- A failed first pass stops the review (no second paid pass). `CHANGES_REQUESTED` without a BLOCKER warns (stderr and
  the report) and exits 0. A model that labels its verdict with the other agent's name is reported under the pass's
  own agent, with a warning.
- The security-reviewer agent was not run on the runner: this generator has no way to start a subagent. I reviewed the
  secret handling myself against the P-6 list (findings below).
- `.gitignore` got a comment line and a blank line next to the two required lines.

## Open issues
- Self-review of the runner's secret handling, residual risks for the orchestrator to weigh:
  1. The CLI process itself holds `ANTHROPIC_API_KEY` (`--bare` reads only that). `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`
     strips it from the Bash tool's subprocesses, but the `Read` tool is allowed without a path rule, so a prompt-injected
     review could read `/proc/self/environ` of the CLI on Linux. The only output channel is the verdict, which is
     redacted (pattern and literal value), and no network tool is allowed, so the practical risk is small. C5 fixes the
     flag set, so no deny rule such as `Read(/proc/**)` was added; consider one as a later hardening.
  2. A same-repo PR can edit `scripts/ci/claude_review.py` or `.claude/agents/*.md`, and the pull_request run uses
     the PR's own copy (SG-10: authors are trusted). Required status checks plus review of those paths are the control.
  3. Encoded secrets (for example base64 of the key) in the verdict would not be redacted.
- Python 3.11 is not installed here. The syntax is checked with `ast.parse(feature_version=(3, 11))` in the test suite;
  `vermin` 1.9.2 reports a minimum of 3.10; the suite ran on 3.14.7. The first run on the GitLab Node image (Debian
  Python 3.11) is the real check.
- The suite takes about 45 s on Windows (process and git start-up); it is fine for the 20-minute `quality-gates` job.
- The real CLI on Windows is a `claude.CMD` shim; `--version` works, but a long `--json-schema` argument through the
  shim was not exercised (no prompt may be sent). CI runs on Linux, where the CLI is a native binary.
- `fetch_tools.py` downloaded both real archives here and their SHA-256 values matched the C3 pins. The shellcheck
  download took about 3 minutes at this host's speed; GitHub-hosted runners are much faster.
- `ac_audit.py --report-only` still exists in the script (it was out of scope to change) but is no longer documented.
- ac_audit reports 89/92 until TASK-003 (NFR-09.5) and TASK-004 (NFR-09.1, NFR-09.2) add their tests.

## Appendix: `claude --version` and `claude --help` of the installed CLI (2.1.318)
```
2.1.318 (Claude Code)
```
```
Usage: claude [options] [command] [prompt]

Claude Code - starts an interactive session by default, use -p/--print for
non-interactive output

Arguments:
  prompt                                Your prompt

Options:
  --add-dir <directories...>            Additional directories to allow tool
                                        access to
  --agent <agent>                       Agent for the current session. Overrides
                                        the 'agent' setting.
  --agents <json-or-file>               JSON object defining custom agents, or
                                        with --print the path to a file that
                                        holds one (e.g. '{"reviewer":
                                        {"description": "Reviews code",
                                        "prompt": "You are a code reviewer"}}')
  --allow-dangerously-skip-permissions  Enable bypassing all permission checks
                                        as an option, without it being enabled
                                        by default. Recommended only for
                                        sandboxes with no internet access.
  --allowedTools, --allowed-tools <tools...>
      Comma or space-separated list of tool names to allow (e.g. "Bash(git *)
      Edit")
  --append-system-prompt <prompt>       Append a system prompt to the default
                                        system prompt
  --autocompact <auto|tokens>           Auto-compact window size (auto, or
                                        100k–1M tokens)
  --ax-screen-reader                    Render screen-reader friendly output
                                        (flat text, no decorative borders or
                                        animations).
  --bg, --background                    Start the session in the background and
                                        return immediately. Prints the id that
                                        `claude attach`, `logs`, `stop` and `rm`
                                        take; `claude agents` lists them. With
                                        --resume <session-id>, continues that
                                        session in the background under the same
                                        ID, or starts a copy and says so when
                                        the session is already running
  --bare                                Minimal mode: skip hooks (those defined
                                        in settings and by installed plugins;
                                        features built into Claude Code are
                                        unaffected), LSP, plugin sync,
                                        attribution, auto-memory, background
                                        prefetches, keychain reads, and
                                        CLAUDE.md auto-discovery. Sets
                                        CLAUDE_CODE_SIMPLE=1. Anthropic auth is
                                        strictly ANTHROPIC_API_KEY or
                                        apiKeyHelper via --settings (OAuth and
                                        keychain are never read). 3P providers
                                        (Bedrock/Vertex/Foundry) use their own
                                        credentials. Skills still resolve via
                                        /skill-name. Explicitly provide context
                                        via: --system-prompt[-file],
                                        --append-system-prompt[-file], --add-dir
                                        (CLAUDE.md dirs), --mcp-config,
                                        --settings, --agents, --plugin-dir.
  --betas <betas...>                    Beta headers to include in API requests
                                        (API key users only)
  --brief                               Enable SendUserMessage tool for
                                        agent-to-user communication
  --chrome                              Enable Claude in Chrome integration
  --cloud [description|session_id|url]  Create a cloud session with the given
                                        description, or attach to an existing
                                        one by session ID or claude.ai/code URL
  -c, --continue                        Continue the most recent conversation in
                                        the current directory
  --dangerously-skip-permissions        Bypass all permission checks.
                                        Recommended only for sandboxes with no
                                        internet access.
  -d, --debug [filter]                  Enable debug mode with optional category
                                        filtering (e.g., --debug=hooks,mcp or
                                        --debug='!hooks,!mcp')
  --debug-file <path>                   Write debug logs to a specific file path
                                        (implicitly enables debug mode)
  --desktop                             Open in the Claude Desktop app instead
                                        of the terminal (with --continue or
                                        --resume <id> to pick the session)
  --disable-slash-commands              Disable all skills and commands
  --disallowedTools, --disallowed-tools <tools...>
      Comma or space-separated list of tool names to deny (e.g. "Bash(git *)
      Edit")
  --effort <level>                      Effort level for the current session
                                        (low, medium, high, xhigh, max)
  --environment <environment_id>        Create a new cloud session that runs on
                                        the given self-hosted environment
                                        (ccpool_...).
  --exclude-dynamic-system-prompt-sections
      Move per-machine sections (cwd, env info, memory paths, git status) from
      the system prompt into the first user message. Improves cross-user
      prompt-cache reuse. Only applies with the default system prompt (ignored
      with --system-prompt). (default: false)
  --fallback-model <model>              Enable automatic fallback to specified
                                        model(s) when the default model is
                                        overloaded or not available. Accepts a
                                        comma-separated list to try each in
                                        order. Re-tries the primary at the start
                                        of each user turn.
  --file <specs...>                     File resources to download at startup.
                                        Format: file_id:relative_path (e.g.,
                                        --file file_abc:doc.txt
                                        file_def:img.png)
  --fork-session                        When resuming, create a new session ID
                                        instead of reusing the original (use
                                        with --resume or --continue)
  --forward-subagent-text               Forward subagent text and thinking
                                        blocks as assistant/user messages with
                                        parent_tool_use_id set (only works with
                                        --print and --output-format=stream-json)
  --from-pr [value]                     Resume a session linked to a PR by PR
                                        number/URL, or open interactive picker
                                        with optional search term
  -h, --help                            Display help for command
  --ide                                 Automatically connect to IDE on startup
                                        if exactly one valid IDE is available
  --include-hook-events                 Include all hook lifecycle events in the
                                        output stream (only works with
                                        --output-format=stream-json)
  --include-partial-messages            Include partial message chunks as they
                                        arrive (only works with --print and
                                        --output-format=stream-json)
  --input-format <format>               Input format (only works with --print):
                                        "text" (default), or "stream-json"
                                        (realtime streaming input) (choices:
                                        "text", "stream-json")
  --json-schema <schema>                JSON Schema for structured output
                                        validation (only works with --print).
                                        Example:
                                        {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
  --max-budget-usd <amount>             Maximum dollar amount to spend on API
                                        calls (only works with --print)
  --mcp-config <configs...>             Load MCP servers from JSON files or
                                        strings (space-separated)
  --model <model>                       Model for the current session. Provide
                                        an alias for the latest model (e.g.
                                        'fable', 'opus', or 'sonnet') or a
                                        model's full name.
  -n, --name <name>                     Set a display name for this session
                                        (shown in the prompt box, /resume
                                        picker, and terminal title)
  --no-chrome                           Disable Claude in Chrome integration
  --no-session-persistence              Disable session persistence - sessions
                                        will not be saved to disk and cannot be
                                        resumed (only works with --print)
  --output-format <format>              Output format (only works with --print):
                                        "text" (default), "json" (single
                                        result), or "stream-json" (realtime
                                        streaming) (choices: "text", "json",
                                        "stream-json")
  --permission-mode <mode>              Permission mode to use for the session
                                        (choices: "acceptEdits", "auto",
                                        "bypassPermissions", "manual",
                                        "dontAsk", "plan")
  --permission-prompts <target>         Who answers permission prompts with
                                        --print: "host" (the SDK host or
                                        --permission-prompt-tool) or "none"
                                        (nobody: anything that would prompt is
                                        denied automatically; the permission
                                        mode still decides everything else)
                                        (choices: "host", "none", default:
                                        "host")
  --plugin-dir <path>                   Load a plugin from a directory or .zip
                                        for this session only; a folder of
                                        plugins loads each child (repeatable:
                                        --plugin-dir A --plugin-dir B.zip)
                                        (default: [])
  --plugin-url <url>                    Fetch a plugin .zip from a URL for this
                                        session only (repeatable: --plugin-url A
                                        --plugin-url B) (default: [])
  -p, --print                           Print response and exit (useful for
                                        pipes). Note: The workspace trust dialog
                                        is skipped when Claude is run in
                                        non-interactive mode (via -p, or when
                                        stdout is not a TTY, e.g. piped or
                                        redirected output). Only use this in
                                        directories you trust. Settings files
                                        that fail validation are silently
                                        ignored in this mode (no error dialog is
                                        shown).
  --prompt-suggestions [value]          Enable prompt suggestions. In print/SDK
                                        mode, emits a prompt_suggestion message
                                        after each turn with a predicted next
                                        user prompt (choices: "true", "false",
                                        "1", "0", "yes", "no", "on", "off",
                                        preset: "true")
  --remote-control [name]               Start an interactive session with Remote
                                        Control enabled (optionally named)
  --remote-control-session-name-prefix <prefix>
      Prefix for auto-generated Remote Control session names (default: hostname)
  --replay-user-messages                Re-emit user messages from stdin back on
                                        stdout for acknowledgment (only works
                                        with --input-format=stream-json and
                                        --output-format=stream-json)
  --restricted                          Restricted mode: removes the built-in
                                        tools that run commands or code (Bash,
                                        PowerShell and the other code-running
                                        tools) and WebFetch unless --tools names
                                        them, and ignores user, project and
                                        local settings files (managed settings
                                        and --settings still apply; add
                                        --strict-mcp-config to skip MCP servers
                                        too). Also confines the file tools to
                                        the working directories (--add-dir
                                        included), refuses bypassPermissions,
                                        and lets only a person or the configured
                                        permission handler approve writes to
                                        settings, git and tool-configuration
                                        files.
  -r, --resume [value]                  Resume a conversation by session ID or
                                        name, or open interactive picker with
                                        optional search term
  --safe-mode                           Start with all customizations
                                        (CLAUDE.md, skills, installed plugins,
                                        hooks, MCP servers, custom commands and
                                        agents, output styles, workflows, custom
                                        themes, keybindings, and more) disabled
                                        — useful for troubleshooting a broken
                                        configuration. Admin-managed (policy)
                                        settings still apply. Auth, model
                                        selection, built-in tools and plugins,
                                        and permissions work normally. Sets
                                        CLAUDE_CODE_SAFE_MODE=1.
  --session-id <uuid>                   Use a specific session ID for the
                                        conversation (must be a valid UUID)
  --setting-sources <sources>           Comma-separated list of setting sources
                                        to load (user, project, local).
  --settings <file-or-json>             Path to a settings JSON file or a JSON
                                        string to load additional settings from
  --strict-mcp-config                   Only use MCP servers from --mcp-config,
                                        ignoring all other MCP configurations
  --system-prompt <prompt>              System prompt to use for the session
  --system-prompt-snapshot <on|off>     Record the system prompt once per
                                        conversation and reuse it verbatim on
                                        every request and resume. on (the
                                        default, except under --bare outside
                                        cloud and background sessions): the
                                        prompt is rendered on the conversation's
                                        first request — a --system-prompt or
                                        --append-system-prompt included — sent,
                                        and recorded; every later request and
                                        resume sends the record as-is, even when
                                        a later launch passes different text,
                                        until the conversation is compacted.
                                        off: never record; the prompt is
                                        rendered fresh every request (for
                                        iterating on prompt text). (choices:
                                        "on", "off")
  --teleport [session]                  Resume a teleport session, optionally
                                        specify session ID
  --tmux                                Create a tmux session for the worktree
                                        (requires --worktree). Uses iTerm2
                                        native panes when available; use
                                        --tmux=classic for traditional tmux.
  --tools <tools...>                    Specify the list of available tools from
                                        the built-in set. Use "" to disable all
                                        tools, "default" to use the default set,
                                        or specify tool names (e.g.
                                        "Bash,Edit,Read").
  --verbose                             Override verbose mode setting from
                                        config
  -v, --version                         Output the version number
  -w, --worktree [name]                 Create a new git worktree for this
                                        session (optionally specify a name)

Commands:
  agents [options]                      Manage background agents
  attach <id|name>                      Open a background session in this
                                        terminal. <id> is the short id that
                                        `claude --bg` prints and `claude agents`
                                        lists. Part of the session name works
                                        too
  auth                                  Manage authentication
  auto-mode                             Inspect or reset auto mode configuration
  doctor                                Check the health of your Claude Code
                                        installation. Reads settings files in
                                        the current directory without a trust
                                        prompt. For a full checkup that can also
                                        fix issues, run /doctor in a session.
  gateway [options]                     Run the enterprise auth/telemetry
                                        gateway
  import [options] [source]             Import config from another AI coding
                                        agent into Claude Code
  install [options] [target]            Install Claude Code native build. Use
                                        [target] to specify version (stable,
                                        latest, or specific version)
  logs <id|name>                        Print a background session's recent
                                        terminal output
  mcp                                   Configure and manage MCP servers
  plugin|plugins                        Manage Claude Code plugins
  purge [options] [path]                Delete a project's Claude Code data
                                        (transcripts, tasks, file history, and
                                        its entry in ~/.claude.json) from this
                                        computer only
  respawn [options] [id]                Restart a background session, or all of
                                        them with --all, so it runs the current
                                        Claude Code version
  rm <id>                               Delete a background session, and its
                                        worktree when that is safe. Works on
                                        sessions that have already exited
  setup-token                           Set up a long-lived authentication token
                                        (requires Claude subscription)
  stop|kill <id>                        Stop a background session. Its
                                        conversation is kept: `claude attach
                                        <id>` opens it again, `claude --resume`
                                        works once it is stopped
  ultrareview [options] [target]        Run a cloud-hosted multi-agent code
                                        review of the current branch (or a PR
                                        number / base branch) and print the
                                        findings
  update|upgrade                        Check for updates and install if
                                        available; package-manager installs
                                        update through the package manager
```
