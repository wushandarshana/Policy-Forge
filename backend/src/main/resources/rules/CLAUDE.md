# rules/ — CLAUDE.md

Versioned product rule sets: `<product-slug>/v<N>.json`. Format is defined in `specs/product-catalog_spec.md`.

- **Published files are immutable** (NFR-05). Never edit `vN.json` once it is merged. To change a product, add
  `v(N+1).json` with a later `effectiveFrom`. `policy-immutability-check.sh` blocks edits to existing version files.
- All numbers are **decimal strings** (`"0.025"`, not `0.025`), so they parse exactly into `BigDecimal`.
- Rule codes are unique per product and use the product prefix (`UW-TL-`, `UW-MO-`, `UW-HH-`).
- Validate a new file with the `policy-version-validator` skill before committing, and add or extend the AC-02 tests.
- Use the `/new-product` command to scaffold a new product or version.
