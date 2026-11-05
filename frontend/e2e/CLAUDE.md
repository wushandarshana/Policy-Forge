# frontend/e2e/ — CLAUDE.md

Playwright end-to-end tests. They run against the packaged app on `http://localhost:8080`, which `webServer` in
`playwright.config.ts` starts with `../backend/mvnw spring-boot:run`.

- One spec file per journey: `quote.spec.ts`, `apply-underwrite.spec.ts`, `policy-lifecycle.spec.ts`,
  `workbench.spec.ts`, `dashboard.spec.ts`.
- Test titles start with the AC, e.g. `test('AC-01.1: term life quote shows 6850.00', …)`.
- Visual snapshots: call the visual-baseline helper in `support.ts`, never `toHaveScreenshot` directly. Baselines live
  in `e2e/__snapshots__/` (committed), suffixed by project and platform (`-win32`, `-linux`). Mask dynamic values
  (dates, policy numbers). On a platform with no committed baselines, the helper skips only the visual assertion and
  records it by name (NFR-09.5). On a platform that has baselines, a missing or differing one fails. `-linux` baselines
  are generated only in CI (the `Linux snapshots` workflow or the GitLab manual job `e2e-update-linux-snapshots`) and
  committed in their own commit after review. Never generate or edit them by hand.
- When `PF_E2E_JAR` is set (CI), `webServer` starts that packaged JAR with the browser-test setting instead of
  `mvnw spring-boot:run`.
- Projects: `desktop-chromium` and `mobile-chromium` (iPhone 13 viewport), which together prove the responsive layout.
- Use `data-testid` selectors. No `waitForTimeout`; use web-first assertions.
- The evaluator agent also uses **Playwright MCP** (`.mcp.json`) for exploratory checks, and records findings in
  `specs/reviews/`.
