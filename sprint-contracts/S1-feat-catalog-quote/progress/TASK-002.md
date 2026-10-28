# TASK-002 Progress

## Status
DONE

## What was built
A React 18 + Vite + TypeScript frontend in `frontend/` with a Quote page. The page loads products from `GET /api/products` (C1). It shows a form with exactly the quote spec's inputs per product (TERM_LIFE 6, MOTOR 6, HOUSEHOLD 7). It submits to `POST /api/quotes` (C2). The result shows `annualPremium`, `sumInsured`, `ruleVersion`, `validUntil`, `quoteId`, every breakdown row and an Apply link (`#/apply?quoteId=<id>`). 400 problem+json `errors[]` entries (C3) render next to the input named by `field`. An entry that matches no input (for example HOUSEHOLD's derived `sumInsured`, J9) goes in a `form-errors` summary. Money is rendered and sent as the strings received or typed. The source has no `Number()`, `parseFloat` or unary plus, and a Vitest guard (`NFR-01`) enforces that. Integer inputs go out as JSON numbers via `parseInt(text, 10)`. A blank input is omitted so the server reports it as missing. Layout is mobile-first grid/flex with one `@media (min-width: 640px)` rule.

Verification beyond the unit tests: I built `dist/`, served it with a throwaway mock API (scratchpad, not in the repo), and drove it in a real browser with Playwright MCP. At 375px there was no horizontal overflow, measured per element, for empty form, 400 errors, result view, HOUSEHOLD and MOTOR. It also showed no overflow at 800px. The premium rendered as `6850.00`. The mock servers were stopped by PID.

TDD: the tests were written first and `npm test` was run with `QuotePage` absent. Both test files failed to load, so no test ran (`sprint-progress/TASK-002-red.log`). The implementation was then written. Once green, I removed an unused-parameter type error and re-ran (`TASK-002-green.log`: 9 passed, build OK, coverage written).

## Acceptance criteria self-check
- Scaffold with `npm test` (single-run Vitest, with v8 coverage writing `frontend/coverage/lcov.info`) and `npm run build` (`tsc --noEmit && vite build`). The proxy `/api` to `http://localhost:8080` is in `vite.config.ts`. MET. `npm run test:coverage` is an alias.
- Typed API client matching C1 to C3: `src/api/types.ts`, `http.ts`, `products.ts`, `quotes.ts`, and `index.ts` (adds `getQuote`). MET
- Product selector from the API with a dynamic form (6/6/7 inputs, selects and checkboxes for enumerations and booleans). MET, tested by the `AC-01.1/01.2/01.3` form-shape tests.
- Submit, result and Apply link carrying the quote ID. MET
- Money displayed verbatim, no number conversion. MET, with the `NFR-01` scan test.
- Per-field errors next to the input. MET
- `data-testid` on all inputs (`input-<name>`), the submit button (`submit-quote`), `quote-premium`, `quote-rule-version`, `breakdown-row-<factor>`, and the Apply link (`quote-apply`). Also `product-select`, `field-<name>`, `error-<name>`, `form-errors`, `quote-sum-insured`, `quote-valid-until`, `quote-id`, `breakdown-value-<factor>`. MET
- CSS grid/flex with a single 640px breakpoint, no horizontal scroll at 375px. MET, checked in a browser.
- Vitest tests `AC-01.1:` (6850.00 verbatim, rule version 1), `AC-01.7:` (per-field errors, plus unmatched-field summary) and `AC-01.8:` (every breakdown factor) all pass. MET
- `npm run build` has no TypeScript errors. MET. `tsc` also type-checks the test files.
- Red and green logs in `sprint-progress/`. MET

## Iteration 2 fix
The evaluator found `frontend/coverage/lcov.info` unreliable. I reproduced it: with the default multi-worker pool, the v8 source-map remap was nondeterministic. Most runs reported 100% (9/9) with every file at `LF:1`, and some left only `QuotePage.tsx` correct. I tried `processingConcurrency=1`, `pool=threads` and `experimentalAstAwareRemapping`. None helped, and the last made things worse (`LF:0`). A single forked worker is deterministic. `vite.config.ts` now sets `pool: 'forks'` with `poolOptions.forks.singleFork: true`. Five consecutive `npm test` runs (the first from a cold `node_modules/.vite` cache) all gave 9 passed, 96.46% lines (382/396), and per-file LF of 31/4/4/11/0/50/71/137/88. `tsc --noEmit` and `npm run build` pass. Only `frontend/vite.config.ts` and the green log changed.

## Commands run and results
- `npm install` in `frontend/` gave react 18.3.1, vite 6.4.5, vitest 3.2.7, typescript 5.9.3, jsdom 26.1.0 and testing-library.
- `npm test` (red, `QuotePage` absent): 2 suites failed with "Failed to resolve import ./QuotePage".
- `npm test` (green): 3 files and 9 tests passed. Coverage in iteration 1 was 92.41% lines, 78.21% branches, and lcov written; iteration 2 is stable at 96.46% lines (see Iteration 2 fix).
- `npm run build`: passed, 35 modules, `dist/` produced.
- Browser check at 375px and 800px against the mock API: no overflowing elements.

## Files changed
- frontend/package.json
- frontend/package-lock.json
- frontend/index.html
- frontend/tsconfig.json
- frontend/vite.config.ts (iteration 2: single-fork pool for stable v8 lcov)
- frontend/src/main.tsx
- frontend/src/styles.css
- frontend/src/vite-env.d.ts
- frontend/src/test/setup.ts
- frontend/src/api/types.ts
- frontend/src/api/http.ts
- frontend/src/api/products.ts
- frontend/src/api/quotes.ts
- frontend/src/api/index.ts
- frontend/src/pages/QuotePage.tsx
- frontend/src/pages/riskFields.ts
- frontend/src/pages/QuotePage.test.tsx
- frontend/src/pages/QuotePageForm.test.tsx
- frontend/src/noMoneyArithmetic.test.ts
- frontend/src/components/QuoteResult.tsx
- frontend/src/components/RiskInputField.tsx
- frontend/coverage/lcov.info (generated)
- .sprint/20261028-052716/sprint-progress/TASK-002-red.log
- .sprint/20261028-052716/sprint-progress/TASK-002-green.log

## Open issues
- `.gitignore` line `frontend/coverage/` ignores the whole directory, so the `!frontend/coverage/lcov.info` negation has no effect. `git check-ignore` confirms `lcov.info` is ignored. frontend/CLAUDE.md says it is committed. Change the rule to `frontend/coverage/*` (a substrate edit for a human or the orchestrator). I did not touch `.gitignore`.
- `npm test` runs with coverage, so the lcov file is refreshed on every test run. The green log was captured with `npm run test:coverage` (the same command).
- The Apply link is `#/apply?quoteId=...`. The Apply journey (AC-03) is later work (J13), so the link has no destination page yet. TASK-007's e2e test should not click it.
- The product form field lists are client-side (J13). If the API's input schema changes, `src/pages/riskFields.ts` must be updated by hand.
- `npm audit` reported advisories on dev-tool dependencies at install time. I did not run `npm audit fix`, to avoid breaking changes.
- The Playwright e2e config and test belong to TASK-007. The `data-testid` values above are the stable hooks for that test.
- esbuild's postinstall is not covered by `allowScripts` (npm warning). The build and tests work, since the esbuild binary is present.
- The `frontend/coverage/lcov-report/` HTML output is gitignored.
