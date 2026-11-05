# TASK-003 Progress

## Status
DONE

## What was built
Frontend and E2E hardening for NFR-09.5, all under `frontend/` plus the lcov lines of `.gitignore`. No substrate was committed, and nothing under `backend/` changed. Amendments A (`4730168`) and B (`f9a1664`) were present in `d9a5694..HEAD`, and TASK-001 and TASK-002 were DONE.

- **lcov untracked** (`9b434d1 chore:`). `git rm --cached frontend/coverage/lcov.info`, and the two `.gitignore` exception lines became `frontend/coverage/`. `git ls-files frontend/coverage` is empty, and `npm test` and `npm run build` leave `git status` clean.
- **Exact Playwright pin** (`8647f54 chore:`). `"@playwright/test": "1.64.1"` in `package.json`. The lock diff is one line, the root dependency specifier, and `npm ls` still resolves 1.64.1.
- **NFR-09.5 policy** (red `6d26416`, green `59420aa`).
  - `frontend/e2e/visual-baseline-policy.ts` holds the pure C7 function `decideVisualBaseline({ mode, baselineExists, platformHasBaselines })` with no Playwright imports. It returns `assert`, `skip` or `error`. An unset mode is `auto`, `require` and `update` always assert, and any other value (including an empty string or a different case) is an error that names `PF_VISUAL_BASELINES` and the allowed values.
  - The Vitest table test is `frontend/src/visualBaselinePolicy.test.ts` (25 tests, every title starts with `NFR-09.5:`). It sits under `src/` because the Vitest `include` is `src/**` and Playwright's default `testMatch` would pick up a `*.test.ts` inside `e2e/`.
  - Red: the stub always asserts, so 11 of 25 tests fail on assertions (the skip rows and every error row). The stub compiles (`tsc --noEmit` exit 0).
- **Helper** `expectVisual(locator, name, options)` in `frontend/e2e/support.ts`. It resolves the snapshot path with `test.info().snapshotPath(name)` and asks the policy. On a skip it pushes the `visual-baseline-missing` annotation, writes the console warning `[visual-baseline-missing] <file>`, appends the file name to `<project outputDir>/visual-baselines-missing.txt` (`frontend/test-results/`) and returns without throwing. A configuration error throws.
- **All 10 `toHaveScreenshot` calls** in the four specs now call `expectVisual` with the same names and options (`mask`, `stylePath`, `animations`). `grep toHaveScreenshot frontend/e2e/*.spec.ts` finds nothing, and only `support.ts` calls it.
- **`PF_E2E_JAR`** in `playwright.config.ts` (C8). When set (absolute, or relative to `frontend/`), `webServer.command` is `java -jar "<resolved>" --policyforge.jobs.renewal-cycle.any-date=true`. URL, cwd, timeout and `reuseExistingServer` are unchanged, and the two Maven commands are unchanged when it is unset. The config also has a comment on the update mode.

## Verification (details in `TASK-003-red.log` and `TASK-003-green.log`)
- `npm test`: 17 files, 173 tests pass (`noMoneyArithmetic` and the 25 NFR-09.5 tests included). `npm run build`: `tsc --noEmit` and `vite build` pass.
- **Playwright run 1** (default `webServer`, `mvnw.cmd spring-boot:run`, both projects): 44 passed, `visual-baselines-missing.txt` absent, no PNG changed.
- **JAR**: `./mvnw -B -DskipTests package` BUILD SUCCESS (16 s), SPA included.
- **Playwright with `PF_E2E_JAR`**: run 2 failed once (see Open issues). Runs 3 and 4, the same command, were 44 passed each, with no PNG created or changed and `visual-baselines-missing.txt` absent. So the criterion "twice with everything passing" is met by runs 3 and 4, and run 2 is recorded as found.
- **Demonstration 1** (all 20 `-win32` baselines moved out, `workbench.spec.ts`, `PF_E2E_JAR`): 8 passed. The two `workbench-case-detail` assertions were skipped, `visual-baselines-missing.txt` lists `workbench.spec.ts-workbench-case-detail-{desktop,mobile}-chromium-win32.png`, the warnings were printed, and the functional assertions after them ran. Baselines restored, `git status` clean.
- **Demonstration 2** (only the two `workbench-case-detail` baselines moved out, 18 remain): `AC-09.2` fails on both projects with "A snapshot doesn't exist at ...workbench-case-detail-...-win32.png, writing actual", the other 6 pass, and no missing-baseline file is written. Playwright wrote the actual image at the baseline path (its default `missing` mode), so I deleted those two files, moved the originals back and confirmed `git status` is clean.
- Port 8080 is free (`netstat` shows no LISTENING socket) and no Java process is running.

## Files changed
- .gitignore (lcov lines only)
- frontend/coverage/lcov.info (untracked, file kept on disk and ignored)
- frontend/package.json
- frontend/package-lock.json
- frontend/e2e/visual-baseline-policy.ts
- frontend/e2e/support.ts
- frontend/e2e/apply-underwrite.spec.ts
- frontend/e2e/dashboard.spec.ts
- frontend/e2e/policy-lifecycle.spec.ts
- frontend/e2e/workbench.spec.ts
- frontend/playwright.config.ts
- frontend/src/visualBaselinePolicy.test.ts
- (not committed, in the ignored workspace) .sprint/20261104-075010/sprint-progress/TASK-003.md, TASK-003-red.log, TASK-003-green.log

Commits on `chore/ci-hardening` (explicit paths, no push): `9b434d1` chore lcov, `8647f54` chore pin, `6d26416` test(red) NFR-09.5, `59420aa` feat(green) NFR-09.5.

## Open issues
- **Intermittent 1 px visual flake (pre-existing, not caused by the helper).** In run 2 (the first start of a fresh JAR, so the first policy was `PF-TL-2026-000008`), `AC-05.1` on `desktop-chromium` failed with "Expected an image 410px by 289px, received 410px by 290px" for `my-policies-card.png`. The two images look identical, so it is a sub-pixel layout rounding of the card height. `maxDiffPixelRatio` does not help because the size check comes first. It passed in runs 1, 3 and 4, on the mobile project every time, and in all runs of the other 9 snapshots. CI sets `retries: 1`, which would absorb it, but TASK-005 and the evaluator should know it can happen. A cure (a fixed height for the card, or masking) would change app or baseline files, so I left it. This is a possible follow-up for the orchestrator.
- **Stale assets in the packaged JAR.** `./mvnw package` does not clean `target/classes/static`, so the local JAR holds three old `assets/index-*.js` bundles next to the current one. This is only local and harmless (`index.html` names the current one). CI builds from a clean checkout.
- **`ac_audit` and the Vitest file.** The NFR-09.5 test is in `frontend/src/` (counted by `frontend/src/**/*.test.ts`), so `NFR-09.5` is COVERED once the audit runs. I did not run the audit here (TASK-005 does).
- **No type-check of `e2e/`.** `tsconfig.json` includes only `src` and `vite.config.ts`, and `@types/node` is not installed, so `e2e/` is not type-checked by `npm run build`. A temporary check (not committed) of `e2e/` and the config showed no error besides the ones caused by the missing node types.
- **Edge case recorded.** An empty `PF_VISUAL_BASELINES=` is a configuration error by C7 ("any other value"). A CI file must not set the variable to an empty string, and must leave it unset for auto mode.
- **Commit mechanics.** `git commit -- <path>` re-adds a path that was removed from the index with `git rm --cached` when the file still exists, so the first lcov commit did not delete it. I amended my own commit (`9b434d1`, no other task's) after repeating `git rm --cached` and committing without a pathspec. Nothing else was amended.
