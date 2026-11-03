# TASK-002 Progress

## Status
DONE

## What was built
An in-memory Basic-auth session (`SessionProvider`, `useSession`, `SignInForm`, `RequireSession`), a typed C1-C5 API client, and three pages: Apply, Application Status and Workbench. The API helper `requestProtected` is the only code that attaches `Authorization: Basic`, so the public quote and product calls never carry it. Reason codes are offered as U-10 pairs them, and Override renders only after `includeDeclined=true` returns 200. TDD order was followed: `8e77617 test(red)` (23 new tests failing on assertions against render-nothing stubs), then `ac9e7ce feat(green)` (34/34 Vitest pass), then `53a2944 refactor`. Nothing was pushed.

Commands run and results:
- `npm test` (red): 23 failed, 11 passed. Log: `sprint-progress/TASK-002-red.log`
- `npm test` (green and after refactor): 7 files, 34 tests passed, lcov refreshed and committed. Log: `sprint-progress/TASK-002-green.log`. Iteration 2 regenerated this log with `vitest run --coverage --reporter=verbose` (ANSI codes stripped), so it lists every test by name. It now names AC-03.1, 03.2, 03.3, 03.4, 03.6, 03.7, 03.8, 04.1, 04.3, 09.1, 09.2, 09.3, 09.4, 09.5, 09.6 and 09.7. No code or commit changed in iteration 2 (frontend tree is clean)
- `npm run build` (tsc --noEmit and vite build): passes with no TypeScript errors
- `python scripts/redact_evidence.py --check` on both logs: total 0

## Contracts for TASK-006 (mounting and E2E)
Components and props:
- `SessionProvider` (prop `initialCredentials?`), `RequireSession` (the sign-in gate), `SignInForm`, in `src/auth/`
- `ApplyPage({ quoteId, onSubmitted?(applicationId) })`. TASK-006 should pass `onSubmitted` to navigate to `#/applications/<id>`. Without it, the page shows `apply-success` with `apply-success-link`.
- `ApplicationStatusPage({ applicationId })`
- `WorkbenchPage()`: takes no props and reads credentials from the session

Test IDs:
- Sign-in: `signin-form`, `signin-username`, `signin-password`, `signin-submit`, `signin-error`
- Apply: `apply-form`, `input-{fullName|dateOfBirth|aadhaar|pan|line1|city|postcode}`, `field-<name>`, `error-<name>`, `submit-application`, `apply-error` (with `data-code`), `form-errors`
- Status: `status-badge` (with `data-status`), `application-id`, `application-decision`, `application-rule-version`, `application-product`, `application-submitted-at`, `application-quote-id`, `reason-list`, `reason-item-<code>`, `application-not-found`, `application-error`, `application-loading`
- Workbench queue: `queue-table`, `queue-row-<applicationId>` (with `data-status`), `select-<applicationId>` (the "Review" button), `row-status-<applicationId>`, `queue-refresh`, `queue-empty`, `queue-error`, `queue-loading`, `decision-result`
- Workbench case panel: `case-detail`, `case-status`, `case-product`, `case-decision`, `case-rule-version`, `case-fullName`, `case-aadhaar`, `case-pan`, `case-risk-<inputName>`, `case-risk-inputs`, `reason-item-<code>`
- Workbench decision form: `decision-form`, `decision-select` (values `""`, `APPROVE`, `DECLINE`), `reason-select`, `reason-option-<code>`, `comment-input`, `submit-decision`, `error-decision`, `error-reasonCode`, `error-comment`, `decision-error`
- Workbench override form: `override-form`, `override-reason-select`, `override-comment-input`, `override-action` (the submit button), `override-error-reasonCode`, `override-error-comment`, `override-error`

Behaviour notes for E2E:
- The Workbench shows no case detail until `select-<id>` is clicked.
- After a successful decision or override, the case panel closes, the queue reloads and `decision-result` shows the new status.
- The placeholder options of the select elements have value `""`. Assert offered codes by filtering those out.
- Credentials are in memory only, so a page reload (or a hash navigation that remounts the provider) signs the user out. The `SessionProvider` must sit above the router in the shell so in-app hash navigation keeps the session.

## Files changed
- frontend/src/api/types.ts (added `code` to `ProblemDetail`; added C1-C5 and `Credentials` types)
- frontend/src/api/http.ts (`basicAuthHeader`, `requestProtected`, `postProtected`)
- frontend/src/api/applications.ts
- frontend/src/api/underwriting.ts
- frontend/src/api/index.ts
- frontend/src/reasonCodes.ts
- frontend/src/messages.ts
- frontend/src/auth/SessionContext.tsx
- frontend/src/auth/SignInForm.tsx
- frontend/src/auth/RequireSession.tsx
- frontend/src/components/Field.tsx
- frontend/src/components/StatusBadge.tsx
- frontend/src/components/ReasonList.tsx
- frontend/src/components/CaseActionForm.tsx
- frontend/src/pages/ApplyPage.tsx
- frontend/src/pages/ApplicationStatusPage.tsx
- frontend/src/pages/WorkbenchPage.tsx
- frontend/src/styles.css (appended; the single 640px breakpoint, queue table stacks into cards below it)
- frontend/src/test/helpers.tsx
- frontend/src/auth/SessionContext.test.tsx
- frontend/src/pages/ApplyPage.test.tsx
- frontend/src/pages/ApplicationStatusPage.test.tsx
- frontend/src/pages/WorkbenchPage.test.tsx
- frontend/coverage/lcov.info (refreshed)
- .sprint/20261028-114922/sprint-progress/TASK-002-red.log, TASK-002-green.log

## Open issues
- The 375px no-horizontal-scroll criterion is satisfied by construction (flex/grid, `min-width: 0`, `overflow-wrap: anywhere`, stacked queue table). It was not checked in a browser here, because the backend and the Playwright run belong to TASK-006. TASK-006's mobile-chromium run is the check.
- The app shell, routing and `main.tsx` are untouched, as required. The pages are not reachable until TASK-006 mounts them.
- Vitest titles carry extra tags beyond the plan's list: "AC-03.1" (Apply posts the exact C1 body, with the Basic header and the `onSubmitted` callback), a second "AC-03.2" (required-only client checks), a second "AC-09.4" (reasonCode error and required checks), a second "AC-09.6" (override codes), a second "AC-09.7" (admin sees declined and Override) and two "NFR-04.1" session tests. The routing "AC-03.1" test is still TASK-006's.
- The client's reason descriptions in the dropdown come from the spec catalogue in `src/reasonCodes.ts`. The queue and status pages show the descriptions the API returns.
- `.gitattributes` or `autocrlf` makes git warn that LF will be replaced by CRLF. This is cosmetic.
