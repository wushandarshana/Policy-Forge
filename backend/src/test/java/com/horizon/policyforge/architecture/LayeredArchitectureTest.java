package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The layer matrix of docs/architecture.md section 2 (NFR-08):
 * web may use service and domain, service may use repository and domain, repository may use domain, domain uses the JDK
 * only, and config may use anything. Nothing in a lower layer may reach up.
 */
@Tag("NFR-08")
@AnalyzeClasses(packages = ArchSupport.PRODUCTION_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    static final ArchRule LAYER_MATRIX = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Web").definedBy("com.horizon..web..")
            .layer("Service").definedBy("com.horizon..service..")
            .layer("Repository").definedBy("com.horizon..repository..")
            .layer("Domain").definedBy("com.horizon..domain..")
            .layer("Config").definedBy("com.horizon..config..")
            .whereLayer("Web").mayOnlyAccessLayers("Service", "Domain")
            .whereLayer("Service").mayOnlyAccessLayers("Repository", "Domain")
            .whereLayer("Repository").mayOnlyAccessLayers("Domain")
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .because("NFR-08: docs/architecture.md section 2 fixes the layer matrix "
                    + "(web to service to repository to domain, never upwards)");

    @ArchTest
    static final ArchRule layersFollowTheMatrix = LAYER_MATRIX;

    @Test
    @DisplayName("NFR-08 negative fixture: web to repository or config is rejected")
    void webReachingDownPastServiceIsRejected() {
        ArchSupport.assertRuleRejects(LAYER_MATRIX, ArchSupport.fixtures("layers"))
                .hasMessageContaining("WebToRepository")
                .hasMessageContaining("WebToConfig")
                .hasMessageNotContaining("WebToService")
                .hasMessageNotContaining("WebToDomain");
    }

    @Test
    @DisplayName("NFR-08 negative fixture: service to web or config is rejected")
    void serviceReachingUpIsRejected() {
        ArchSupport.assertRuleRejects(LAYER_MATRIX, ArchSupport.fixtures("layers"))
                .hasMessageContaining("ServiceToWeb")
                .hasMessageContaining("ServiceToConfig")
                .hasMessageNotContaining("ServiceToRepository")
                .hasMessageNotContaining("ServiceToDomain");
    }

    @Test
    @DisplayName("NFR-08 negative fixture: repository to service or web is rejected")
    void repositoryReachingUpIsRejected() {
        ArchSupport.assertRuleRejects(LAYER_MATRIX, ArchSupport.fixtures("layers"))
                .hasMessageContaining("RepositoryToService")
                .hasMessageContaining("RepositoryToWeb")
                .hasMessageNotContaining("RepositoryToDomain");
    }

    @Test
    @DisplayName("NFR-08 negative fixture: domain to service, repository, web or config is rejected")
    void domainDependingOnAnyOtherLayerIsRejected() {
        ArchSupport.assertRuleRejects(LAYER_MATRIX, ArchSupport.fixtures("layers"))
                .hasMessageContaining("DomainToService")
                .hasMessageContaining("DomainToRepository")
                .hasMessageContaining("DomainToWeb")
                .hasMessageContaining("DomainToConfig");
    }

    @Test
    @DisplayName("NFR-08 negative fixture: config may depend on any layer, so it is never reported")
    void configDependingOnAnyLayerIsAllowed() {
        ArchSupport.assertRuleRejects(LAYER_MATRIX, ArchSupport.fixtures("layers"))
                .hasMessageNotContaining("ConfigToWeb")
                .hasMessageNotContaining("ConfigToRepository");
    }
}
