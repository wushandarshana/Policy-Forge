# Release v0.1.0: Catalog and Quote

| | |
|---|---|
| Version | **v0.1.0** |
| Date | 2026-10-28 |
| Promotion | `dev` → `main`, `git merge --no-ff`, tag `v0.1.0` |
| QA'd revision | `dev` @ `d9e5379` |
| Previous release | none (first release; `main` previously held only the substrate, PR-001 `50a5f62`) |

## Included PRs

| PR | Branch | Type | Review sign-off |
|---|---|---|---|
| [PR-002](../prs/PR-002.md) | `feat/catalog-quote` | Feature: Sprint 1 | Harness evaluator PASS (2 iterations); `security-reviewer` **APPROVE** (3 rounds); `test-engineer` **APPROVE** |
| [PR-003](../prs/PR-003.md) | `chore/branching-strategy` | Substrate chore | No reviewer-agent run. See the note below. |
| [PR-004](../prs/PR-004.md) | `chore/release-v0.1.0` | Release record | Docs and coverage artifact only |
| [PR-005](../prs/PR-005.md) | `fix/hook-quoted-separators` | Substrate fix | Hook regression suite 145/145. Red confirmed on the old hook. |

**Note on PR-003 and PR-005.** `/release` requires security and test-engineer approvals for every *feature* merged since the last
release. PR-003 changes only guardrails (the commit hook, commands and docs), with no application code or data path.
Its behaviour is covered by the hook regression suite (138 cases at QA, 145 after PR-005). PR-005 was found *during*
this release: the live hook falsely blocked the `dev` → `main` merge because its `-m` message contained `;`. It was fixed
on a `fix/` branch and merged through the normal path, and the release resumed (KD-012). It was treated as out of
scope for the reviewer-agent requirement, and that is recorded here rather than left implicit.

## Delivered

**Backend platform**
- Spring Boot 3.5 / Java 21, Maven wrapper, Flyway V1–V3 and H2.
- Health endpoint (NFR-07.1).
- Correlation-ID JSON logs (NFR-06.1, NFR-06.2).
- HTTP Basic security with synthetic users.
- RFC 7807 errors.

**Product catalog (AC-02.1–02.7)**
- Immutable v1 rule files for TERM_LIFE, MOTOR and HOUSEHOLD.
- Validator that reports every problem at once.
- Append-only persistence and startup seeding.
- List, versions and publish API, with audit entries.

**Quote engine**
- Deterministic BigDecimal rating models (AC-01.1–01.8).
- Unknown inputs rejected (AC-01.9).
- Health-code validation (AC-01.10) and browser normalization (AC-01.11).
- Public quote read built from an allow-list, with UUID IDs (NFR-04.2).

**Security**
- JSON-only state-changing endpoints as the CSRF defence (NFR-04.3).
- Default-deny request-size limit on the decoded path, enforced before authentication (NFR-04.4).

**Architecture**
- ArchUnit rules for layering, domain purity, money precision, append-only data and controller placement, each with a
  negative fixture (NFR-08).

**Frontend**
- React Quote page: responsive, verbatim money display, per-field errors.

## QA (full, from clean, on `dev` @ `d9e5379`, 2026-10-28T10:46Z)

| Check | Result |
|---|---|
| `./mvnw clean verify` | **BUILD SUCCESS**: 106 unit + 75 integration tests, 0 failures, 0 errors, 0 skipped. JaCoCo checks met. |
| Backend coverage (JaCoCo) | Line 89.5% (1139/1272), branch 76.6% (395/516), instruction 89.8%. Gates: domain ≥ 80%, overall ≥ 70%. |
| `npm test` | 11/11 passed (3 files) |
| `npm run build` | OK |
| `npx playwright test` | 2/2 passed (desktop-chromium, mobile-chromium) |
| `premium_check.py spec-examples` | 9/9 PASS (6850.00, 15150.00, 11350.00, 18150.00, 9490.00, 1330.85, 4473.29, 6850.00, 0.00) |
| Hook regression suite | 138 cases, ALL PASSED at QA. 145 after PR-005, ALL PASSED. |
| `ac_audit.py --offline` | 25/72 covered. Every Sprint 1 ID is covered. The 47 uncovered IDs are AC-03 to AC-11 and NFR-03.1, all planned for Sprints 2–5. |
| `redact_evidence.py --check` | 0 findings |

Coverage artifacts: [`coverage/backend/jacoco.xml`](../../coverage/backend/jacoco.xml), refreshed from this QA build,
and [`frontend/coverage/lcov.info`](../../frontend/coverage/lcov.info).

**Delta after QA.** Between the QA'd revision `d9e5379` and the promoted `dev` tip, only `docs/`, `coverage/` and
`.claude/` (hook) files changed. No application code changed, so the QA results above still apply.

## Known residuals and open items
- **NFR-04.4 residual.** The 64 KB publish allowance is decided before authentication. A request with bogus
  credentials can send up to 64 KB before getting 401, and nothing is stored. This is documented in the spec.
- **J8.** Vehicle age above 15, or term-life age outside 18–65, gets a 400 at quote time, so UW-MO-002 can't fire in
  the normal flow. The quote spec wording needs clarifying in Sprint 2.
- **NFR-04.1 is only partly evidenced.** The 403 half needs the underwriter endpoints from Sprint 2.
- **Not yet built:** CI pipeline (`.gitlab-ci.yml` / GitHub Actions with Claude), Playwright visual-snapshot
  directory, and the Sprint 1 test-review minors m2–m6. All are planned for Sprint 5.
- **Repository setting the owner must change:** branch protection for `main` and `dev` on GitHub (no force-push or
  deletion, PRs required). The local hook isn't a server-side control.
