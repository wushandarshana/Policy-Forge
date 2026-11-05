# PolicyForge — Application Spec (root)

**Status:** Approved v1.0 · **Owner:** AI-Native Engineer · **Source:** `docs/business-case.md`

This is the root specification. Feature behaviour is defined in the feature specs listed in §3. Where this file and a
feature spec disagree, the feature spec wins for that feature. Where any spec and the code disagree, **the spec wins**.

## 1. Goal

One platform for quote-to-bind-to-renewal across **TERM_LIFE**, **MOTOR** and **HOUSEHOLD**. Pricing and underwriting
come from versioned rule files. Every material record is append-only and every money value is fixed-point.

## 2. Actors and roles

Authentication is HTTP Basic with in-memory synthetic users (out of scope: real IdP). Roles are checked at the
controller layer (NFR-04).

| User (synthetic) | Password | Roles |
|---|---|---|
| `customer1`, `customer2` | `password` | `CUSTOMER` |
| `underwriter1` | `password` | `UNDERWRITER` |
| `admin1` | `password` | `ADMIN`, `UNDERWRITER`, `PRODUCT_MANAGER` |

Customers see only their own applications and policies.

## 3. Feature specs

| Spec | ACs |
|---|---|
| [`product-catalog_spec.md`](product-catalog_spec.md) | AC-02 |
| [`quote-engine_spec.md`](quote-engine_spec.md) | AC-01 |
| [`underwriting_spec.md`](underwriting_spec.md) | AC-03 (03.1–03.8), AC-04, AC-09 (09.1–09.7) |
| [`policy-issuance_spec.md`](policy-issuance_spec.md) | AC-05, AC-10 |
| [`endorsement_spec.md`](endorsement_spec.md) | AC-06 |
| [`renewal-cancellation_spec.md`](renewal-cancellation_spec.md) | AC-07, AC-08 |
| [`portfolio-dashboard_spec.md`](portfolio-dashboard_spec.md) | AC-11 |

## 4. Acceptance Criteria (index)

Each AC is detailed in its feature spec. Every AC must have at least one test tagged with its ID.

| ID | Criterion |
|---|---|
| AC-01 | Quote engine returns a deterministic premium for a product and insured profile, sourced from a versioned policy rule file |
| AC-02 | Product catalog supports at least 3 products (Term Life, Motor, Household) with distinct rule sets |
| AC-03 | Application captures a KYC stub and risk inputs, validates them against the product rule set, and moves the case to underwriting |
| AC-04 | Underwriting decision returns AUTO_BIND / MANUAL_REVIEW / DECLINE with reason codes from the active policy version |
| AC-05 | Policy issuance generates a unique policy number, effective date, sum insured and premium, and sets status ACTIVE |
| AC-06 | Endorsement creates an immutable endorsement record linked to the policy and updates policy attributes atomically |
| AC-07 | Renewal generates a new term with refreshed premium; the policy auto-lapses if premium is unpaid beyond the grace period |
| AC-08 | Cancellation calculates a pro-rated refund per product rules; status moves to CANCELLED; the refund record is append-only |
| AC-09 | Admin reviews the manual underwriting queue and can override a DECLINE with reason code and comment; the override is audited |
| AC-10 | Policy state transitions ACTIVE → ENDORSED / LAPSED / CANCELLED / RENEWED are enforced; invalid transitions raise `InvalidPolicyStateException` |
| AC-11 | Admin portfolio dashboard shows active policy count by product, premium collected, renewal pipeline and lapse forecast |

## 5. Non-functional requirements

| ID | Requirement | Verified by |
|---|---|---|
| NFR-01 | Premium and refund amounts use `BigDecimal`, never floating point | ArchUnit `MoneyPrecisionArchTest`; `premium-precision-check.sh` |
| NFR-02 | Policy versions, endorsements and state transitions are append-only | ArchUnit `AppendOnlyArchTest`; repository tests |
| NFR-03 | PII (Aadhaar, PAN, health declarations) is never written to logs | Log-capture test; `pii-redaction-check.sh` |
| NFR-04 | Auth boundary at the controller layer; underwriter and admin actions audited with actor ID | Security slice tests; audit tests |
| NFR-05 | DB migrations and policy rule versions are append-only | `policy-immutability-check.sh`; Flyway validate-on-migrate |
| NFR-06 | Structured JSON logs with request correlation ID (`X-Correlation-Id`) | Filter test |
| NFR-07 | `/actuator/health` returns 200 within 1 s of startup | Integration test |
| NFR-08 | Architecture rules are automated tests | `backend/src/test/java/.../architecture/` |
| NFR-09 | Every push to and pull or merge request into dev and main runs the same automated gates on GitHub Actions and GitLab CI, ending with a required Claude Code review | scripts/tests/ CI tests; actionlint; GitLab CI schema check; the pipelines |

### Acceptance Criteria (NFR, testable form)

- **NFR-06.1** Given a request without `X-Correlation-Id`, when it is handled, then the response carries a generated
  `X-Correlation-Id` header and every log line for that request contains the same `correlationId` field in JSON.
- **NFR-06.2** Given a request with `X-Correlation-Id: abc-123`, when it is handled, then the response echoes `abc-123`.
- **NFR-07.1** Given the application has started, when `GET /actuator/health` is called, then it returns 200 with
  `{"status":"UP"}` in under 1000 ms.
- **NFR-03.1** Given an application with Aadhaar `XXXX-XXXX-1234` and PAN `ABCDE1234F`, when it is submitted and
  underwritten, then no captured log line contains either value or any health declaration text.
- **NFR-04.1** Given an unauthenticated request to any `/api/**` endpoint except `GET /api/products**`,
  `POST /api/quotes` and `GET /api/quotes/{id}`, then the response is 401. Given a CUSTOMER calls an underwriter
  endpoint, then the response is 403.
- **NFR-04.2** Given a stored TERM_LIFE quote with BMI and health declarations, when `GET /api/quotes/{id}` is called
  without authentication, then the response contains the premium, breakdown and rule version but no `bmi`,
  `healthDeclarations` or other health inputs, and the quote ID is an unguessable `Q-<UUIDv4>`. The response is built
  from an **allow-list**: only the risk inputs that the product's rating model declares, minus health inputs. No
  submitted key outside that list is ever returned.
- **NFR-04.3** Given any state-changing `/api/**` endpoint (POST/PUT/PATCH/DELETE), when it is called with a
  `Content-Type` other than `application/json` (for example a cross-site `<form enctype="text/plain">`), then the
  response is **415** and nothing is changed. CORS stays unconfigured, so cross-origin JSON requests fail preflight.
  This is the CSRF defence for browser-cached HTTP Basic credentials.
- **NFR-04.4** Every request body (all paths, including `/api/**`) is size-limited **by default**: 16 KB, with or
  without a `Content-Length`. The limit is enforced **before** authentication. The only exception is
  `POST /api/products/{code}/versions` carrying an `Authorization` header, which allows 64 KB. Credentials can't be
  verified at that point, so a request with an invalid header may send up to 64 KB before Spring Security rejects it
  with 401, and nothing is stored. Anonymous requests always get the 16 KB cap. Oversized requests get **413** and
  nothing is stored. The route is chosen on the **decoded, normalized** path that
  Spring Security and MVC route on, so encoded variants such as `/api/%71uotes` are capped like `/api/quotes`. Any
  path that doesn't match an allowance gets the 16 KB default. (Security re-review R-1.)
- **NFR-08.1** Given the production classes, when any class other than `EndorsementService` calls
  `PolicyTermEntity.repriceOffer`, then the ArchUnit suite fails. A negative fixture in which a class other than the
  fixture's endorsement service calls it is rejected, and the fixture's endorsement service is not (Sprint 4
  security review MINOR-A).
- **NFR-09.1** Given `.github/workflows/ci.yml` and `.gitlab-ci.yml`, then each defines the jobs `build`,
  `frontend-unit`, `e2e`, `quality-gates` and `claude-review` with the same gate commands. Each runs for pushes to
  `dev` and `main` and for pull or merge requests into them. `claude-review` runs only after the other four jobs
  pass and is never allowed to fail.
- **NFR-09.2** Given the CI definitions and `.mcp.json`, then every GitHub action is pinned to a full 40-character
  commit SHA, every container image to an exact version tag with an `@sha256:` digest, every downloaded tool to an
  exact version with a SHA-256 checksum, and every npm package run by CI or MCP to an exact version. Nothing uses
  `latest` or a floating tag. The Playwright image's version equals the `@playwright/test` version locked in
  `frontend/package-lock.json`, and the CI Node version equals `frontend.node.version` in `backend/pom.xml`.
- **NFR-09.3** Given the CI definitions, then the GitHub workflows default to `permissions: contents: read`, and
  only `claude-review` adds `pull-requests: write`. No workflow uses `pull_request_target`, and every checkout sets
  `persist-credentials: false`. No `run:` script contains a `${{ github.event.… }}` expression. `ANTHROPIC_API_KEY`
  reaches only the review step, from the secret of the GitHub Environment `claude-review` (which requires the owner's
  approval before the job runs) or the masked GitLab variable scoped to the protected environment `claude-review`,
  and is never printed. No repository-level copy of the key exists. No
  CI file sets `NODE_TLS_REJECT_UNAUTHORIZED`. `redact_evidence.py` redacts an Anthropic API key (`sk-ant-…`) in
  evidence and review output.
- **NFR-09.4** Given a Claude review run, then each pass's prompt contains the body of
  `.claude/agents/security-reviewer.md` or `.claude/agents/test-engineer.md` verbatim, and the pinned Claude Code CLI
  runs in bare, read-only mode against the diff under review. The job fails when any finding has severity BLOCKER,
  when a pass returns no structured verdict or one that fails the verdict schema, when the CLI exits non-zero or is
  not the pinned version, or when `ANTHROPIC_API_KEY` is absent. With no BLOCKER it passes and still publishes every
  MAJOR and MINOR finding. An empty diff passes without calling the CLI.
- **NFR-09.5** Given an E2E run on a platform with no committed visual baselines, then each visual assertion is
  skipped and recorded by name, and every functional assertion still runs. Given a platform with at least one
  committed baseline, a missing or differing baseline fails the test. A run in update mode writes only the missing
  baselines of its own platform.
- **NFR-09.6** Given the quality gates, then the job fails when the offline AC audit reports any NOT COVERED or
  PARENT-ONLY ID, when `redact_evidence.py --check` finds a credential in `sprint-contracts/`, `specs/reviews/` or
  `docs/fix-loops/`, when the hook regression suite fails, when a `.claude/hooks/*.sh` file has a CR byte in the
  index or the working tree, when the AC-08.5 oracle fixture differs from a fresh generation, or when actionlint or
  the CI schema checks report an error. It runs every gate and reports each result before it fails.
- **NFR-09.7** Given a Claude review run (Sprint 5 security review, user decision 2026-11-04), then:
  - the CLI gets no Bash tool and runs in a disposable `git worktree` copy of the checkout, outside the repository,
    which is removed afterwards. It receives the diff and the commit log as files it may `Read`, and `Read`, `Grep`
    and `Glob` are limited to that copy (no `/proc`, no other path). New untracked files the CLI's sandbox creates in
    the copy are tolerated and logged, but a changed tracked file in the copy fails the job;
  - the CLI's environment is built from an allowlist (`PATH`, `HOME`, `LANG`, `TMPDIR`, `ANTHROPIC_API_KEY`,
    `CLAUDE_*`), so no GitHub or GitLab token, registry password or token-bearing URL reaches it;
  - after each pass the job fails closed if any tracked or untracked file outside `ci-out/` changed in the real
    checkout;
  - the PR or MR comment is posted by a separate job that holds the platform token, downloads only the report
    artifact, and runs the comment code from the base branch, never from the code under review. It edits only a
    comment written by its own bot user, and refuses any API URL that is not `https://`;
    if the base branch has no comment code yet (the first PR that adds CI), it skips the comment with a notice, and
    the report stays an artifact;
  - the Claude Code CLI and the CI Python packages are installed from committed lockfiles with integrity hashes
    (`npm ci`, `pip install --require-hashes`);
  - a final required job fails unless every gate job succeeded, so a skipped review never counts as passing;
  - `.github/CODEOWNERS` names the owner of `.github/**`, `.gitlab-ci.yml`, `scripts/ci/**`,
    `scripts/redact_evidence.py`, `scripts/ac_audit.py` and `.claude/agents/**`. While the repository has a single
    owner, code-owner review is not enforced (it would block the owner's own PRs). The owner's approval of the
    `claude-review` environment is the enforced control, and the approver checks changes to those paths first.
  **Accepted v1 limitation (Sprint 5 security re-review, user decision 2026-11-04).** On a same-repository PR the
  workflow file comes from the PR, so a writer can change the jobs. The key is still released only after the
  owner approves the `claude-review` environment, and the comment job's token adds no right a writer lacks. Treat
  the Claude review as defence in depth, not as the merge authority.
  A PR from a fork never receives the key, so its review fails closed. A maintainer re-pushes the branch to `origin`
  to review it.

## 6. Domain model (summary)

```
Product 1─* RuleSetVersion (immutable, versioned JSON)
Quote *─1 RuleSetVersion
Application 1─1 Quote ; Application 1─* UnderwritingDecision (append-only)
Policy 1─* PolicyVersion (append-only snapshots) ; Policy 1─* PolicyStatusTransition (append-only)
Policy 1─* Endorsement (immutable) ; Policy 1─* PolicyTerm ; Policy 1─* RefundRecord (append-only)
AuditEntry (append-only: actorId, action, entityType, entityId, reasonCode, comment, timestamp)
```

## 7. Money and time conventions

- Currency INR, `BigDecimal`. Intermediate scale 10 with `RoundingMode.HALF_UP`. Final amounts scale 2 with HALF_UP.
- All dates are `LocalDate`, from an injectable `java.time.Clock` (tests use a fixed clock).
- Day counts are `ChronoUnit.DAYS.between(start, end)`, start inclusive and end exclusive.

## 8. REST API overview

| Method | Path | Role | Spec |
|---|---|---|---|
| GET | `/api/products` | public | catalog |
| GET | `/api/products/{code}/versions` | public | catalog |
| POST | `/api/products/{code}/versions` | PRODUCT_MANAGER | catalog |
| POST | `/api/quotes` | public | quote |
| GET | `/api/quotes/{id}` | public (UUID id; health inputs omitted, NFR-04.2) | quote |
| POST | `/api/applications` | CUSTOMER | underwriting |
| GET | `/api/applications/mine` | CUSTOMER | underwriting |
| GET | `/api/underwriting/queue` | UNDERWRITER | underwriting |
| POST | `/api/underwriting/{applicationId}/decision` | UNDERWRITER | underwriting |
| POST | `/api/underwriting/{applicationId}/override` | ADMIN | underwriting |
| POST | `/api/applications/{id}/issue` | CUSTOMER (owner) | issuance |
| GET | `/api/policies/mine` | CUSTOMER | issuance |
| GET | `/api/policies/{number}` | owner / ADMIN | issuance |
| POST | `/api/policies/{number}/endorsements` | owner | endorsement |
| POST | `/api/policies/{number}/endorsements/preview` | owner | endorsement |
| POST | `/api/policies/{number}/renewal/pay` | owner | renewal |
| GET | `/api/policies/{number}/cancel/preview?date=` | owner | cancellation |
| POST | `/api/policies/{number}/cancel` | owner | cancellation |
| POST | `/api/admin/jobs/renewal-cycle?date=` | ADMIN | renewal |
| GET | `/api/admin/dashboard` | ADMIN | dashboard |
| GET | `/api/admin/audit` | ADMIN | underwriting |

Errors use `application/problem+json` (RFC 7807). `InvalidPolicyStateException` maps to 409 and validation errors to
400 with field errors.

## 9. Seed data (synthetic)

- Rule files `rules/term-life/v1.json`, `rules/motor/v1.json`, `rules/household/v1.json`.
- Sample policies: at least 2 per product for `customer1`/`customer2`, plus 1 MANUAL_REVIEW application in the queue,
  1 policy near renewal and 1 policy past expiry within grace.
- Delivery is staged with the features it depends on. The MANUAL_REVIEW application ships with underwriting (Sprint 2,
  underwriting spec U-14). The sample policies ship with issuance and renewal (Sprints 3–4).
