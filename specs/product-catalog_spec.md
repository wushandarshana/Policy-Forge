# Feature Spec — Product Catalog & Versioned Rule Sets

**Parent:** [`app_spec.md`](app_spec.md) · **ACs:** AC-02 · **Domain classes:** `RuleSetVersion`, `RuleSetValidator`

## Purpose
Hold each product's rating factors, underwriting rules and lifecycle parameters as **versioned, immutable data**, so
products can be added or repriced without code changes.

## Rule file format

Location: `backend/src/main/resources/rules/<product-slug>/v<N>.json`. On startup the files are loaded into the
`rule_set_version` table if absent (by `productCode` + `version`). Published versions can also be created through the
API. The `ratingModel` picks one of the three rating algorithms in `domain/rating/`.

```json
{
  "productCode": "TERM_LIFE",
  "productName": "Term Life Protect",
  "version": 1,
  "effectiveFrom": "2026-01-01",
  "ratingModel": "TERM_LIFE",
  "currency": "INR",
  "sumInsured": { "min": "500000.00", "max": "50000000.00" },
  "rating": { "...model-specific factor tables, all numbers as decimal strings..." },
  "underwriting": {
    "rules": [
      { "code": "UW-TL-001", "outcome": "DECLINE", "description": "Entry age above 60",
        "all": [ { "field": "age", "op": "GT", "value": "60" } ] }
    ]
  },
  "lifecycle": {
    "termMonths": 12, "gracePeriodDays": 30, "coolingOffDays": 15,
    "cancellationFee": "500.00", "renewalOfferDaysBeforeExpiry": 30, "quoteValidityDays": 30
  }
}
```

The condition operators are `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `NOT_EMPTY` and `IS_TRUE`. A rule fires when
**all** its conditions hold. Numeric comparisons use `BigDecimal`.

### Launch products (v1)

| Product | Slug | Rating model | Grace | Cooling-off | Cancel fee |
|---|---|---|---|---|---|
| TERM_LIFE | `term-life` | TERM_LIFE | 30 d | 15 d | 500.00 |
| MOTOR | `motor` | MOTOR | 15 d | 7 d | 250.00 |
| HOUSEHOLD | `household` | HOUSEHOLD | 15 d | 14 d | 200.00 |

The factor tables are defined in [`quote-engine_spec.md`](quote-engine_spec.md). The underwriting rules are defined
in [`underwriting_spec.md`](underwriting_spec.md).

## Behaviour
- **Active version:** the highest `version` with `effectiveFrom <= today` (from the clock). If none exists,
  `NoActiveRuleSetException` (404).
- **Publish:** `POST /api/products/{code}/versions` (PRODUCT_MANAGER) with a rule JSON body. Version = current max + 1.
  The body is validated by `RuleSetValidator` (required sections, min < max, known operators, unique rule codes,
  non-negative decimals). The stored record is immutable. The publish is audited.
- **New product:** publishing version 1 for an unknown `productCode` creates the product, if `ratingModel` is one of
  the three known models.
- There is no update or delete endpoint. The JPA entity has no setters, and the repository exposes no delete.

## Acceptance Criteria

- **AC-02.1** Given a fresh start, when `GET /api/products` is called, then it returns exactly TERM_LIFE, MOTOR and
  HOUSEHOLD, each with active version 1 and a distinct rating model.
- **AC-02.2** Given the three v1 rule sets, when they are compared, then each has different rating factors, different
  underwriting rule codes (prefixes `UW-TL-`, `UW-MO-`, `UW-HH-`) and different lifecycle parameters.
- **AC-02.3** Given MOTOR v1 is active, when a PRODUCT_MANAGER publishes a new MOTOR rule set, then it is stored as
  version 2 and v1 is unchanged byte for byte.
- **AC-02.4** Given MOTOR v2 has `effectiveFrom` in the future, when the active version is resolved today, then v1 is
  returned. When the clock reaches `effectiveFrom`, then v2 is returned.
- **AC-02.5** Given a rule set with `sumInsured.min > sumInsured.max`, an unknown operator or duplicate rule codes,
  when it is published, then the response is 400 listing each violation and nothing is stored.
- **AC-02.6** Given a stored rule set version, when any code path attempts to modify it, then this is impossible: the
  entity has no mutators and there is no repository delete (enforced by the ArchUnit `AppendOnlyArchTest`).
- **AC-02.7** Given a CUSTOMER, when they `POST /api/products/MOTOR/versions`, then the response is 403.

## UI
A **Catalog Manager** page (PRODUCT_MANAGER) lists products and versions, shows a version's JSON read-only, and
publishes a new version from an edited copy.
