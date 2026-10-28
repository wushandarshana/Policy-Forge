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
