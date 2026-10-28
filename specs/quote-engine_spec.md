# Feature Spec — Quote Engine

**Parent:** [`app_spec.md`](app_spec.md) · **ACs:** AC-01 · **NFR:** NFR-01
**Domain classes:** `PremiumCalculator` (facade), `TermLifeRatingModel`, `MotorRatingModel`, `HouseholdRatingModel`,
`PremiumBreakdown`

## Purpose
Return a deterministic, explainable premium for a product and insured profile, using the product's **active** rule
version.

## Request / response

`POST /api/quotes` (public):
```json
{ "productCode": "TERM_LIFE", "riskInputs": { "age": 35, "smoker": false, "sumInsured": "5000000.00",
  "termYears": 20, "bmi": "24.5", "healthDeclarations": [] } }
```
Response `201`:
```json
{ "quoteId": "Q-…", "productCode": "TERM_LIFE", "ruleVersion": 1, "sumInsured": "5000000.00",
  "annualPremium": "6850.00", "breakdown": [ {"factor":"baseRatePerThousand","value":"1.20"}, … ],
  "validUntil": "2026-11-27" }
```

Quotes are stored and immutable. The quote ID is `Q-<UUIDv4>` (unguessable, never sequential).
`GET /api/quotes/{id}` is public and returns the premium, breakdown, rule version and validity, but **omits health
inputs** (`bmi`, `healthDeclarations`) from `riskInputs` (NFR-04.2). The full inputs are only read server-side when
an application references the quote. A quote is valid for `lifecycle.quoteValidityDays` days.

## Arithmetic rules (NFR-01)
- All inputs and factors are parsed as `BigDecimal` from strings.
- Multiply in the order shown below. Intermediate results use scale 10 with HALF_UP. The final premium is rounded to
  scale 2 with HALF_UP **once**, after adding the policy fee.
- Bands are inclusive at both ends.

## Rating models (v1 factor tables)

### TERM_LIFE
Inputs: `age` (18–65), `smoker`, `sumInsured`, `termYears` ∈ {10, 20, 30}, `bmi`, `healthDeclarations[]`.

`premium = sumInsured / 1000 × baseRatePerThousand[ageBand] × smokerFactor × termFactor[termYears] + policyFee`

| Age band | 18–30 | 31–40 | 41–50 | 51–60 | 61–65 |
|---|---|---|---|---|---|
| baseRatePerThousand | 0.80 | 1.20 | 2.50 | 5.00 | 9.00 |

smokerFactor 1.50 / 1.00 · termFactor 10→1.00, 20→1.10, 30→1.25 · policyFee 250.00 · sumInsured 500,000–50,000,000.

### MOTOR
Inputs: `vehicleValue` (= sum insured), `vehicleAgeYears`, `driverAge`, `claimsLast3Years`, `noClaimBonusYears` (0–5),
`vehicleUse` ∈ {PRIVATE, COMMERCIAL}.

`premium = vehicleValue × baseRate × vehicleAgeFactor × driverAgeFactor × (1 + claimLoading × claimsLast3Years) × (1 − ncbDiscount[ncbYears]) × useFactor + policyFee`

baseRate 0.025 · vehicleAgeFactor 0–3→1.00, 4–7→1.10, 8–15→1.25 (above 15 is rejected by underwriting)
· driverAgeFactor 18–24→1.30, 25–65→1.00, 66–99→1.15 · claimLoading 0.20
· ncbDiscount 0→0.00, 1→0.20, 2→0.25, 3→0.35, 4→0.45, 5→0.50 · useFactor PRIVATE 1.00, COMMERCIAL 1.40
· policyFee 150.00 · sumInsured 100,000–10,000,000.

### HOUSEHOLD
Inputs: `buildingSumInsured`, `contentsSumInsured`, `propertyAgeYears`, `constructionType` ∈ {BRICK, CONCRETE, TIMBER},
`floodZone` ∈ {LOW, MEDIUM, HIGH}, `hasSecuritySystem`, `hasFloodDefences`. Sum insured = building + contents.

`premium = (buildingSumInsured × buildingRate + contentsSumInsured × contentsRate) × constructionFactor × floodFactor × securityFactor + policyFee`

buildingRate 0.0015 · contentsRate 0.0035 · construction BRICK 1.00, CONCRETE 0.95, TIMBER 1.30
· flood LOW 1.00, MEDIUM 1.15, HIGH 1.40 · security with system 0.90, without 1.00 · policyFee 100.00
· sumInsured 200,000–50,000,000.

## Acceptance Criteria

- **AC-01.1** Given TERM_LIFE v1, age 35, non-smoker, sum insured 5,000,000.00, 20-year term, when quoted, then the
  annual premium is **6850.00** and the quote records `ruleVersion = 1`.
- **AC-01.2** Given MOTOR v1, vehicle value 800,000.00, vehicle age 2, driver age 30, 0 claims, 2 NCB years, PRIVATE,
  when quoted, then the premium is **15150.00**.
- **AC-01.3** Given HOUSEHOLD v1, building 6,000,000.00, contents 1,000,000.00, BRICK, LOW flood zone, with a security
  system, when quoted, then the premium is **11350.00**.
- **AC-01.4** Given the same product, inputs and rule version, when quoted 100 times, then all 100 premiums are equal
  (determinism).
- **AC-01.5** Given TERM_LIFE age 30 versus 31 (band boundary), when quoted, then base rates 0.80 and 1.20 are applied
  respectively.
- **AC-01.6** Given a MOTOR v2 rule set with baseRate 0.030 effective today, when the AC-01.2 input is quoted, then the
  premium is computed from v2 (18150.00) and the quote records `ruleVersion = 2`.
- **AC-01.7** Given sum insured outside the product min/max, or a missing required risk input, when quoted, then the
  response is 400 naming each invalid field.
- **AC-01.8** Given any quote, when the breakdown is returned, then it lists every applied factor, and recomputing the
  formula from the breakdown gives the same premium.

## UI
A **Quote** page: choose a product, see a dynamic form for that product's risk inputs, submit, then see the premium,
breakdown and rule version, with an "Apply" call to action.
