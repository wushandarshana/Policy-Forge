---
name: archtest-author
description: Write ArchUnit rules for PolicyForge from docs/architecture.md — layered architecture, domain purity, no floating-point money, append-only entities/repositories, controller placement — each with a negative fixture proving the rule bites. Use when adding or changing an architectural constraint (NFR-08).
---

# ArchTest author

## Dependencies (backend/pom.xml)
```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version>
  <scope>test</scope>
</dependency>
```

## Templates

**Layering**
```java
@AnalyzeClasses(packages = "com.horizon.policyforge", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {
  @ArchTest static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
      .layer("Web").definedBy("..web..")
      .layer("Service").definedBy("..service..")
      .layer("Repository").definedBy("..repository..")
      .layer("Domain").definedBy("..domain..")
      .whereLayer("Web").mayNotBeAccessedByAnyLayer()
      .whereLayer("Service").mayOnlyBeAccessedByLayers("Web")
      .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
      .whereLayer("Domain").mayOnlyBeAccessedByLayers("Web", "Service", "Repository")
      .because("docs/architecture.md §2 / NFR-08");
}
```

**No floating-point money (NFR-01)**: a custom condition over fields and method signatures:
```java
static final DescribedPredicate<JavaClass> FLOATING =
    DescribedPredicate.describe("floating point", c -> Set.of("double","float","java.lang.Double","java.lang.Float").contains(c.getName()));
@ArchTest static final ArchRule noFloatFields = noFields().that().areDeclaredInClassesThat().resideInAnyPackage("..domain..","..service..")
    .should().haveRawType(FLOATING).because("NFR-01: money must be BigDecimal");
@ArchTest static final ArchRule noFloatReturns = noMethods().that().areDeclaredInClassesThat().resideInAnyPackage("..domain..","..service..")
    .should().haveRawReturnType(FLOATING).because("NFR-01");
```
Also add a `methods().should(notHaveFloatingParameters())` custom `ArchCondition<JavaMethod>`.

**Append-only (NFR-02)**
```java
@ArchTest static final ArchRule noSetters = noMethods().that().areDeclaredInClassesThat(APPEND_ONLY_ENTITIES)
    .and().arePublic().should().haveNameMatching("set[A-Z].*").because("NFR-02: append-only");
@ArchTest static final ArchRule noDeletes = noMethods().that().areDeclaredInClassesThat(APPEND_ONLY_REPOSITORIES)
    .should().haveNameMatching("delete.*|remove.*").because("NFR-02");
@ArchTest static final ArchRule notCrud = noClasses().that(APPEND_ONLY_REPOSITORIES)
    .should().beAssignableTo(CrudRepository.class).because("NFR-02: CrudRepository exposes delete");
```
`APPEND_ONLY_ENTITIES` is a `DescribedPredicate` over the simple-name list in `repository/CLAUDE.md`.

## Prove the rule bites
For each rule, add a fixture under `src/test/java/com/horizon/policyforge/architecture/fixtures/` that violates it
(e.g. `class BadMoney { double premium; }`). Add a test that imports **only** the fixtures package with
`new ClassFileImporter().importPackages("…fixtures")` and asserts that `rule.evaluate(classes).hasViolation()` is true.
Exclude the fixtures from the main `@AnalyzeClasses` scan.

## Done when
`./mvnw -q test -Dtest='*ArchTest,*ArchitectureTest'` is green against production code, and the negative tests prove
each rule fails on its fixture.
