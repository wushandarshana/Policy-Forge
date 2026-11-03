# TASK-006 Progress

## Status
DONE

## What was built
The app shell (`frontend/src/App.tsx`) now mounts the Quote page (default route, public) and, behind the `RequireSession` sign-in gate, Apply (`#/apply?quoteId=...`), Application Status (`#/applications/<id>`) and Workbench (`#/workbench`). `SessionProvider` sits above the hash router, so navigation keeps the in-memory session. The Quote page's Apply link already pointed at `#/apply?quoteId=...`. A successful submission sets `#/applications/<applicationId>`. A nav bar (`nav-quote`, `nav-workbench`, `nav-user`, `nav-signout`) reaches the Workbench. The red stage (stub shell, 3 Vitest and 14 Playwright failures on assertions, `quote.spec` passing) was committed first as `8cb4242 test(red)`. The green stage is `b941f95 feat(green)`. Nothing was pushed.

SPA/API mismatch changes (criterion 3): none were needed. The pages from TASK-002 already follow U-rules and C1-C5, and all 16 Playwright runs pass against the real API. Changes made beyond the shell:
- `playwright.config.ts`: `snapshotPathTemplate` is `{testDir}/__snapshots__/{testFileName}-{arg}-{projectName}-{platform}{ext}` (flat files). The first attempt with a per-spec directory (`apply-underwrite.spec.ts/`) made `scripts/ac_audit.py` crash with PermissionError, because it reads every path matching `*.spec.ts` as a file. Flat names avoid editing the script.
- `styles.css`: appended `.nav` rules.

## Commands run and results
Environment: this shell had no `JAVA_HOME`, so the webServer failed ("JAVA_HOME ... not defined correctly"). I exported the machine's `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` and added its bin to PATH. The earlier "worker process exited unexpectedly" crashes were not seen again.
- Red: Vitest 3 failed / 35 passed; Playwright desktop 7 failed / 1 passed (`quote.spec`), mobile 7 failed / 1 passed. All fail on missing `signin-username` or `nav-workbench`. Log: `sprint-progress/TASK-006-red.log`.
- `npm test` (vitest run --coverage): 8 files, 38 tests passed. Statements 95.05%, branches 86.51%. `coverage/lcov.info` refreshed and committed.
- `npm run build` (tsc --noEmit and vite build): passes.
- `./mvnw verify` (SPA packaged, frontend not skipped: npm-ci and npm-build ran): 251 unit and 212 integration tests, 0 failures, 0 skipped. JaCoCo `check-overall` (70%) and `check-domain` (80%) passed, thresholds unchanged. BUILD SUCCESS.
- Playwright, both projects in parallel (11 workers), first run with `--update-snapshots=missing`: 16 passed, 6 baselines written. Second consecutive run, no update flag: 16 passed. Log: `sprint-progress/TASK-006-green.log`.
- `python scripts/ac_audit.py --offline --no-write`: Covered 47/78, parent-only 0. The only NOT COVERED IDs are AC-05, 06, 07, 08, 10 and 11 (other sprints). AC-01, 02, 03, 04 and 09 are all covered, so every Sprint 2 ID and every Sprint 1 ID is COVERED. The audit lists no NFR-03.1/04.1/04.3/04.4 as uncovered.
- Red-before-green in `git log 7303fde..HEAD`: AC-04.1-04.6 `cd638af` then `831edd4`; AC-03.x, AC-09.x frontend `8e77617` then `ac9e7ce`; AC-03.x, AC-04.x, NFR-03.1, NFR-04.1/04.3/04.4 backend `4632739` then `3001b7b`; AC-09.1-09.7 and NFR ids `a985d4a` then `1a77eb4`; TASK-006 ids `8cb4242` then `b941f95`.
- Git: `dev` = `ddeae97` and `main` = `8e265d6`, locally and as `origin/dev` and `origin/main` (also confirmed by `git ls-remote origin`). `origin/feat/application-underwriting` does not exist (`ls-remote --heads` returned 0 lines). Working tree clean.
- `python scripts/redact_evidence.py --check .sprint/20261028-114922/sprint-progress`: total 0, exit 0.
- Port 8080: no listener after the runs.

## Files changed
- frontend/src/App.tsx (routing shell, nav, sign-in gate)
- frontend/src/App.test.tsx (AC-03.1 route test, AC-03.8, AC-09.1, AC-01.1)
- frontend/src/main.tsx (mounts App)
- frontend/src/styles.css (nav styles)
- frontend/playwright.config.ts (snapshotPathTemplate)
- frontend/e2e/apply-underwrite.spec.ts, frontend/e2e/workbench.spec.ts, frontend/e2e/support.ts
- frontend/e2e/__snapshots__/*.png (6 baselines: apply form, application status, workbench case detail, each on desktop-chromium and mobile-chromium, win32)
- frontend/coverage/lcov.info
- .sprint/20261028-114922/sprint-progress/TASK-006-red.log, TASK-006-green.log (old TASK-006-red-*.log files removed)

## Open issues
- Baselines are Windows (`-win32`) only. Linux CI baselines are due in Sprint 5 (spec UI section).
- Playwright's `webServer` needs `JAVA_HOME` in the invoking shell. It is set at machine level but not in this agent shell.
- `git` prints LF-to-CRLF warnings on commit. This is cosmetic. The `lcov.info` diff is large for that reason.
- The nav bar was not in TASK-002's contracts. `nav-signout` signs out but does not clear the hash.
