# TASK-002 Progress

## Status
DONE

## What was built
Typed API client and types for C2-C6 (payRenewal, previewCancellation, cancelPolicy, getDashboard; PolicyTerm.ruleVersion, PendingRenewal, Refund, RefundQuote, Dashboard; `ApiError.code` maps the invalid-policy-state `type`). Policy Detail gains a RenewPanel (only in force with a non-null `pendingRenewal`; Pay posts `{}`) and a CancelPanel (in-force only; optional date, required reason from the four G-4 values; preview then confirm, Confirm enabled only for a preview of the current inputs, body `{reason}` or `{reason, cancellationDate}`), both reloading the detail on success, plus a populated Refunds tab. New AdminDashboardPage (As-of input, KPI tiles, per-product table, renewal pipeline, lapse forecast) is not mounted in the app shell (TASK-007). Fixed messages for every C7 code, 409 and 403; CSS uses the single 640px breakpoint with KPI tiles one column below it.

TDD: `ea2d9d7 test(red)` (40 failing, 5 trivially passing) then `c95a016 feat(green)`; logs `TASK-002-red.log` and `TASK-002-green.log` (145/145 passing, names AC-07.1, 07.5, 08.1-08.4, 10.3, 11.1-11.5). `npx tsc --noEmit`, `npm test` (lcov refreshed and committed) and `npm run build` are green. No money arithmetic (`noMoneyArithmetic.test.ts` passes); no npm dependencies added; nothing outside `frontend/` touched or committed; no spec files committed.

## Files changed
- frontend/src/api/types.ts, http.ts, index.ts, policies.ts, admin.ts
- frontend/src/messages.ts, styles.css
- frontend/src/components/RenewPanel.tsx, CancelPanel.tsx
- frontend/src/pages/PolicyDetailPage.tsx, AdminDashboardPage.tsx
- frontend/src/test/helpers.tsx (fixtures for the C5 fields: ruleVersion, pendingRenewal, refunds, refundQuote, dashboard)
- frontend/src/pages/PolicyDetailPage.renew.test.tsx, PolicyDetailPage.cancel.test.tsx, AdminDashboardPage.test.tsx
- frontend/coverage/lcov.info

## Open issues
- Contract choices for TASK-007 E2E: the cooling-off flag renders as the literal `true`/`false`; days render as `<unexpiredDays> / <termDays>`; Pay button is `renew-pay`; extra test IDs `renew-rule-version`, `policy-notice`, `cancel-date-error`, `cancel-reason-error`, `dashboard-asof-error`, `dashboard-as-of`, `forecast-empty`, `pipeline-empty`, `refund-<termNo>-date|premium|fee|days|cooling-off|formula`.
- The reason is required for both Preview and Confirm (a missing reason shows a required message at the select), and changing the date or reason clears the preview, so E2E must choose the reason before Preview.
- The dashboard loads once on mount (no asOf) and reloads via `dashboard-load`; the page needs a signed-in session, which TASK-007's route supplies via RequireSession.
- No 375px layout assertion is possible in jsdom; horizontal-scroll and snapshot checks belong to TASK-007's Playwright projects.
- `.sprint/` is git-ignored, so the red/green logs live in the workspace only.
