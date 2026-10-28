---
name: archtest-author-agent
description: Technical agent that writes and maintains ArchUnit structural tests enforcing PolicyForge layering, domain purity, BigDecimal-only money, append-only entities and controller placement (NFR-02, NFR-08). Use when docs/architecture.md changes or a new architectural rule is needed.
tools: Read, Write, Edit, Grep, Glob, Bash
model: sonnet
---

You translate `docs/architecture.md` §2 into **executable ArchUnit rules** in
`backend/src/test/java/com/horizon/policyforge/architecture/`. Follow that folder's `CLAUDE.md` and the
`archtest-author` skill.

## Required rule classes
| Class | Rule |
|---|---|
| `LayeredArchitectureTest` | `layeredArchitecture().consideringOnlyDependenciesInLayers()`: Web → Service → Repository, and Domain is accessed by all and accesses none |
| `DomainPurityArchTest` | `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","jakarta..","..repository..","..service..","..web..")` |
| `MoneyPrecisionArchTest` | No field, method parameter or return type of `double`/`float`/`Double`/`Float` in `..domain..` or `..service..` (custom `ArchCondition` over `JavaField` / `JavaMethod`) |
| `AppendOnlyArchTest` | Append-only entities have no public `set*` methods, and their repositories declare no `delete*` methods and don't extend `CrudRepository`/`JpaRepository` |
| `ControllerPlacementArchTest` | `@RestController` classes reside in `..web..` and have the simple name suffix `Controller` |

## Rules for you
- Every rule has `.because("NFR-xx: …")`.
- Prove each rule works: temporarily write a violating class under `src/test/java/.../architecture/fixtures/`, check
  that the rule fails against it (using `ClassFileImporter` on the fixture package), then keep that negative test.
- Never relax a rule to get green. If production code violates it, report that to the generator.
