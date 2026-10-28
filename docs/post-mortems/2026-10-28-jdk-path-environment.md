# Post-mortem: `java` not found after JDK install (environment-first)

**Date:** 2026-10-28 · **Phase:** 0 (foundation) · **Severity:** blocker for backend build · **Status:** resolved

## Symptom
Right after `winget install EclipseAdoptium.Temurin.21.JDK` finished with exit code 0, `java -version` and `./mvnw`
in the running agent session still failed with `java: command not found` / `JAVA_HOME is not set`.

## Environment-first investigation
We checked the environment before touching any build files:

1. **Is it installed?** `ls "/c/Program Files/Eclipse Adoptium/"` showed `jdk-21.0.12.101-hotspot`. Running
   `bin/java -version` by full path printed `openjdk version "21.0.12.1" 2026-08-18 LTS`. The install was fine.
2. **Was the machine environment updated?** `[Environment]::GetEnvironmentVariable('Path','Machine')` contained
   `…\jdk-21.0.12.101-hotspot\bin`, and machine `JAVA_HOME` was set. The installer did its job.
3. **What does the current process see?** `Get-Command java` returned nothing. The agent's shell was started
   **before** the install, so it inherited the old `PATH`, and Windows doesn't push environment changes into running
   processes.

## Root cause
A stale process environment, not a project or tool defect.

## Resolution
- Within the running session, prefix build commands with the machine values:
  `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`.
- For later sessions, restart the terminal or Claude Code so the new `PATH` is picked up.

## Prevention (knowledge deposit)
- Added to root `CLAUDE.md` § "Debugging: environment first": check the JDK version and `JAVA_HOME` first.
- `docs/knowledge-deposits.md` entry KD-001.
- The Maven wrapper (`./mvnw`) is committed so no global Maven is needed. Only a JDK is.

## Related environment finding
The capstone PDF couldn't be moved (`Device or resource busy`) because it was open in a viewer. We left it in place
and gitignored `*.pdf` instead of retrying, which also keeps course material out of the repo.

## Follow-up finding: hooks failing open (`python3` is a Store stub)
- **Symptom:** every guardrail hook exited 49 with "Python was not found; run without arguments to install from the
  Microsoft Store". Claude Code treats a non-2 exit as a non-blocking error, so the guardrails were **silently off**.
- **Environment check:** `command -v python3` gave `…/AppData/Local/Microsoft/WindowsApps/python3` (an App Execution
  Alias stub). `command -v python` gave `C:\Python314\python.exe` (real).
- **Fix:** the wrappers now probe `python`, `python3` and `py`, and use the first one that executes `import sys`. If none
  works, they exit 2, so the hook fails closed with a clear message.
- **Verification:** each hook was exercised with violating and clean payloads (BigDecimal from a double literal, PII
  in `log.info`, setter on an append-only entity, JpaRepository on an append-only repository, `git commit` on main).
  All violations exit 2, and clean payloads exit 0.
