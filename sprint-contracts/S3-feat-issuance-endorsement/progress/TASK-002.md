# TASK-002 Progress

## Status
DONE

## What was built
Frontend for policy issuance and endorsement, all under `frontend/`. Added the typed C1-C5 API client and wire types, with C6 problems parsed through the existing `ApiError` (`errors[]`, `code`, `type`). Added the MyPolicies, PolicyDetail (Versions, Endorsements, Status, Refunds tabs) and Endorse pages (type selector, per-type forms, C5 preview gate, fixed messages for every C6 code, 409 INVALID_POLICY_STATE and 409 POLICY_UPDATE_CONFLICT), plus an Issue panel on the Application Status page (blank date posts `{}`; ISSUED shows an "Issued" state with a link to `#/policies`). Quote creation now sends the signed-in user's Basic header only on `POST /api/quotes`, and a 401 shows a fixed "sign in again" message with no anonymous retry. `App.tsx` routing is untouched: TASK-006 must mount the pages.

## TDD evidence
- G-1 check: `0bdda51 spec: endorsement preview endpoint (plan G-1 ...)` is in `819a246..HEAD`; no extra spec commit made.
- Red: `27c5062 test(red): AC-01.13, AC-05.1, AC-05.3, AC-06.1, AC-06.2, AC-06.4, AC-06.5, AC-06.6, AC-10.2, AC-10.4 ...` (46 failed, 41 existing passed; assertion / element-not-found failures, production stubs only). Log: `.sprint/20261103-130317/sprint-progress/TASK-002-red.log`.
- Green: `12415da feat(green): ...` (`npm test`: 13 files, 87 tests passed; statements 95.9%; lcov refreshed and committed). Log: `.sprint/20261103-130317/sprint-progress/TASK-002-green.log`. `npm run build` (tsc --noEmit + vite build) passes with no errors.
- Both logs name AC-01.13, AC-05.1, AC-05.3, AC-06.1, AC-06.2, AC-06.4, AC-06.5, AC-06.6, AC-10.2, AC-10.4 and pass `scripts/redact_evidence.py --check`.
- The green commit also fixes two of my own red tests that forgot to install the fetch mock (they had failed on the stub anyway).

## Contracts and test IDs TASK-006 can rely on
- Page props: `ApplicationStatusPage({applicationId, onIssued?(policyNumber)})`, `MyPoliciesPage()`, `PolicyDetailPage({policyNumber})`, `EndorsePage({policyNumber, onEndorsed?(policyNumber)})`. Links already point at `#/policies`, `#/policies/<n>` and `#/policies/<n>/endorse`.
- Issue: `issue-panel`, `issue-effective-date`, `issue-effective-date-error`, `issue-submit`, `issue-error`, `application-issued`, `application-policies-link`.
- My Policies: `policies-list`, `policies-empty`, `policy-card-<number>` with `-status`, `-product`, `-sum-insured`, `-premium`, `-next-due`, `-endorsements`, `-link`.
- Detail: `policy-number`, `policy-status-badge`, `policy-product`, `policy-effective-date`, `policy-expiry-date`, `policy-sum-insured`, `policy-premium`, `policy-next-due`, `policy-endorse-link`, `policy-not-found`, `tab-<versions|endorsements|status|refunds>`, `tabpanel-<name>`, `version-row-<n>` with `version-<n>-address|sum-insured|premium|nominees`, `endorsement-row-<seq>` with `endorsement-<seq>-type|versions|adjustment|new-premium` (versions text is `v1 → v2`), `transition-row-<i>` with `transition-<i>-from|to|reason` (null from is an em dash), `refunds-empty`.
- Endorse: `endorse-type`, `input-line1|city|postcode|nomineeName|nomineeRelationship|nomineeSharePercent|newSumInsured|newBuildingSumInsured|newContentsSumInsured|effectiveDate`, `error-<field>`, `endorse-preview`, `endorse-confirm`, `endorse-error` (with `data-code`), `endorse-form-errors`, `preview-result`, `preview-current-premium|new-premium|adjustment|unexpired-days|term-days|effective-date`, `endorse-not-found`.

## Files changed
- frontend/src/api/types.ts
- frontend/src/api/policies.ts
- frontend/src/api/index.ts
- frontend/src/api/http.ts
- frontend/src/api/quotes.ts
- frontend/src/messages.ts
- frontend/src/pages/QuotePage.tsx
- frontend/src/pages/ApplicationStatusPage.tsx
- frontend/src/pages/MyPoliciesPage.tsx
- frontend/src/pages/PolicyDetailPage.tsx
- frontend/src/pages/EndorsePage.tsx
- frontend/src/styles.css
- frontend/src/test/helpers.tsx
- frontend/src/pages/QuotePageCredentials.test.tsx
- frontend/src/pages/ApplicationStatusPage.issue.test.tsx
- frontend/src/pages/MyPoliciesPage.test.tsx
- frontend/src/pages/PolicyDetailPage.test.tsx
- frontend/src/pages/EndorsePage.test.tsx
- frontend/coverage/lcov.info

## Open issues
- Not verified in a real browser: the 375px no-horizontal-scroll layout rests on CSS review (single-column grids, wrapping flex, `overflow-wrap`). TASK-006's mobile Playwright run and snapshots are the real check.
- `sharePercent` is sent as an integer when the input is whole digits and as the typed string otherwise (so the server rejects it); `EndorseRequest` types it `number | string` for that reason.
- The Endorse page shows all three types for every product (P-13) and relies on the server for `NOMINEE_NOT_SUPPORTED`.
- Terms from C3 are typed but not displayed (not required).
- Backend work from TASK-001 was running in the same tree; this task touched nothing outside `frontend/`.
