---
name: policy-version-validator
description: Check a PolicyForge product rule-set JSON (rules/<product>/vN.json) for schema completeness, decimal-string numbers, contiguous bands, unique prefixed rule codes, valid operators, and version/effectiveFrom ordering; confirm published versions are unmodified. Use before committing or reviewing any rule file.
---

# Policy version validator

## Checklist (all must pass)

**Identity and ordering**
- [ ] The file path is `backend/src/main/resources/rules/<slug>/v<N>.json`, and the JSON `version == N`.
- [ ] `N == max(existing versions for product) + 1`, with no gaps.
- [ ] `effectiveFrom` (ISO date) is later than the previous version's `effectiveFrom`.
- [ ] `productCode` matches the slug (`term-life` ↔ TERM_LIFE, `motor` ↔ MOTOR, `household` ↔ HOUSEHOLD).
- [ ] `ratingModel` ∈ {TERM_LIFE, MOTOR, HOUSEHOLD}.

**Numbers (NFR-01)**
- [ ] Every numeric factor, rate, fee and limit is a **JSON string** (e.g. `"0.025"`). Integers used as band edges or
      day counts may be JSON numbers.
- [ ] `sumInsured.min < sumInsured.max`, both > 0.
- [ ] Band tables (`[{min,max,factor}]`) are sorted, contiguous (next.min = prev.max + 1) and non-overlapping.

**Underwriting rules**
- [ ] Each rule has `code`, `outcome` ∈ {DECLINE, REFER}, `description` and a non-empty `all[]`.
- [ ] Codes are unique and start with the product prefix (`UW-TL-`, `UW-MO-`, `UW-HH-`).
- [ ] Each condition has `field`, an `op` ∈ {EQ, NEQ, GT, GTE, LT, LTE, IN, NOT_EMPTY, IS_TRUE}, and a `value` where
      the op needs one.
- [ ] Each `field` is a known risk input for the product, or a derived field (`sumInsured`, `age`).

**Lifecycle**
- [ ] `termMonths`, `gracePeriodDays`, `coolingOffDays`, `renewalOfferDaysBeforeExpiry` and `quoteValidityDays` are
      present, as integers ≥ 0. `cancellationFee` is a decimal string ≥ 0.

**Immutability (NFR-05)**
- [ ] `git diff --name-status main...HEAD -- backend/src/main/resources/rules/` shows only `A` for this file. Any
      `M` or `D` on an existing `vN.json` fails.

## Quick mechanical check
```bash
python -I -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); print(d['productCode'], d['version'], d['effectiveFrom'], len(d['underwriting']['rules']), 'rules')" backend/src/main/resources/rules/motor/v1.json
```
Then recompute one worked example with the `premium-calc-evaluator` skill (`quote … --version N`) and add a test for
the new version (AC-02.3 / AC-01.6 pattern).
