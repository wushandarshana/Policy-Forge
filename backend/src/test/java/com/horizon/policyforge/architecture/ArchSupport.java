package com.horizon.policyforge.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.assertj.core.api.AbstractThrowableAssert;

/**
 * Shared helpers for the architecture tests (NFR-08). Production classes and the negative fixtures are imported through
 * separate calls: the production import drops test classes, and the fixtures live outside the application's base
 * package ({@code com.horizon.archfixtures}) so Spring and JPA never scan them.
 */
final class ArchSupport {

    static final String PRODUCTION_PACKAGE = "com.horizon.policyforge";
    static final String FIXTURE_PACKAGE = "com.horizon.archfixtures";

    private static JavaClasses production;

    private ArchSupport() {
    }

    /** Production classes only (test classes, and so all fixtures, are excluded). */
    static synchronized JavaClasses production() {
        if (production == null) {
            production = new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(PRODUCTION_PACKAGE);
        }
        return production;
    }

    /** The classes of one fixture sub-package, for example {@code layers} or {@code appendonly.setter}. */
    static JavaClasses fixtures(String subPackage) {
        return new ClassFileImporter().importPackages(FIXTURE_PACKAGE + "." + subPackage);
    }

    /** Asserts the rule rejects the fixtures, and returns the assertion on the violation report. */
    static AbstractThrowableAssert<?, ? extends Throwable> assertRuleRejects(ArchRule rule, JavaClasses fixtures) {
        return assertThatThrownBy(() -> rule.check(fixtures)).isInstanceOf(AssertionError.class);
    }
}
