---
name: premium-calc-evaluator
description: Independently recompute PolicyForge premiums, endorsement adjustments and pro-rata refunds with exact decimals from the versioned rule files, and compare against the Java implementation or spec examples. Use when writing or reviewing spec worked examples, after any change to rating/refund code, or when a premium mismatch is reported.
---

# Premium calculation evaluator

An **independent oracle** for money math. It doesn't share code with the Java implementation, so agreement between the
two is meaningful evidence.

## When to use
- Before committing a spec that contains a money example (`specs/CLAUDE.md` requires it).
- In the evaluator phase of any sprint that touches `domain/rating`, `ProRataRefundCalculator` or
  `EndorsementPremiumAdjuster`.
- When a test reports a premium or refund mismatch (start of a fix loop).

## How
Run the bundled checker (Python standard library only, exact `decimal` arithmetic):

```bash
python -I .claude/skills/premium-calc-evaluator/premium_check.py quote TERM_LIFE '{"age":35,"smoker":false,"sumInsured":"5000000.00","termYears":20}'
python -I .claude/skills/premium-calc-evaluator/premium_check.py quote MOTOR '{"vehicleValue":"800000.00","vehicleAgeYears":2,"driverAge":30,"claimsLast3Years":0,"noClaimBonusYears":2,"vehicleUse":"PRIVATE"}'
python -I .claude/skills/premium-calc-evaluator/premium_check.py refund 6850.00 2026-11-01 2027-10-31 2027-02-09 500.00
python -I .claude/skills/premium-calc-evaluator/premium_check.py adjust 6850.00 9490.00 2026-11-01 2027-10-31 2027-05-01
python -I .claude/skills/premium-calc-evaluator/premium_check.py spec-examples
```

`quote` reads factors from `backend/src/main/resources/rules/<slug>/v<N>.json` (`--version N`, default the highest)
when the file exists, and falls back to the v1 tables from `specs/quote-engine_spec.md` otherwise.
`spec-examples` re-verifies every worked example in the specs (AC-01.1/2/3/6, AC-06.2, AC-08.1) and exits non-zero on
any mismatch.

## Rules the oracle encodes (must match the spec)
- Intermediate scale 10 with HALF_UP after each multiply or divide. Add the fee. **One** final round to 2 dp HALF_UP.
- Bands are inclusive at both ends.
- Days: `(end - start).days`, end exclusive. Term and unexpired periods use `termEnd + 1 day`.
- Refund: `max(0, premium × unexpired / termDays − fee)`. Full premium inside cooling-off.

## Reporting
For each case, output a table `case | expected (spec) | oracle | java (from test output) | status`. Any difference
of even 0.01 is a FAIL. Hand off to `quote-engine-agent` or `renewal-orchestrator-agent` with the failing case as a
reproducible test.
