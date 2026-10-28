package com.horizon.policyforge.domain.rating;

import static com.horizon.policyforge.domain.rating.RatingFixtures.householdAc013;
import static com.horizon.policyforge.domain.rating.RatingFixtures.motorAc012;
import static com.horizon.policyforge.domain.rating.RatingFixtures.termLifeAc011;
import static com.horizon.policyforge.domain.rating.RatingFixtures.with;
import static com.horizon.policyforge.domain.rating.RatingFixtures.without;
import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-01.7 at the domain level: one violation per missing or invalid field, including sum insured outside min/max. */
@Tag("AC-01")
@Tag("AC-01.7")
class RiskInputValidatorTest {

    private final RiskInputValidator validator = new RiskInputValidator();
    private final RuleSetVersion termLife = RatingFixtures.termLifeV1();
    private final RuleSetVersion motor = RatingFixtures.motorV1();
    private final RuleSetVersion household = RatingFixtures.householdV1();

    private List<String> fields(RuleSetVersion rules, Map<String, Object> inputs) {
        return validator.validate(rules, inputs).stream().map(RiskInputViolation::field).toList();
    }

    @Test
    @DisplayName("AC-01.7: the spec's worked inputs for all three products have no violations")
    void validInputsHaveNoViolations() {
        assertThat(validator.validate(termLife, termLifeAc011())).isEmpty();
        assertThat(validator.validate(motor, motorAc012())).isEmpty();
        assertThat(validator.validate(household, householdAc013())).isEmpty();
    }

    @Test
    @DisplayName("AC-01.7: TERM_LIFE sum insured below the minimum or above the maximum is named as sumInsured")
    void termLifeSumInsuredOutOfRange() {
        assertThat(fields(termLife, with(termLifeAc011(), "sumInsured", "499999.99"))).containsExactly("sumInsured");
        assertThat(fields(termLife, with(termLifeAc011(), "sumInsured", "50000000.01"))).containsExactly("sumInsured");
    }

    @Test
    @DisplayName("AC-01.7: sum insured exactly at the minimum and at the maximum is accepted for every product")
    void sumInsuredLimitsAreInclusive() {
        assertThat(validator.validate(termLife, with(termLifeAc011(), "sumInsured", "500000.00"))).isEmpty();
        assertThat(validator.validate(termLife, with(termLifeAc011(), "sumInsured", "50000000.00"))).isEmpty();
        assertThat(validator.validate(motor, with(motorAc012(), "vehicleValue", "100000.00"))).isEmpty();
        assertThat(validator.validate(motor, with(motorAc012(), "vehicleValue", "10000000.00"))).isEmpty();
        Map<String, Object> low = with(with(householdAc013(), "buildingSumInsured", "150000.00"), "contentsSumInsured", "50000.00");
        Map<String, Object> high = with(with(householdAc013(), "buildingSumInsured", "40000000.00"), "contentsSumInsured", "10000000.00");
        assertThat(validator.validate(household, low)).isEmpty();
        assertThat(validator.validate(household, high)).isEmpty();
    }

    @Test
    @DisplayName("AC-01.7: MOTOR names vehicleValue and HOUSEHOLD names the derived sumInsured when the amount is out of range")
    void sumInsuredFieldNameFollowsTheProduct() {
        assertThat(fields(motor, with(motorAc012(), "vehicleValue", "99999.99"))).containsExactly("vehicleValue");
        assertThat(fields(motor, with(motorAc012(), "vehicleValue", "10000000.01"))).containsExactly("vehicleValue");
        Map<String, Object> small = with(with(householdAc013(), "buildingSumInsured", "100000.00"), "contentsSumInsured", "99999.99");
        Map<String, Object> large = with(with(householdAc013(), "buildingSumInsured", "45000000.00"), "contentsSumInsured", "5000000.01");
        assertThat(fields(household, small)).containsExactly("sumInsured");
        assertThat(fields(household, large)).containsExactly("sumInsured");
    }

    @Test
    @DisplayName("AC-01.7: every missing required input is its own violation, none is defaulted")
    void missingInputsAreEachReported() {
        assertThat(fields(termLife, Map.of())).containsExactlyInAnyOrder(
                "age", "smoker", "sumInsured", "termYears", "bmi", "healthDeclarations");
        assertThat(fields(motor, Map.of())).containsExactlyInAnyOrder(
                "vehicleValue", "vehicleAgeYears", "driverAge", "claimsLast3Years", "noClaimBonusYears", "vehicleUse");
        assertThat(fields(household, Map.of())).containsExactlyInAnyOrder(
                "buildingSumInsured", "contentsSumInsured", "propertyAgeYears", "constructionType", "floodZone",
                "hasSecuritySystem", "hasFloodDefences");
    }

    @Test
    @DisplayName("AC-01.7: a single missing input is the only violation, and an empty healthDeclarations list is not missing")
    void oneMissingInput() {
        assertThat(fields(termLife, without(termLifeAc011(), "bmi"))).containsExactly("bmi");
        assertThat(fields(termLife, without(termLifeAc011(), "healthDeclarations"))).containsExactly("healthDeclarations");
        assertThat(fields(motor, without(motorAc012(), "vehicleUse"))).containsExactly("vehicleUse");
        assertThat(fields(household, without(householdAc013(), "hasFloodDefences"))).containsExactly("hasFloodDefences");
    }

    @Test
    @DisplayName("AC-01.7: several invalid fields at once are all reported, each exactly once")
    void severalViolationsAtOnce() {
        Map<String, Object> inputs = without(with(with(termLifeAc011(), "sumInsured", "1.00"), "age", 17), "termYears");

        assertThat(fields(termLife, inputs)).containsExactlyInAnyOrder("sumInsured", "age", "termYears");
    }

    @Test
    @DisplayName("AC-01.7: a value outside every band is a violation naming the field, not a default factor")
    void outOfBandInputsAreViolations() {
        assertThat(fields(termLife, with(termLifeAc011(), "age", 17))).containsExactly("age");
        assertThat(fields(termLife, with(termLifeAc011(), "age", 66))).containsExactly("age");
        assertThat(validator.validate(termLife, with(termLifeAc011(), "age", 65))).isEmpty();
        assertThat(fields(motor, with(motorAc012(), "vehicleAgeYears", 16))).containsExactly("vehicleAgeYears");
        assertThat(validator.validate(motor, with(motorAc012(), "vehicleAgeYears", 15))).isEmpty();
        assertThat(fields(motor, with(motorAc012(), "driverAge", 17))).containsExactly("driverAge");
        assertThat(fields(motor, with(motorAc012(), "driverAge", 100))).containsExactly("driverAge");
    }

    @Test
    @DisplayName("AC-01.7: enumerations are checked against the rule set's keys")
    void enumerationsAreChecked() {
        assertThat(fields(termLife, with(termLifeAc011(), "termYears", 15))).containsExactly("termYears");
        assertThat(fields(motor, with(motorAc012(), "vehicleUse", "RACING"))).containsExactly("vehicleUse");
        assertThat(fields(motor, with(motorAc012(), "noClaimBonusYears", 6))).containsExactly("noClaimBonusYears");
        assertThat(fields(motor, with(motorAc012(), "noClaimBonusYears", -1))).containsExactly("noClaimBonusYears");
        assertThat(fields(household, with(householdAc013(), "constructionType", "STRAW"))).containsExactly("constructionType");
        assertThat(fields(household, with(householdAc013(), "floodZone", "EXTREME"))).containsExactly("floodZone");
    }

    @Test
    @DisplayName("AC-01.7: wrongly typed values are violations: booleans, whole numbers, decimals and the declarations list")
    void wronglyTypedValuesAreViolations() {
        assertThat(fields(termLife, with(termLifeAc011(), "smoker", "yes"))).containsExactly("smoker");
        assertThat(fields(termLife, with(termLifeAc011(), "age", "thirty"))).containsExactly("age");
        assertThat(fields(termLife, with(termLifeAc011(), "age", new BigDecimal("35.5")))).containsExactly("age");
        assertThat(fields(termLife, with(termLifeAc011(), "sumInsured", "five million"))).containsExactly("sumInsured");
        assertThat(fields(termLife, with(termLifeAc011(), "sumInsured", "5000000.001"))).containsExactly("sumInsured");
        assertThat(fields(termLife, with(termLifeAc011(), "bmi", "-1"))).containsExactly("bmi");
        assertThat(fields(termLife, with(termLifeAc011(), "healthDeclarations", "none"))).containsExactly("healthDeclarations");
        assertThat(fields(household, with(householdAc013(), "hasSecuritySystem", "true-ish"))).containsExactly("hasSecuritySystem");
        assertThat(fields(household, with(householdAc013(), "propertyAgeYears", -3))).containsExactly("propertyAgeYears");
        assertThat(fields(motor, with(motorAc012(), "claimsLast3Years", -1))).containsExactly("claimsLast3Years");
        assertThat(fields(motor, with(motorAc012(), "vehicleValue", null))).containsExactly("vehicleValue");
    }

    @Test
    @DisplayName("AC-01.7: messages name the problem but never repeat the submitted value")
    void messagesDoNotEchoValues() {
        List<RiskInputViolation> violations = validator.validate(termLife,
                with(with(termLifeAc011(), "sumInsured", "123456.78"), "bmi", "not-a-number"));

        assertThat(violations).hasSize(2);
        assertThat(violations).allSatisfy(v -> {
            assertThat(v.message()).isNotBlank();
            assertThat(v.message()).doesNotContain("123456.78").doesNotContain("not-a-number");
        });
    }

    @Test
    @DisplayName("AC-01.7: a HOUSEHOLD component that is invalid is reported on its own field and the derived sum is not also flagged")
    void householdComponentViolationsStandAlone() {
        assertThat(fields(household, with(householdAc013(), "buildingSumInsured", "-5.00"))).containsExactly("buildingSumInsured");
        assertThat(fields(household, with(without(householdAc013(), "contentsSumInsured"), "buildingSumInsured", "10.00")))
                .containsExactly("contentsSumInsured");
    }
}
