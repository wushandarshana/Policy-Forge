# TASK-004 Progress

## Status
DONE

## What was built
Five ArchUnit test classes now encode docs/architecture.md section 2, each rule with a `because("NFR-..: ...")` and each paired with a negative-fixture test. `LayeredArchitectureTest` holds the layer matrix, `DomainPurityArchTest` has a forbidden-framework rule and a JDK-only rule, `MoneyPrecisionArchTest` has four rules (fields, returns, parameters, BigDecimal to or from double or float calls), and `ControllerPlacementArchTest` has placement and naming rules. `AppendOnlyArchTest` keeps the TASK-003 AC-02.6 and audit checks (they still require the class to exist and fail when it is absent or mutable). It adds set-wide rules for the seven entities listed in docs/architecture.md plus `QuoteEntity` (J11): `@Entity` and `@Immutable` in `..repository..`, no public setters, no public fields, repositories only extend `Repository` (never Crud, ListCrud, PagingAndSorting, ListPagingAndSorting or Jpa), and no `delete*`, `remove*`, `update*` or `@Modifying` methods. Production rules run as `@ArchTest` fields under `@AnalyzeClasses(... DoNotIncludeTests)`. Negative fixtures are real classes under `src/test/java/com/horizon/archfixtures/**`, outside the application base package so Spring component, entity and repository scanning never sees them, and excluded from the production import.

Evidence: `./mvnw -B -ntp clean verify` exits 0 with 112 tests passing (surefire 67 plus failsafe 45, up from 74), and the JaCoCo checks pass (70 overall, 80 domain, unchanged). The architecture tests number 42 and all pass against current production code. The red log shows all 17 rule-versus-fixture checks failing, including the AC-02.6 check failing on an absent `RuleSetVersionEntity`.

## Files changed
Architecture tests (`backend/src/test/java/com/horizon/policyforge/architecture/`):
- ArchSupport.java (new, shared production and fixture import helpers)
- LayeredArchitectureTest.java (new)
- DomainPurityArchTest.java (new)
- MoneyPrecisionArchTest.java (new)
- ControllerPlacementArchTest.java (new)
- AppendOnlyArchTest.java (extended, AC-02.6 and audit tests kept with the same display names)

Negative fixtures (`backend/src/test/java/com/horizon/archfixtures/`, all new):
- layers/{web,service,repository,domain,config}/*  (violating and allowed dependency classes plus layer markers)
- purity/{domain,web}/*
- money/domain/*
- controllers/{web,service,repository}/*
- appendonly/{setter,notimmutable,publicfield,crud,jpa,delete,update,notrepo,absent,clean}/repository/*

Evidence (sprint workspace):
- .sprint/20261028-052716/sprint-progress/TASK-004-red.log
- .sprint/20261028-052716/sprint-progress/TASK-004-green.log

No production code changed. No production class violated any rule.

## Open issues
- **Layer patterns are rooted at `com.horizon..`.** A first draft used the bare `..web..` / `..config..` patterns from the skill template. They also match Spring packages such as `org.springframework.security.config` and `...annotation.web...`, which produced false violations on `SecurityConfig`. Rules now use `com.horizon..<layer>..`, which matches production and the fixtures but not third-party packages. This is a precision fix, not a weakening.
- **Red log method.** The rules and fixtures were written together, so there was no earlier moment when the rules existed but did not fail. The red log comes from a temporary class that ran each production rule constant against its fixture without asserting the failure (deleted after capture; the log header explains this). The permanent tests assert the same rejection and also check the report names the intended violating classes and not the allowed ones.
- **Rules that can't fail yet.** The set-wide append-only rules use `allowEmptyShould(true)`, because `QuoteEntity` (TASK-006) and the other future entities do not exist yet. They bite as soon as such a class appears. The AC-02.6 and audit checks are not empty-tolerant. TASK-006 should add `QuoteEntity` and `QuoteRepository` following these rules (`@Entity`, `@Immutable`, no setters, repository extends `Repository`).
- **Docs gap.** The append-only bullet in docs/architecture.md does not list `QuoteEntity` (repository/CLAUDE.md does). Docs are human-edited substrate, so it is left for a human to align.
- **Stricter than the doc in places.** The money rules cover `repository` and `web` as well as `domain` and `service`. The layer matrix lets `config` depend on anything, but the web, service and repository layers may not depend on `config`, following the doc's matrix literally. `update*` repository method names are also forbidden. If a later task needs web to use `config`, change docs/architecture.md first.
- **Mixed engines.** The test classes mix `@ArchTest` fields (ArchUnit engine) and `@Test` methods (Jupiter), so an ArchUnit-engine test displays under its field name (e.g. `layersFollowTheMatrix`), not an AC id. The only AC-tagged display names here are the retained AC-02.6 ones.
- **Environment.** `JAVA_HOME` was set per command as in the plan. Large heredocs in the Windows shell failed again, so the Java files were written with the Write tool. No git add, commit or push was made.
