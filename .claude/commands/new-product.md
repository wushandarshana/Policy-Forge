---
description: Scaffold a new product or a new rule-set version (rules/<slug>/vN.json) plus the spec delta and red tests — no code changes to rating logic.
argument-hint: "<PRODUCT_CODE> <ratingModel TERM_LIFE|MOTOR|HOUSEHOLD> [--from <existing PRODUCT_CODE>] [--effective YYYY-MM-DD]"
allowed-tools: Read, Write, Glob, Grep, Bash(git:*), Bash(python:*)
---

# /new-product

Arguments: `$ARGUMENTS`

Publishing a product or version is a **data change, not a code change** (AC-02, specs/product-catalog_spec.md).

1. **Branch check.** You must be on a `feat/` branch. If on `main` or `dev`, stop and ask for a branch name (cut it from `dev`).
2. **Resolve the target.**
   - If `PRODUCT_CODE` exists under `backend/src/main/resources/rules/<slug>/`, this is a **new version**:
     N = max(vK) + 1. Copy `v(N-1).json` as the starting point.
   - Otherwise it is a **new product**: N = 1. Start from `--from` (an existing product with the same rating model),
     or from the matching v1 template in the spec.
   - Slug = lower-case kebab of the code (e.g. `TERM_LIFE` → `term-life`).
3. **Never modify existing `vK.json` files.** The `policy-immutability-check` hook will block it.
4. Ask the user for the factor and threshold changes. If they don't say, keep the values and only bump `version` and
   `effectiveFrom` (`--effective`, default today + 30 days).
5. Write `rules/<slug>/v<N>.json` with all numbers as **decimal strings**.
6. Run the `policy-version-validator` skill checklist. Fix any failure before continuing.
7. **Spec delta:** append to `specs/product-catalog_spec.md` § "Launch products" (or a new "Version history" table): the
   product, version, effectiveFrom and a summary of the changes. Commit it separately as `spec: <product> v<N>`.
8. **Worked example:** compute one quote with
   `python -I .claude/skills/premium-calc-evaluator/premium_check.py quote <PRODUCT> '<inputs>' --version <N>`.
9. **Red test:** add a test tagged `AC-02` that loads v<N>, asserts it becomes active on `effectiveFrom`, and checks the
   worked-example premium (AC-01.6 pattern).
10. Print a summary: file created, validator result, example premium, tests added, and next step (`/pr`).
