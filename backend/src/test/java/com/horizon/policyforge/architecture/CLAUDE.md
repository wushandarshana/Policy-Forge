# architecture/ (tests) — CLAUDE.md

ArchUnit structural tests (NFR-08). They are the executable form of `docs/architecture.md` §2.

- One rule theme per class: `LayeredArchitectureTest`, `DomainPurityArchTest`, `MoneyPrecisionArchTest`,
  `AppendOnlyArchTest`, `ControllerPlacementArchTest`.
- Import with `@AnalyzeClasses(packages = "com.horizon.policyforge", importOptions = ImportOption.DoNotIncludeTests.class)`.
- Every rule has a `because("…NFR-NN…")` that names the requirement it protects.
- **Never weaken a rule to make a build pass.** If a rule is genuinely wrong, change `docs/architecture.md` first
  (spec is truth), then the rule. Use the `archtest-author` skill or agent to add rules.
