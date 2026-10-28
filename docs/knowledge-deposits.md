# Knowledge Deposits

Recurring mistakes and lessons, and where each one was encoded so the next agent can't repeat it.
This file is append-only: add new entries at the bottom.

| ID | Date | Mistake / lesson | Encoded into |
|---|---|---|---|
| KD-001 | 2026-10-28 | After installing a JDK, the running session keeps the old `PATH`, so `java` isn't found. Check the environment before changing the build | Root `CLAUDE.md` "Debugging: environment first"; `docs/post-mortems/2026-10-28-jdk-path-environment.md` |
| KD-002 | 2026-10-28 | Hook scripts edited on Windows get CRLF line endings and fail under bash with `$'\r': command not found` | `.gitattributes` (`*.sh eol=lf`); `.claude/hooks/CLAUDE.md` |
| KD-003 | 2026-10-28 | Spec worked examples must be computed, not estimated. All money examples were re-verified with exact decimals before commit | `specs/CLAUDE.md`; `premium-calc-evaluator` skill |
| KD-004 | 2026-10-28 | UW-TL-005 (BMI > 45) can't fire without UW-TL-004 (BMI > 35). "One rule fires alone" matrices must allow nested thresholds | `docs/tdd.md` worked example; `spec-to-test-generator` skill |
| KD-005 | 2026-10-28 | On Windows, `python3` resolves to the Microsoft Store stub (exit 49), so hooks silently failed open. Hook wrappers must probe for an interpreter that actually runs, and hooks must be tested with a violating payload, not only assumed to work | `.claude/hooks/*.sh` interpreter probe; `.claude/hooks/CLAUDE.md`; post-mortem 2026-10-28 |
| KD-006 | 2026-10-28 | `git rev-parse --abbrev-ref HEAD` prints `HEAD` before the first commit, which disabled the main-branch guard in fresh repos. Use `git branch --show-current` | `.claude/hooks/lib/no_direct_main_commit.py` |
| KD-007 | 2026-10-28 | CSRF was disabled on the grounds that "Basic auth has no cookies". Browsers cache Basic credentials and attach them cross-site, and a `text/plain` form POST to a `@RequestBody String` endpoint needs no preflight (security review S1 F-1) | `web/CLAUDE.md` (consumes JSON rule); NFR-04.3 in `specs/app_spec.md` |
| KD-008 | 2026-10-28 | The public quote read removed `bmi`/`healthDeclarations` from a client-supplied map (a deny-list), so any other key a caller submitted leaked (S1 F-2) | `web/CLAUDE.md` (allow-list rule); NFR-04.2 allow-list wording; AC-01.9 |
| KD-009 | 2026-10-28 | The security reviewer ran `./mvnw test -Dtest=*Security*`, which matched nothing because the security tests are `*IT` and run under failsafe. To run ITs selectively: `./mvnw verify -Dit.test=<Pattern> -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false` | `.claude/agents/security-reviewer.md` (commands section) |
| KD-010 | 2026-10-28 | The body-size filter matched the raw request URI while Spring routes on the decoded path, so `POST /api/%71uotes` bypassed the cap (re-review R-1). Security checks must use the same path normalization as routing, and limits should default-deny (apply to everything unless explicitly relaxed) instead of opting specific routes in | NFR-04.4; `web/CLAUDE.md` |
| KD-011 | 2026-10-28 | A synthetic Basic header reappeared in a new evidence log after the archive was redacted by hand (re-review R-3). Redaction must be a tool, not a one-off | `scripts/redact_evidence.py` (`--check` mode); `docs/fix-loops/README.md`; `/sprint-archive` step |
