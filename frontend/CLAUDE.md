# frontend/ — CLAUDE.md

React 18 + Vite + TypeScript SPA. Visual polish isn't graded; correctness, responsiveness and testability are.

- Structure: `src/api/` (typed fetch client, one module per backend area), `src/pages/` (one page per user journey),
  `src/components/` (shared), `src/auth/` (Basic-auth session context).
- Money arrives as **decimal strings** from the API. Display it as is or format it with `Intl.NumberFormat` from the
  string. **Never do arithmetic on money in the browser.**
- Responsive: CSS grid/flex with one breakpoint at 640px. Every page must work at 375px wide (Playwright mobile
  project).
- Every interactive element that tests touch has a `data-testid`.
- Tests: Vitest + Testing Library for components (`*.test.tsx`), Playwright E2E in `e2e/` (see `e2e/CLAUDE.md`).
  Titles start with `AC-NN.M:`.
- Coverage: `vitest --coverage` (v8) writes `coverage/lcov.info`, which is committed.
