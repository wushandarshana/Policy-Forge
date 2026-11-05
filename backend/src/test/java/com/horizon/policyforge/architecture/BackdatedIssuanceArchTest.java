package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameContaining;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The backdated issuance path (plan P-15, AC-11.1). The synthetic portfolio seed issues policies whose effective date is
 * in the past, which the public issue endpoint refuses (AC-05.1). That path lives in the service layer only: it is never
 * public or protected, and no class outside the service package calls it, so no web class can reach it. The existence
 * rule fails while the method is absent, and the other two have a negative fixture each.
 */
@Tag("NFR-08")
@Tag("AC-11")
@AnalyzeClasses(packages = ArchSupport.PRODUCTION_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class BackdatedIssuanceArchTest {

    static final String SERVICE_PACKAGE = "com.horizon.policyforge.service..";
    static final String FIXTURE_SERVICE_PACKAGE = "com.horizon.archfixtures.backdated.service..";

    static ArchRule onlyTheServiceLayerCallsIt(String servicePackage) {
        return noClasses().that().resideOutsideOfPackage(servicePackage)
                .should().callMethodWhere(target(nameContaining("Backdated")))
                .because("AC-11.1: only the service layer may issue a backdated policy, never a web class");
    }

    static final ArchRule NEVER_PUBLIC = methods().that().haveNameContaining("Backdated")
            .should().notBePublic().andShould().notBeProtected()
            .because("AC-11.1: the backdated issuance path has package visibility only");

    static final ArchRule THE_PATH_EXISTS = methods().that().haveName("issueBackdated")
            .should().beDeclaredIn("com.horizon.policyforge.service.PolicyIssuanceService")
            .allowEmptyShould(false)
            .because("AC-11.1: the seed's backdated issuance lives on the issuance service");

    @ArchTest
    static final ArchRule noCallerOutsideTheService = onlyTheServiceLayerCallsIt(SERVICE_PACKAGE);

    @ArchTest
    static final ArchRule neverPublicOrProtected = NEVER_PUBLIC;

    @ArchTest
    static final ArchRule thePathExistsOnTheIssuanceService = THE_PATH_EXISTS;

    @Test
    @DisplayName("AC-11.1 negative fixture: a web class that calls a backdated method is rejected, and a service class in the same package is not")
    void aCallerOutsideTheServiceIsRejected() {
        ArchSupport.assertRuleRejects(onlyTheServiceLayerCallsIt(FIXTURE_SERVICE_PACKAGE),
                ArchSupport.fixtures("backdated"))
                .hasMessageContaining("BackdatedCaller")
                .hasMessageNotContaining("SameLayerCaller");
    }

    @Test
    @DisplayName("AC-11.1 negative fixture: a public backdated method is rejected, and a package-private one is not")
    void aPublicBackdatedMethodIsRejected() {
        ArchSupport.assertRuleRejects(NEVER_PUBLIC, ArchSupport.fixtures("backdated"))
                .hasMessageContaining("issueBackdated()")
                .hasMessageNotContaining("issueBackdatedQuietly");
    }
}
