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
| [`underwriting_spec.md`](underwriting_spec.md) | AC-03, AC-04, AC-09 |
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

### Acceptance Criteria (NFR, testable form)

- **NFR-06.1** Given a request without `X-Correlation-Id`, when it is handled, then the response carries a generated
  `X-Correlation-Id` header and every log line for that request contains the same `correlationId` field in JSON.
- **NFR-06.2** Given a request with `X-Correlation-Id: abc-123`, when it is handled, then the response echoes `abc-123`.
- **NFR-07.1** Given the application has started, when `GET /actuator/health` is called, then it returns 200 with
  `{"status":"UP"}` in under 1000 ms.
- **NFR-03.1** Given an application with Aadhaar `XXXX-XXXX-1234` and PAN `ABCDE1234F`, when it is submitted and
  underwritten, then no captured log line contains either value or any health declaration text.
- **NFR-04.1** Given an unauthenticated request to any `/api/**` endpoint except `GET /api/products**` and
  `POST /api/quotes`, then the response is 401. Given a CUSTOMER calls an underwriter endpoint, then the response is 403.

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
| GET | `/api/quotes/{id}` | public | quote |
| POST | `/api/applications` | CUSTOMER | underwriting |
| GET | `/api/applications/mine` | CUSTOMER | underwriting |
| GET | `/api/underwriting/queue` | UNDERWRITER | underwriting |
| POST | `/api/underwriting/{applicationId}/decision` | UNDERWRITER | underwriting |
| POST | `/api/underwriting/{applicationId}/override` | ADMIN | underwriting |
| POST | `/api/applications/{id}/issue` | CUSTOMER (owner) | issuance |
| GET | `/api/policies/mine` | CUSTOMER | issuance |
| GET | `/api/policies/{number}` | owner / ADMIN | issuance |
| POST | `/api/policies/{number}/endorsements` | owner | endorsement |
| POST | `/api/policies/{number}/renewal/pay` | owner | renewal |
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
