package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;

/** Controllers (any {@code @Controller} or {@code @RestController}) live only in {@code ..web..} and end in Controller. */
@Tag("NFR-08")
@AnalyzeClasses(packages = ArchSupport.PRODUCTION_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ControllerPlacementArchTest {

    static final ArchRule CONTROLLERS_LIVE_IN_WEB = classes().that().areMetaAnnotatedWith(Controller.class)
            .should().resideInAPackage("com.horizon..web..")
            .because("NFR-08: web is the only layer that serves HTTP (docs/architecture.md section 2)");

    static final ArchRule CONTROLLERS_ARE_NAMED_CONTROLLER = classes().that().areMetaAnnotatedWith(Controller.class)
            .should().haveSimpleNameEndingWith("Controller")
            .because("NFR-08: controllers are recognisable by name (docs/architecture.md section 2)");

    @ArchTest
    static final ArchRule controllersResideInWeb = CONTROLLERS_LIVE_IN_WEB;

    @ArchTest
    static final ArchRule controllersAreNamedController = CONTROLLERS_ARE_NAMED_CONTROLLER;

    @Test
    @DisplayName("NFR-08 negative fixture: a controller outside the web package is rejected")
    void misplacedControllersAreRejected() {
        ArchSupport.assertRuleRejects(CONTROLLERS_LIVE_IN_WEB, ArchSupport.fixtures("controllers"))
                .hasMessageContaining("MisplacedController")
                .hasMessageContaining("MisplacedPlainController")
                .hasMessageNotContaining("GoodController")
                .hasMessageNotContaining("AdviceHandler");
    }

    @Test
    @DisplayName("NFR-08 negative fixture: a controller whose name does not end in Controller is rejected")
    void misnamedControllersAreRejected() {
        ArchSupport.assertRuleRejects(CONTROLLERS_ARE_NAMED_CONTROLLER, ArchSupport.fixtures("controllers"))
                .hasMessageContaining("Misnamed")
                .hasMessageNotContaining("GoodController")
                .hasMessageNotContaining("AdviceHandler");
    }
}
