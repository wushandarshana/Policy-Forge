# frontend/e2e/ — CLAUDE.md

Playwright end-to-end tests. They run against the packaged app on `http://localhost:8080`, which `webServer` in
`playwright.config.ts` starts with `../backend/mvnw spring-boot:run`.

- One spec file per journey: `quote.spec.ts`, `apply-underwrite.spec.ts`, `policy-lifecycle.spec.ts`,
  `workbench.spec.ts`, `dashboard.spec.ts`.
- Test titles start with the AC, e.g. `test('AC-01.1: term life quote shows 6850.00', …)`.
- Visual snapshots: `await expect(page).toHaveScreenshot()`, stored in `e2e/__snapshots__/` (committed). Mask dynamic
  values (dates, policy numbers).
- Projects: `desktop-chromium` and `mobile-chromium` (iPhone 13 viewport), which together prove the responsive layout.
- Use `data-testid` selectors. No `waitForTimeout`; use web-first assertions.
- The evaluator agent also uses **Playwright MCP** (`.mcp.json`) for exploratory checks, and records findings in
  `specs/reviews/`.
