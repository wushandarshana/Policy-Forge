# scripts/ — CLAUDE.md

Automation that drives Claude programmatically through the **Claude Agent SDK** (`claude-agent-sdk`, Python).

- `ac_audit.py`: reads `specs/*_spec.md` and test sources, asks a Claude agent (read-only tools) to map every AC-NN
  to tests and flag spec/code drift, and writes `specs/reviews/ac-audit-<date>.md`. Exit code 1 if any AC is
  uncovered, so CI can use it.
- `ac_audit.py`: Scripts must be read-only with respect to production code. Allowed tools: `Read`, `Glob`, `Grep`.
- Requires `ANTHROPIC_API_KEY` (or a logged-in Claude Code). Install with `pip install -r scripts/requirements.txt`.
- `ci/` holds the CI entry points, which `.github/workflows/` and `.gitlab-ci.yml` run in the same way:
  - `quality_gates.py` runs every quality gate and reports each result.
  - `claude_review.py` runs the required Claude Code review.
  - `fetch_tools.py` downloads actionlint and shellcheck at pinned versions and verifies their checksums.
  - `check_line_endings.py` checks hook line endings.

  They use the Python standard library only and run on Python 3.11 or later.
- The CI review runs the pinned Claude Code CLI (from `ci/claude-cli/package-lock.json`) in `--bare` print mode with
  `Read`, `Grep` and `Glob` only, limited to the checkout, and no shell tool (NFR-09.7). It reads the diff and log
  from `ci-out/review/input/`, gets an allowlisted environment, never receives a GitHub or GitLab token, and fails
  closed if any file outside `ci-out/` changes. The PR or MR comment is a separate job that runs base-branch code.
- Tests live in `scripts/tests/` and run with `python -m unittest discover -s scripts/tests -v`. Each test's docstring
  starts with the ID it proves (`NFR-09.4: …`), and `ac_audit.py` counts it.
- `requirements-ci.txt` pins, with hashes, the packages that only the tests and gates need (PyYAML, check-jsonschema).
  Install them first: `pip install --require-hashes -r scripts/requirements-ci.txt` (in `.venv`). Without them the CI
  config tests and the `ci-schema` gate fail closed.
