# TASK-007 Progress

## Status
DONE

## What was built
The Admin Dashboard route `#/admin/dashboard` (behind `RequireSession`) with the `nav-dashboard` link (`aria-current` on the route), the Playwright journeys for renewal, cancellation and the dashboard on both projects, eight committed visual baselines, and the sprint-wide verification. No spec file, rule file or migration was touched. Commit `7d95f67` (the spec amendment, G-6 vetoed) is in `12f95c3..HEAD` and is the oldest commit of the sprint, so it precedes every code commit. Nothing was pushed.

- **Routing (AC-11.1, AC-11.5).** `App.tsx` gains the `dashboard` route and the nav link. Three new Vitest tests in `App.test.tsx`: the route behind the sign-in gate (no dashboard call before sign-in, then `kpi-tiles` and the admin Authorization header), the nav link reaching it and marking it current, and a 403 showing the fixed not-authorised message. All 11 existing routing tests pass unchanged.
- **Playwright (`policy-lifecycle.spec.ts`).** AC-07.1 (job on own expiry minus 30 lists the policy in `offersCreated`; Renew panel start = expiry + 1 and 6850.00; snapshot), AC-07.5 (Pay shows RENEWED, hides the Renew panel, two transitions with the last `RENEWED` / `RENEWAL_PAID`, Versions tab shows v2 at 6850.00), AC-08.2 (same-day cancel previews 6850.00 / 0.00 / cooling-off `true`, confirms to CANCELLED, one `refund-row` of 6850.00; snapshots of the preview and of the Refunds tab) and AC-08.4 (cancel through the API gets 201, no Cancel, Renew or Endorse action, a second cancel gets 409, the Refunds tab keeps one row after the detail is loaded again through hash navigation).
- **Playwright (`dashboard.spec.ts`, new).** AC-11.1 (tiles and per-product table equal the page's own `/api/admin/dashboard` response, tiles one column on mobile and side by side on desktop by bounding boxes, no horizontal scroll, KPI tile snapshot), AC-11.3 (asOf = term end - 20 lists the policy, - 90 does not), AC-11.4 (asOf = term end + 1 lists it in the forecast), AC-11.5 (customer1 sees the fixed message and no figures).
- **Support (`support.ts`).** `shiftDate`, `policyExpiry`, `runRenewalCycle` and `withRenewalLock`. P-12 alone was not enough for a deterministic AC-07.1: a cycle run offers a renewal to every in-force policy whose offer window has opened, and policies issued on the same day share one expiry, so another test's job (desktop and mobile run in separate workers) can offer this test's policy first and make its `offersCreated` miss it. The two renewal tests therefore create their policy and run the job inside a cross-process lock (a directory in the OS temp folder, taken through `expect.poll`, released in `finally`, stale after 120 s). Every other test runs unlocked. The job is still called only with date = own expiry - 30.
- **Snapshot mask.** `snapshot-mask.css` additionally hides `renew-start`, `renew-end`, `renew-payable-until`, `refund-preview-date`, `refund-preview-days`, `refund-*-date`, `refund-*-days` and `.kpi-value` (day counts depend on a 365 or 366 day term). No Sprint 1-3 element uses these, and their baselines are untouched.
- **Baselines.** `renew-panel-offer`, `cancel-refund-preview`, `refunds-tab` (in `policy-lifecycle.spec.ts-...`) and `dashboard-kpi-tiles` (in `dashboard.spec.ts-...`), each for `desktop-chromium` and `mobile-chromium`, `win32`, 8 PNGs under `frontend/e2e/__snapshots__/`. Created with `--update-snapshots` limited by `--grep` to the new tests, so no existing baseline changed (`git status` showed no modified PNG). I opened the Refunds-tab and KPI-tile images to confirm the masks hide only dates, day counts and figures.

## SPA and API mismatches resolved (criterion 2)
No contract (C1 to C10) or spec mismatch was found: the SPA's calls, test IDs and messages matched the real endpoints in every journey. One layout change was needed, not a contract change:
- **Renew and Cancel panels moved below the history tabs** in `PolicyDetailPage.tsx`. With the Cancel panel above the tabs, the Sprint 3 element baseline `policy-lifecycle.spec.ts-policy-versions-tab-*` failed on both projects by one pixel of height (474 vs 475 desktop, 695 vs 694 mobile). The cause is sub-pixel position: the tab panel's y moved to a different fraction, which changes the screenshot's rounded size (the bounding box read 782.484375). A renewal offer on the same page (for example one made by another test's cycle run) would shift it the same way. Putting both action panels after the tab panel restores the exact Sprint 3 geometry and removes the race. All 131 page tests still pass, so TASK-002's tests do not depend on the order.

## Commands run and results
All in the foreground, from `C:\Users\wdarshana\Downloads\PolicyForge`, JDK 21.0.12 on `PATH`.
- `git branch --show-current` before each commit: `feat/renewal-cancellation`.
- Red: `npx vitest run src/App.test.tsx` gave 3 failed, 11 passed (the three dashboard routing tests). `npx playwright test e2e/dashboard.spec.ts e2e/policy-lifecycle.spec.ts --grep "AC-07.1|AC-07.5|AC-08.2|AC-08.4|AC-11"` on both projects gave 12 failed, 4 passed: the 8 dashboard tests time out on the missing route, and AC-07.1 and AC-08.2 pass every functional assertion and fail only at `toHaveScreenshot` for a missing baseline. AC-07.5 and AC-08.4 test behaviour that TASK-002, TASK-004 and TASK-005 already delivered and pass in red, on both projects. The baselines the red run wrote were deleted before the red commit.
- `f61f067 test(red)` (5 files, no snapshots), then green.
- Green: `npx tsc --noEmit` clean; `npx vitest run src/App.test.tsx` 14/14; `npx vitest run src/pages` 131/131.
- Playwright with `--update-snapshots` limited to AC-07.1, AC-08.2 and AC-11.1: 6 passed, 8 PNGs written. Run twice because the panel move changed the layout (first set discarded and regenerated).
- Playwright, whole suite, both projects in parallel, no update: run 1 `44 passed (34.1s)`, run 2 `44 passed (39.4s)`. Each run started its own app (in-memory H2). Before the panel move the same suite failed AC-06.1 on both projects (the Sprint 3 snapshot), as described above.
- `npm test`: 16 files, 148 tests passed, `coverage/lcov.info` refreshed and committed. `npm run build`: `tsc --noEmit` and `vite build` succeeded.
- `./mvnw verify` in `backend/` (frontend not skipped: `npm ci` and `npm run build` ran, SPA copied to `target/classes/static`): 479 unit and 550 integration tests, 0 failures, 0 errors, 0 skipped; both `jacoco:check` executions "All coverage checks have been met"; BUILD SUCCESS in 3:10 min. `backend/pom.xml` is unchanged since `12f95c3` (thresholds 0.80 domain, 0.70 overall).
- `python scripts/ac_audit.py --offline --no-write`: `Covered 81 / 81 ACs (100%)`, parent-only 0, not covered 0. A grep also found a sub-criterion-named test (`DisplayName("AC-NN.M` / `@Tag` / `test('AC-NN.M`) for each of AC-07.1 to 07.6, AC-08.1 to 08.5, AC-10.2 to 10.4, AC-11.1 to 11.5, NFR-03.1, NFR-04.1, NFR-04.3 and NFR-04.4.
- Git history check over `git log --reverse 12f95c3..HEAD`: the spec commit is index 0, the first code commit is index 1, and a script found for every one of those 23 IDs a `test(red)` commit naming it before the first `feat(green)` naming it (no violations).
- `git rev-parse dev main` = `12f95c3...` and `8e265d6...`; `git ls-remote origin` gives `refs/heads/dev` = `12f95c3...`, `HEAD` (main) = `8e265d6...`, and no `refs/heads/feat/renewal-cancellation`; `git branch -a` shows no `origin/feat/renewal-cancellation`. Nothing was pushed.
- `python scripts/redact_evidence.py --check .sprint/20261103-223107/sprint-progress`: `total: 0`, exit 0.
- Port 8080: `netstat` found no listener before the verify and after the last run (the Playwright `webServer` stops with each run).
- `git status` is clean after the green commit.

## Commits
- `f61f067` test(red): AC-07.1 AC-07.5 AC-08.2 AC-08.4 AC-11.1 AC-11.3 AC-11.4 AC-11.5 (Vitest routing tests, Playwright journeys with snapshot assertions, helpers, mask)
- `b29c511` feat(green): same IDs (route, nav link, panel order, `lcov.info`, 8 baselines)
- Logs (workspace only, `.sprint/` is git-ignored): `sprint-progress/TASK-007-red.log` (Vitest and Playwright, both projects) and `TASK-007-green.log` (Vitest, both Playwright runs listing all 44 tests per run, and the verify summary).

## Spec gaps (recorded, not implemented)
- **SG-1** Sum-insured endorsements do not re-run underwriting. G-6 was vetoed: this is an accepted v1 limitation (the amendment text in `7d95f67`). No AC-06.8 and no `SUM_INSURED_REQUIRES_UNDERWRITING` code exist.
- **SG-2** The cancellation date may be any date in the current paid term, including past dates, so a backdated cancellation gives a larger refund.
- **SG-3** Renewal re-rates the stored inputs unchanged. Ages are not advanced and underwriting is not re-run.
- **SG-4** The admin job accepts any date, including future ones, and lapses are terminal. My Playwright journeys call it with a date about 11 months ahead (own expiry - 30), which lapses the seed policies and any older policy on that server. The app uses in-memory H2 by default, so a restart restores the seed.
- **SG-5** Between an early renewal payment and the renewal term's start, endorsement and cancellation dates must lie in the renewal term (G-5 default).
- **SG-6** `premiumCollected` and the active count are taken at request time, not cut at `asOf`, so a historic `asOf` shows today's collected figure.
- **SG-7** A policy whose inputs the active rule version rejects gets no offer (logged by number). The lapse forecast lists any in-force policy past its current term, with or without an offer, so such a policy is in the forecast but never lapses.

## Open issues
- **Sprint 3 baseline sensitivity.** Sprint 3 baselines are element screenshots of boxes at fractional positions, so anything inserted above them can change their rounded size by a pixel. The two new action panels now sit below the tabs for that reason. A future change above the tabs (a banner in the header, for example) would need the same care or a regenerated baseline.
- **`policy-notice` banner.** It appears above the policy header after a pay or cancel. The AC-08.2 Refunds-tab baseline was taken with it present (the real flow). It is deterministic, but it moves that tab's position relative to the Sprint 3 shots, which do not show it.
- **Renewal lock is test infrastructure.** `withRenewalLock` uses `%TEMP%/policyforge-e2e-renewal.lock`. A run killed while holding it leaves a lock that the next run takes over after 120 s (the poll waits up to 120 s). CI with several machines does not share it, which is fine because each has its own app instance.
- **AC-07.5 and AC-08.4 had no red failure.** The behaviour they check was delivered by earlier tasks and their E2E tests passed on first run. The red commit still precedes the green commit, and the routing and dashboard tests failed first, as the plan asks.
- **No 375px assertion in the lifecycle snapshots beyond `expectNoHorizontalScroll`.** The mobile project is the iPhone 13 viewport (390px wide).
- **Substrate not touched (human edits).** `frontend/e2e/CLAUDE.md` could mention the renewal lock and the sub-pixel note, and `web/CLAUDE.md` could list the dashboard endpoint.
- **Commit trailer.** These commits end with `Co-Authored-By: Claude Opus 5.5`, as the task text instructs.
- `git` prints LF to CRLF warnings on commit (autocrlf); the files are not affected.

## Files changed
- frontend/src/App.tsx, frontend/src/App.test.tsx
- frontend/src/pages/PolicyDetailPage.tsx
- frontend/e2e/policy-lifecycle.spec.ts, frontend/e2e/dashboard.spec.ts (new), frontend/e2e/support.ts, frontend/e2e/snapshot-mask.css
- frontend/e2e/__snapshots__/dashboard.spec.ts-dashboard-kpi-tiles-{desktop,mobile}-chromium-win32.png (new)
- frontend/e2e/__snapshots__/policy-lifecycle.spec.ts-{renew-panel-offer,cancel-refund-preview,refunds-tab}-{desktop,mobile}-chromium-win32.png (new)
- frontend/coverage/lcov.info
