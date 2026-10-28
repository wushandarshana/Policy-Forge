package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;

/**
 * Append-only persistence (NFR-02, NFR-05).
 *
 * <p>Two kinds of rule live here. The AC-02.6 checks (and the audit entry checks) <em>require</em> their entity or
 * repository to exist: ArchUnit fails a rule that matches no classes, so they fail while the class is absent and again
 * if it becomes mutable. The set-wide rules cover every append-only entity named in docs/architecture.md and
 * repository/CLAUDE.md, plus {@code QuoteEntity} (J11); they allow entities that later sprints have not created yet, and
 * start to bite the moment one appears.
 */
@Tag("AC-02")
@Tag("NFR-02")
@Tag("NFR-08")
@AnalyzeClasses(packages = ArchSupport.PRODUCTION_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class AppendOnlyArchTest {

    /** The seven entities in docs/architecture.md section 2, plus QuoteEntity (J11). */
    static final List<String> APPEND_ONLY_ENTITIES = List.of(
            "PolicyVersionEntity", "EndorsementEntity", "PolicyStatusTransitionEntity", "RefundRecordEntity",
            "AuditEntryEntity", "RuleSetVersionEntity", "UnderwritingDecisionEntity", "QuoteEntity");

    private static final List<String> APPEND_ONLY_REPOSITORIES = APPEND_ONLY_ENTITIES.stream()
            .map(name -> name.substring(0, name.length() - "Entity".length()) + "Repository")
            .toList();

    private static final DescribedPredicate<JavaClass> APPEND_ONLY_ENTITY =
            DescribedPredicate.describe("append-only entities",
                    type -> APPEND_ONLY_ENTITIES.contains(type.getSimpleName()));

    private static final DescribedPredicate<JavaClass> APPEND_ONLY_REPOSITORY =
            DescribedPredicate.describe("append-only repositories",
                    type -> APPEND_ONLY_REPOSITORIES.contains(type.getSimpleName()));

    // ---- Rules that require a named class to exist (AC-02.6 and the audit entry) ----

    static ArchRule entityIsImmutable(String simpleName, String nfr) {
        return classes().that().haveSimpleName(simpleName)
                .should().resideInAPackage("com.horizon..repository..")
                .andShould().beAnnotatedWith(Entity.class)
                .andShould().beAnnotatedWith(Immutable.class)
                .because(nfr + ": " + simpleName + " is append-only, a new fact is a new row");
    }

    static ArchRule entityHasNoPublicSetters(String simpleName, String nfr) {
        return noMethods().that().areDeclaredInClassesThat().haveSimpleName(simpleName)
                .and().arePublic()
                .should().haveNameMatching("set[A-Z].*")
                .because(nfr + ": " + simpleName + " must have no public setters");
    }

    static ArchRule repositoryIsNotCrud(String simpleName, String nfr) {
        return classes().that().haveSimpleName(simpleName)
                .should().resideInAPackage("com.horizon..repository..")
                .andShould().beInterfaces()
                .andShould().beAssignableTo(Repository.class)
                .andShould().notBeAssignableTo(CrudRepository.class)
                .andShould().notBeAssignableTo(ListCrudRepository.class)
                .andShould().notBeAssignableTo(PagingAndSortingRepository.class)
                .andShould().notBeAssignableTo(ListPagingAndSortingRepository.class)
                .andShould().notBeAssignableTo(JpaRepository.class)
                .because(nfr + ": " + simpleName + " extends Repository, never a CRUD or JPA repository that exposes delete");
    }

    static ArchRule repositoryHasNoDeleteOrUpdate(String simpleName, String nfr) {
        return noMethods().that().areDeclaredInClassesThat().haveSimpleName(simpleName)
                .should().haveNameMatching("(delete|remove|update).*")
                .orShould().beAnnotatedWith(Modifying.class)
                .because(nfr + ": " + simpleName + " has no delete or update methods");
    }

    // ---- Set-wide rules (every append-only entity and repository, whichever exist) ----

    static final ArchRule ENTITIES_ARE_IMMUTABLE = classes().that(APPEND_ONLY_ENTITY)
            .should().resideInAPackage("com.horizon..repository..")
            .andShould().beAnnotatedWith(Entity.class)
            .andShould().beAnnotatedWith(Immutable.class)
            .allowEmptyShould(true)
            .because("NFR-02/NFR-05: policy versions, endorsements, transitions, refunds, audit entries, rule-set "
                    + "versions, underwriting decisions and quotes are append-only, a new fact is a new row");

    static final ArchRule ENTITIES_HAVE_NO_PUBLIC_SETTERS = noMethods().that().areDeclaredInClassesThat(APPEND_ONLY_ENTITY)
            .and().arePublic()
            .should().haveNameMatching("set[A-Z].*")
            .allowEmptyShould(true)
            .because("NFR-02: append-only entities have no public setters");

    static final ArchRule ENTITIES_HAVE_NO_PUBLIC_FIELDS = fields().that().areDeclaredInClassesThat(APPEND_ONLY_ENTITY)
            .and().areNotStatic()
            .should().notBePublic()
            .allowEmptyShould(true)
            .because("NFR-02: a public field is a setter by another name, so append-only entities hide their fields");

    static final ArchRule REPOSITORIES_ARE_NOT_CRUD = classes().that(APPEND_ONLY_REPOSITORY)
            .should().beInterfaces()
            .andShould().beAssignableTo(Repository.class)
            .andShould().notBeAssignableTo(CrudRepository.class)
            .andShould().notBeAssignableTo(ListCrudRepository.class)
            .andShould().notBeAssignableTo(PagingAndSortingRepository.class)
            .andShould().notBeAssignableTo(ListPagingAndSortingRepository.class)
            .andShould().notBeAssignableTo(JpaRepository.class)
            .allowEmptyShould(true)
            .because("NFR-02: append-only repositories extend Repository, never a CRUD or JPA repository that exposes delete");

    static final ArchRule REPOSITORIES_HAVE_NO_DELETE_OR_UPDATE = noMethods().that()
            .areDeclaredInClassesThat(APPEND_ONLY_REPOSITORY)
            .should().haveNameMatching("(delete|remove|update).*")
            .orShould().beAnnotatedWith(Modifying.class)
            .allowEmptyShould(true)
            .because("NFR-02: append-only repositories declare no delete*, remove*, update* or @Modifying methods");

    @ArchTest
    static final ArchRule appendOnlyEntitiesAreImmutable = ENTITIES_ARE_IMMUTABLE;

    @ArchTest
    static final ArchRule appendOnlyEntitiesHaveNoPublicSetters = ENTITIES_HAVE_NO_PUBLIC_SETTERS;

    @ArchTest
    static final ArchRule appendOnlyEntitiesHaveNoPublicFields = ENTITIES_HAVE_NO_PUBLIC_FIELDS;

    @ArchTest
    static final ArchRule appendOnlyRepositoriesAreNotCrud = REPOSITORIES_ARE_NOT_CRUD;

    @ArchTest
    static final ArchRule appendOnlyRepositoriesHaveNoDeleteOrUpdate = REPOSITORIES_HAVE_NO_DELETE_OR_UPDATE;

    // ---- AC-02.6 and audit entry checks against production code ----

    @Test
    @DisplayName("AC-02.6: the rule-set version entity exists, is an immutable JPA entity and has no public setters")
    void ruleSetVersionEntityIsImmutable() {
        entityIsImmutable("RuleSetVersionEntity", "NFR-05").check(ArchSupport.production());
        entityHasNoPublicSetters("RuleSetVersionEntity", "NFR-05").check(ArchSupport.production());
    }

    @Test
    @DisplayName("AC-02.6: the rule-set version repository exists and has no delete or update methods")
    void ruleSetVersionRepositoryHasNoDelete() {
        repositoryIsNotCrud("RuleSetVersionRepository", "NFR-05").check(ArchSupport.production());
        repositoryHasNoDeleteOrUpdate("RuleSetVersionRepository", "NFR-05").check(ArchSupport.production());
    }

    @Test
    @DisplayName("NFR-02 audit: the audit entry entity is immutable with no public setters")
    void auditEntryEntityIsImmutable() {
        entityIsImmutable("AuditEntryEntity", "NFR-02").check(ArchSupport.production());
        entityHasNoPublicSetters("AuditEntryEntity", "NFR-02").check(ArchSupport.production());
    }

    @Test
    @DisplayName("NFR-02 audit: the audit entry repository has no delete or update methods")
    void auditEntryRepositoryHasNoDelete() {
        repositoryIsNotCrud("AuditEntryRepository", "NFR-02").check(ArchSupport.production());
        repositoryHasNoDeleteOrUpdate("AuditEntryRepository", "NFR-02").check(ArchSupport.production());
    }

    // ---- Negative fixtures: every rule must reject a deliberately violating class ----

    @Test
    @DisplayName("AC-02.6 negative fixture: the checks fail when the rule-set version entity and repository are absent")
    void ruleSetVersionChecksFailWhenTheClassesAreAbsent() {
        var absent = ArchSupport.fixtures("appendonly.absent");
        ArchSupport.assertRuleRejects(entityIsImmutable("RuleSetVersionEntity", "NFR-05"), absent);
        ArchSupport.assertRuleRejects(entityHasNoPublicSetters("RuleSetVersionEntity", "NFR-05"), absent);
        ArchSupport.assertRuleRejects(repositoryIsNotCrud("RuleSetVersionRepository", "NFR-05"), absent);
        ArchSupport.assertRuleRejects(repositoryHasNoDeleteOrUpdate("RuleSetVersionRepository", "NFR-05"), absent);
    }

    @Test
    @DisplayName("AC-02.6 negative fixture: a rule-set version entity with a public setter is rejected")
    void ruleSetVersionEntityWithSetterIsRejected() {
        ArchSupport.assertRuleRejects(entityHasNoPublicSetters("RuleSetVersionEntity", "NFR-05"),
                ArchSupport.fixtures("appendonly.setter")).hasMessageContaining("setPayload");
    }

    @Test
    @DisplayName("AC-02.6 negative fixture: a rule-set version entity that is not @Immutable is rejected")
    void ruleSetVersionEntityWithoutImmutableIsRejected() {
        ArchSupport.assertRuleRejects(entityIsImmutable("RuleSetVersionEntity", "NFR-05"),
                ArchSupport.fixtures("appendonly.notimmutable")).hasMessageContaining("Immutable");
    }

    @Test
    @DisplayName("AC-02.6 negative fixture: a rule-set version repository with a delete method is rejected")
    void ruleSetVersionRepositoryWithDeleteIsRejected() {
        ArchSupport.assertRuleRejects(repositoryHasNoDeleteOrUpdate("RuleSetVersionRepository", "NFR-05"),
                ArchSupport.fixtures("appendonly.delete")).hasMessageContaining("deleteByProductCode");
    }

    @Test
    @DisplayName("AC-02.6 negative fixture: a CRUD rule-set version repository is rejected")
    void ruleSetVersionCrudRepositoryIsRejected() {
        ArchSupport.assertRuleRejects(repositoryIsNotCrud("RuleSetVersionRepository", "NFR-05"),
                ArchSupport.fixtures("appendonly.crud")).hasMessageContaining("CrudRepository");
    }

    @Test
    @DisplayName("NFR-02 negative fixture: the clean fixture passes every set-wide rule")
    void cleanFixturePassesEverySetWideRule() {
        var clean = ArchSupport.fixtures("appendonly.clean");
        ENTITIES_ARE_IMMUTABLE.check(clean);
        ENTITIES_HAVE_NO_PUBLIC_SETTERS.check(clean);
        ENTITIES_HAVE_NO_PUBLIC_FIELDS.check(clean);
        REPOSITORIES_ARE_NOT_CRUD.check(clean);
        REPOSITORIES_HAVE_NO_DELETE_OR_UPDATE.check(clean);
    }

    @Test
    @DisplayName("NFR-02 negative fixture: public setters on RuleSetVersionEntity and QuoteEntity are rejected")
    void publicSettersAreRejected() {
        ArchSupport.assertRuleRejects(ENTITIES_HAVE_NO_PUBLIC_SETTERS, ArchSupport.fixtures("appendonly.setter"))
                .hasMessageContaining("RuleSetVersionEntity.setPayload")
                .hasMessageContaining("QuoteEntity.setStatus");
    }

    @Test
    @DisplayName("NFR-02 negative fixture: an append-only entity without @Immutable is rejected")
    void entityWithoutImmutableIsRejected() {
        ArchSupport.assertRuleRejects(ENTITIES_ARE_IMMUTABLE, ArchSupport.fixtures("appendonly.notimmutable"))
                .hasMessageContaining("RuleSetVersionEntity");
    }

    @Test
    @DisplayName("NFR-02 negative fixture: a public field on an append-only entity is rejected")
    void publicFieldIsRejected() {
        ArchSupport.assertRuleRejects(ENTITIES_HAVE_NO_PUBLIC_FIELDS, ArchSupport.fixtures("appendonly.publicfield"))
                .hasMessageContaining("RuleSetVersionEntity.payload");
    }

    @Test
    @DisplayName("NFR-02 negative fixture: CrudRepository, JpaRepository and non-Repository supertypes are rejected")
    void crudJpaAndPlainRepositoriesAreRejected() {
        ArchSupport.assertRuleRejects(REPOSITORIES_ARE_NOT_CRUD, ArchSupport.fixtures("appendonly.crud"))
                .hasMessageContaining("RuleSetVersionRepository");
        ArchSupport.assertRuleRejects(REPOSITORIES_ARE_NOT_CRUD, ArchSupport.fixtures("appendonly.jpa"))
                .hasMessageContaining("RuleSetVersionRepository");
        ArchSupport.assertRuleRejects(REPOSITORIES_ARE_NOT_CRUD, ArchSupport.fixtures("appendonly.notrepo"))
                .hasMessageContaining("RuleSetVersionRepository");
    }

    @Test
    @DisplayName("NFR-02 negative fixture: delete*, remove*, update* and @Modifying repository methods are rejected")
    void deleteRemoveUpdateAndModifyingMethodsAreRejected() {
        ArchSupport.assertRuleRejects(REPOSITORIES_HAVE_NO_DELETE_OR_UPDATE, ArchSupport.fixtures("appendonly.delete"))
                .hasMessageContaining("deleteByProductCode")
                .hasMessageContaining("removeById");
        ArchSupport.assertRuleRejects(REPOSITORIES_HAVE_NO_DELETE_OR_UPDATE, ArchSupport.fixtures("appendonly.update"))
                .hasMessageContaining("overwrite")
                .hasMessageContaining("updateActor");
    }
}
