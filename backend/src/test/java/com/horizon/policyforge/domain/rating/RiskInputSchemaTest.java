package com.horizon.policyforge.domain.rating;

import static com.horizon.policyforge.domain.rating.RatingFixtures.householdAc013;
import static com.horizon.policyforge.domain.rating.RatingFixtures.motorAc012;
import static com.horizon.policyforge.domain.rating.RatingFixtures.termLifeAc011;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.ruleset.RatingModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-01.9 and NFR-04.2 at the domain level: declared inputs, unknown-key rejection and the public allow-list. */
@Tag("AC-01")
@Tag("AC-01.9")
@Tag("NFR-04")
class RiskInputSchemaTest {

    private final RiskInputValidator validator = new RiskInputValidator();
    private final PremiumCalculator calculator = new PremiumCalculator();

    @Test
    @DisplayName("AC-01.9: each rating model declares exactly the spec inputs, and only TERM_LIFE has health inputs")
    void eachModelDeclaresTheSpecInputs() {
        assertThat(RiskInputSchema.forModel(RatingModel.TERM_LIFE).declared())
                .containsExactly("age", "smoker", "sumInsured", "termYears", "bmi", "healthDeclarations");
        assertThat(RiskInputSchema.forModel(RatingModel.TERM_LIFE).health())
                .containsExactlyInAnyOrder("bmi", "healthDeclarations");
        assertThat(RiskInputSchema.forModel(RatingModel.MOTOR).declared()).containsExactly("vehicleValue",
                "vehicleAgeYears", "driverAge", "claimsLast3Years", "noClaimBonusYears", "vehicleUse");
        assertThat(RiskInputSchema.forModel(RatingModel.MOTOR).health()).isEmpty();
        assertThat(RiskInputSchema.forModel(RatingModel.HOUSEHOLD).declared()).containsExactly("buildingSumInsured",
                "contentsSumInsured", "propertyAgeYears", "constructionType", "floodZone", "hasSecuritySystem",
                "hasFloodDefences");
        assertThat(RiskInputSchema.forModel(RatingModel.HOUSEHOLD).health()).isEmpty();
    }

    @Test
    @DisplayName("AC-01.9: undeclared BMI, medicalHistory and aadhaar keys are one violation each, named by the key, and the quote is not priced")
    void unknownKeysAreViolationsNamedByKey() {
        Map<String, Object> inputs = new LinkedHashMap<>(termLifeAc011());
        inputs.put("BMI", "27.3");
        inputs.put("medicalHistory", "SYNTHETIC");
        inputs.put("aadhaar", "XXXX-XXXX-1234");

        List<RiskInputViolation> violations = validator.validate(RatingFixtures.termLifeV1(), inputs);

        assertThat(violations).extracting(RiskInputViolation::field)
                .containsExactly("BMI", "aadhaar", "medicalHistory");
        assertThat(violations).allSatisfy(v -> assertThat(v.message()).isEqualTo("is not an accepted input")
                .doesNotContain("XXXX").doesNotContain("SYNTHETIC"));
        assertThatThrownBy(() -> calculator.calculate(RatingFixtures.termLifeV1(), inputs))
                .isInstanceOf(InvalidRiskInputException.class);
    }

    @Test
    @DisplayName("AC-01.9: a MOTOR or HOUSEHOLD request with an extra key is rejected naming that key")
    void unknownKeysOnOtherProducts() {
        Map<String, Object> motor = new LinkedHashMap<>(motorAc012());
        motor.put("fullName", "Test Person");
        Map<String, Object> household = new LinkedHashMap<>(householdAc013());
        household.put("dateOfBirth", "1990-01-01");

        assertThat(validator.validate(RatingFixtures.motorV1(), motor)).extracting(RiskInputViolation::field)
                .containsExactly("fullName");
        assertThat(validator.validate(RatingFixtures.householdV1(), household)).extracting(RiskInputViolation::field)
                .containsExactly("dateOfBirth");
    }

    @Test
    @DisplayName("NFR-04.2: the public view of TERM_LIFE inputs is the declared non-health keys only, even when the map holds extra or health keys")
    void publicViewIsAnAllowList() {
        Map<String, Object> stored = new LinkedHashMap<>(termLifeAc011());
        stored.put("BMI", "31.4");
        stored.put("aadhaar", "XXXX-XXXX-1234");

        Map<String, Object> view = RiskInputSchema.forModel(RatingModel.TERM_LIFE).publicView(stored);

        assertThat(view.keySet()).containsExactly("age", "smoker", "sumInsured", "termYears");
        assertThat(RiskInputSchema.forModel(RatingModel.TERM_LIFE).publicView(null)).isEmpty();
        assertThat(RiskInputSchema.forModel(RatingModel.TERM_LIFE).unknownKeys(null)).isEmpty();
    }

    @Test
    @DisplayName("NFR-04.2: a schema whose health inputs are not declared is rejected")
    void healthInputsMustBeDeclared() {
        assertThatThrownBy(() -> new RiskInputSchema(List.of("age"), Set.of("bmi")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
