package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Money is fixed-point (NFR-01): no {@code double} or {@code float} in fields, parameters or return types of the
 * domain, service, repository and web layers, and no conversion of a BigDecimal to or from a binary floating-point value.
 */
@Tag("NFR-08")
@Tag("NFR-01")
@AnalyzeClasses(packages = ArchSupport.PRODUCTION_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class MoneyPrecisionArchTest {

    private static final String[] MONEY_PACKAGES = {"com.horizon..domain..", "com.horizon..service..", "com.horizon..repository..", "com.horizon..web.."};
    private static final Set<String> FLOATING_POINT = Set.of(
            "double", "float", "java.lang.Double", "java.lang.Float");

    private static final DescribedPredicate<JavaClass> FLOATING_POINT_TYPE =
            DescribedPredicate.describe("a double or float type", type -> FLOATING_POINT.contains(type.getName()));

    private static final ArchCondition<JavaMethod> NOT_HAVE_FLOATING_POINT_PARAMETERS =
            new ArchCondition<>("not have double or float parameters") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    method.getRawParameterTypes().stream()
                            .filter(FLOATING_POINT_TYPE)
                            .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " has a parameter of type " + type.getName())));
                }
            };

    /** A call that turns a BigDecimal into, or builds one from, a binary floating-point value. */
    private static final DescribedPredicate<JavaCall<?>> BIG_DECIMAL_FLOATING_POINT_CONVERSION =
            DescribedPredicate.describe("convert between BigDecimal and double or float", call -> {
                if (!call.getTargetOwner().isEquivalentTo(BigDecimal.class)) {
                    return false;
                }
                String name = call.getTarget().getName();
                boolean toFloating = name.equals("doubleValue") || name.equals("floatValue");
                boolean fromFloating = call.getTarget().getRawParameterTypes().stream().anyMatch(FLOATING_POINT_TYPE);
                return toFloating || fromFloating;
            });

    static final ArchRule NO_FLOATING_POINT_FIELDS = noFields().that().areDeclaredInClassesThat()
            .resideInAnyPackage(MONEY_PACKAGES)
            .should().haveRawType(FLOATING_POINT_TYPE)
            .because("NFR-01: money, rates and factors are BigDecimal, never double or float");

    static final ArchRule NO_FLOATING_POINT_RETURNS = noMethods().that().areDeclaredInClassesThat()
            .resideInAnyPackage(MONEY_PACKAGES)
            .should().haveRawReturnType(FLOATING_POINT_TYPE)
            .because("NFR-01: money, rates and factors are BigDecimal, never double or float");

    static final ArchRule NO_FLOATING_POINT_PARAMETERS = methods().that().areDeclaredInClassesThat()
            .resideInAnyPackage(MONEY_PACKAGES)
            .should(NOT_HAVE_FLOATING_POINT_PARAMETERS)
            .because("NFR-01: money, rates and factors are BigDecimal, never double or float");

    static final ArchRule NO_BIG_DECIMAL_FLOATING_POINT_CONVERSION = noClasses().that()
            .resideInAnyPackage(MONEY_PACKAGES)
            .should().callCodeUnitWhere(BIG_DECIMAL_FLOATING_POINT_CONVERSION)
            .because("NFR-01: BigDecimal must not be built from or converted to double or float "
                    + "(use BigDecimal.valueOf(long), a string, or setScale)");

    @ArchTest
    static final ArchRule noFloatingPointFields = NO_FLOATING_POINT_FIELDS;

    @ArchTest
    static final ArchRule noFloatingPointReturns = NO_FLOATING_POINT_RETURNS;

    @ArchTest
    static final ArchRule noFloatingPointParameters = NO_FLOATING_POINT_PARAMETERS;

    @ArchTest
    static final ArchRule noBigDecimalFloatingPointConversion = NO_BIG_DECIMAL_FLOATING_POINT_CONVERSION;

    @Test
    @DisplayName("NFR-01 negative fixture: double and float fields are rejected")
    void floatingPointFieldsAreRejected() {
        ArchSupport.assertRuleRejects(NO_FLOATING_POINT_FIELDS, ArchSupport.fixtures("money"))
                .hasMessageContaining("MoneyDoubleField")
                .hasMessageContaining("MoneyBoxedFloatField")
                .hasMessageNotContaining("MoneyOk");
    }

    @Test
    @DisplayName("NFR-01 negative fixture: double and float return types are rejected")
    void floatingPointReturnsAreRejected() {
        ArchSupport.assertRuleRejects(NO_FLOATING_POINT_RETURNS, ArchSupport.fixtures("money"))
                .hasMessageContaining("MoneyFloatReturn")
                .hasMessageContaining("MoneyBoxedDoubleReturn")
                .hasMessageNotContaining("MoneyOk");
    }

    @Test
    @DisplayName("NFR-01 negative fixture: double and float parameters are rejected")
    void floatingPointParametersAreRejected() {
        ArchSupport.assertRuleRejects(NO_FLOATING_POINT_PARAMETERS, ArchSupport.fixtures("money"))
                .hasMessageContaining("MoneyDoubleParameter")
                .hasMessageNotContaining("MoneyOk");
    }

    @Test
    @DisplayName("NFR-01 negative fixture: BigDecimal built from or converted to a double is rejected")
    void bigDecimalFloatingPointConversionIsRejected() {
        ArchSupport.assertRuleRejects(NO_BIG_DECIMAL_FLOATING_POINT_CONVERSION, ArchSupport.fixtures("money"))
                .hasMessageContaining("MoneyFromDouble")
                .hasMessageContaining("MoneyToDouble")
                .hasMessageNotContaining("MoneyOk");
    }
}
