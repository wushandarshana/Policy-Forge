# Feature Spec — Admin Portfolio Dashboard

**Parent:** [`app_spec.md`](app_spec.md) · **ACs:** AC-11 · **NFR:** NFR-01, NFR-04

## Purpose
Give operations a portfolio snapshot: what's in force, what's been collected, what's renewing and what's likely to
lapse.

## API
`GET /api/admin/dashboard?asOf=YYYY-MM-DD` (ADMIN; `asOf` defaults to today):

```json
{ "asOf": "2027-10-15",
  "activePoliciesByProduct": { "TERM_LIFE": 3, "MOTOR": 2, "HOUSEHOLD": 2 },
  "premiumCollected": { "TERM_LIFE": "20550.00", "MOTOR": "30300.00", "HOUSEHOLD": "22700.00", "total": "73550.00" },
  "renewalPipeline": [ { "policyNumber": "PF-TL-2026-000001", "termEnd": "2027-10-31", "renewalPremium": "6850.00", "paid": false } ],
  "lapseForecast": { "count": 1, "policies": [ "PF-MO-2026-000002" ] } }
```

Definitions:
- **Active:** status in {ACTIVE, ENDORSED, RENEWED}.
- **Premium collected:** per product, `Σ paid term premiums + Σ endorsement adjustments (signed: charges are
  positive, credits negative) − Σ refunds`. All `BigDecimal`.
- **Renewal pipeline:** in-force policies whose current term ends within the next 60 days of `asOf`, with renewal
  offer status.
- **Lapse forecast:** in-force policies whose renewal is unpaid and whose `termEnd < asOf`. They are in grace now and
  will lapse if unpaid.

## Acceptance Criteria

- **AC-11.1** Given the seed portfolio, when an ADMIN loads the dashboard, then active counts per product equal the
  number of in-force seed policies per product.
- **AC-11.2** Given policies with paid terms, an endorsement adjustment of +1330.85 and a refund of 4473.29, then
  `premiumCollected.total` equals the exact decimal sum (no floating-point drift).
- **AC-11.3** Given a policy whose term ends in 20 days, then it appears in `renewalPipeline`. Given one ending in
  90 days, then it does not.
- **AC-11.4** Given a policy past termEnd with an unpaid renewal and still within grace, then it appears in
  `lapseForecast`.
- **AC-11.5** Given a CUSTOMER or UNDERWRITER, when the dashboard is requested, then 403.

## UI
The **Admin Dashboard** has KPI tiles (active policies, premium collected), a per-product breakdown table, a
renewal-pipeline table and a lapse-forecast list. It is responsive: tiles wrap to one column under 640 px.
