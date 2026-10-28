# Feature Spec — Application, KYC & Underwriting

**Parent:** [`app_spec.md`](app_spec.md) · **ACs:** AC-03, AC-04, AC-09 · **NFR:** NFR-03, NFR-04
**Domain classes:** `KycValidator`, `RiskInputValidator`, `UnderwritingRuleEvaluator`, `UnderwritingOutcome`,
`ReasonCode`

## Application (AC-03)

`POST /api/applications` (CUSTOMER):
```json
{ "quoteId": "Q-…",
  "kyc": { "fullName": "Asha Test", "dateOfBirth": "1991-04-02", "aadhaar": "XXXX-XXXX-1234",
           "pan": "ABCDE1234F", "address": { "line1": "1 Demo Street", "city": "Pune", "postcode": "411001" } } }
```
- The risk inputs are taken from the quote. The quote must exist, belong to the same product, and be inside
  `validUntil`.
- **KYC stub validation (`KycValidator`):** `fullName` is non-blank. `dateOfBirth` gives an age that matches the quote's
  `age` input (TERM_LIFE) or `driverAge` (MOTOR), within 0 years. `aadhaar` matches `^[X0-9]{4}-[X0-9]{4}-\d{4}$`.
  `pan` matches `^[A-Z]{5}\d{4}[A-Z]$`. The address `line1`, `city` and `postcode` (6 digits) are required.
- **Risk validation (`RiskInputValidator`):** inputs are re-checked against the active rule set's ranges.
- Application statuses: `SUBMITTED` → `UNDERWRITING` → (`APPROVED` | `REFERRED` | `DECLINED`) → `ISSUED`.
  A valid submission moves straight to `UNDERWRITING` and is evaluated in the same request.
- PII (aadhaar, pan, healthDeclarations, dateOfBirth, fullName) is stored but **never logged** (NFR-03). In API
  responses to non-owners, Aadhaar and PAN are masked to their last 4 characters.

## Underwriting rules (AC-04)

`UnderwritingRuleEvaluator` evaluates every rule in the **active** rule version against the risk inputs plus derived
fields (`sumInsured`, `age`).

- **Outcome precedence:** any `DECLINE` rule gives **DECLINE**; otherwise any `REFER` rule gives **MANUAL_REVIEW**;
  otherwise **AUTO_BIND**.
- **Reason codes:** every fired rule's `code` and `description` is returned, sorted by code. AUTO_BIND returns
  `["UW-OK"]`.
- The decision is stored in the append-only `underwriting_decision` table with the rule version.

### v1 rules

| Code | Product | Outcome | Condition |
|---|---|---|---|
| UW-TL-001 | TERM_LIFE | DECLINE | age > 60 |
| UW-TL-002 | TERM_LIFE | REFER | smoker AND sumInsured > 10,000,000 |
| UW-TL-003 | TERM_LIFE | REFER | healthDeclarations NOT_EMPTY |
| UW-TL-004 | TERM_LIFE | REFER | bmi > 35 |
| UW-TL-005 | TERM_LIFE | DECLINE | bmi > 45 |
| UW-MO-001 | MOTOR | DECLINE | claimsLast3Years ≥ 3 |
| UW-MO-002 | MOTOR | DECLINE | vehicleAgeYears > 15 |
| UW-MO-003 | MOTOR | REFER | driverAge < 21 |
| UW-MO-004 | MOTOR | REFER | vehicleUse = COMMERCIAL AND vehicleValue > 5,000,000 |
| UW-HH-001 | HOUSEHOLD | DECLINE | propertyAgeYears > 100 |
| UW-HH-002 | HOUSEHOLD | REFER | floodZone = HIGH AND hasFloodDefences = false |
| UW-HH-003 | HOUSEHOLD | REFER | constructionType = TIMBER |
| UW-HH-004 | HOUSEHOLD | REFER | sumInsured > 30,000,000 |

Mapping to application status: AUTO_BIND → `APPROVED`, MANUAL_REVIEW → `REFERRED`, DECLINE → `DECLINED`.

## Underwriter workbench and override (AC-09)

- `GET /api/underwriting/queue` (UNDERWRITER) lists `REFERRED` applications, oldest first, with masked KYC, risk
  inputs and reason codes. `?includeDeclined=true` (ADMIN) also lists `DECLINED`.
- `POST /api/underwriting/{id}/decision` (UNDERWRITER), body `{ "decision": "APPROVE"|"DECLINE", "reasonCode": "UW-MAN-0xx", "comment": "…" }`.
  Only valid when the application is `REFERRED`. A comment of at least 10 characters is required.
- `POST /api/underwriting/{id}/override` (ADMIN), body `{ "reasonCode": "UW-OVR-0xx", "comment": "…" }`. Only valid when
  the application is `DECLINED`. It sets `APPROVED`. A comment of at least 10 characters is required.
- Manual reason codes: `UW-MAN-001` Acceptable after review, `UW-MAN-002` Additional evidence satisfactory,
  `UW-MAN-010` Risk outside appetite. Override codes: `UW-OVR-001` Commercial exception approved,
  `UW-OVR-002` Evidence rebuts decline reason.
- Every decision and override appends an `UnderwritingDecision` (source `MANUAL` or `OVERRIDE`) and an `AuditEntry`
  with `actorId`, `action`, `applicationId`, `reasonCode`, `comment`, `previousStatus`, `newStatus` and `timestamp`
  (NFR-04).

## Acceptance Criteria

- **AC-03.1** Given a valid TERM_LIFE quote and valid KYC, when a CUSTOMER submits an application, then it is stored,
  linked to the quote and customer, and moved to `UNDERWRITING` before evaluation.
- **AC-03.2** Given PAN `abcde1234f`, Aadhaar `1234-5678` or postcode `41100`, when submitted, then the response is 400
  naming each invalid KYC field and no application is stored.
- **AC-03.3** Given a quote past `validUntil`, when an application is submitted, then the response is 400
  `QUOTE_EXPIRED`.
- **AC-03.4** Given a date of birth that gives age 40 for a quote priced at age 35, when submitted, then the response is
  400 `KYC_AGE_MISMATCH`.
- **AC-04.1** Given TERM_LIFE age 35, non-smoker, BMI 24, no declarations, SI 5,000,000, when underwritten, then the
  decision is AUTO_BIND with `["UW-OK"]`.
- **AC-04.2** Given TERM_LIFE smoker with SI 12,000,000, when underwritten, then the decision is MANUAL_REVIEW with
  `UW-TL-002`.
- **AC-04.3** Given TERM_LIFE age 62 and BMI 37, when underwritten, then the decision is DECLINE with reason codes
  `["UW-TL-001","UW-TL-004"]` (decline beats refer; all fired codes listed).
- **AC-04.4** Given MOTOR with 3 claims, when underwritten, then DECLINE with `UW-MO-001`. Given HOUSEHOLD TIMBER, then
  MANUAL_REVIEW with `UW-HH-003`.
- **AC-04.5** Given a v2 rule set that changes the UW-TL-001 threshold to 65 and is active, when age 62 is underwritten,
  then the decision is not DECLINE and the stored decision references `ruleVersion = 2`.
- **AC-04.6** (reason-code matrix) For each v1 rule, a parameterised test case triggers only that rule, where the rule
  set allows it, and asserts the expected outcome and reason codes. Nested thresholds fire together: BMI 46 fires
  UW-TL-004 and UW-TL-005, giving DECLINE with `["UW-TL-004","UW-TL-005"]`.
- **AC-09.1** Given two REFERRED applications, when an UNDERWRITER gets the queue, then both are returned oldest first,
  with Aadhaar/PAN masked to their last 4 characters.
- **AC-09.2** Given a REFERRED application, when an UNDERWRITER approves with `UW-MAN-001` and a comment, then the status
  is `APPROVED` and an audit entry records `actorId = underwriter1`.
- **AC-09.3** Given a DECLINED application, when an ADMIN overrides with `UW-OVR-001` and comment "Commercial exception
  signed off", then the status is `APPROVED`, an `OVERRIDE` decision is appended (the original DECLINE row is kept),
  and an audit entry records actor, reason code and comment.
- **AC-09.4** Given a DECLINED application, when an UNDERWRITER (not ADMIN) calls override, then the response is 403.
  When the comment is missing or too short, then 400.
- **AC-09.5** Given an APPROVED application, when override is called, then the response is 409 (only DECLINED can be
  overridden).

## UI
- **Apply** page (from a quote): KYC form, then a status page showing the decision and reason codes.
- **Underwriter Workbench:** queue table, a case detail panel, and approve/decline with reason code and comment.
  ADMIN sees declined cases and an Override action.
