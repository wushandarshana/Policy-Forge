package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The domain is pure Java (NFR-08): no Spring, JPA, web or other layer, and nothing outside {@code java.*}. */
@Tag("NFR-08")
@AnalyzeClasses(packages = ArchSupport.PRODUCTION_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class DomainPurityArchTest {

    static final ArchRule NO_FRAMEWORK_OR_OTHER_LAYER = noClasses().that().resideInAPackage("com.horizon..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax..", "org.hibernate..",
                    "com.horizon..web..", "com.horizon..service..", "com.horizon..repository..", "com.horizon..config..")
            .because("NFR-08: the domain has no Spring, JPA or web imports and does not reach other layers "
                    + "(docs/architecture.md section 2)");

    static final ArchRule JDK_ONLY = classes().that().resideInAPackage("com.horizon..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.horizon..domain..")
            .because("NFR-08: the domain may depend on the JDK (java.*) and other domain classes only");

    @ArchTest
    static final ArchRule domainHasNoFrameworkOrLayerDependencies = NO_FRAMEWORK_OR_OTHER_LAYER;

    @ArchTest
    static final ArchRule domainDependsOnTheJdkOnly = JDK_ONLY;

    @Test
    @DisplayName("NFR-08 negative fixture: Spring, JPA and web dependencies in the domain are rejected")
    void frameworkAndWebDependenciesAreRejected() {
        ArchSupport.assertRuleRejects(NO_FRAMEWORK_OR_OTHER_LAYER, ArchSupport.fixtures("purity"))
                .hasMessageContaining("DomainUsesSpring")
                .hasMessageContaining("DomainUsesJpa")
                .hasMessageContaining("DomainUsesWeb")
                .hasMessageNotContaining("DomainPure");
    }

    @Test
    @DisplayName("NFR-08 negative fixture: any non-JDK library in the domain is rejected")
    void nonJdkLibrariesAreRejected() {
        ArchSupport.assertRuleRejects(JDK_ONLY, ArchSupport.fixtures("purity"))
                .hasMessageContaining("DomainUsesJackson")
                .hasMessageContaining("DomainUsesSlf4j")
                .hasMessageContaining("DomainUsesSpring")
                .hasMessageNotContaining("DomainPure");
    }
}
