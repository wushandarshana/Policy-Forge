package com.horizon.policyforge.domain.rating;

import static com.horizon.policyforge.domain.rating.RatingFixtures.householdAc013;
import static com.horizon.policyforge.domain.rating.RatingFixtures.motorAc012;
import static com.horizon.policyforge.domain.rating.RatingFixtures.termLife;
import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.ruleset.RatingModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The view of a stored quote's risk inputs that an underwriter reviews (U-7, AC-09.1): the product's declared inputs,
 * health inputs included, and never a key the rating model does not declare. Unlike the public view (NFR-04.2), the
 * health inputs stay in.
 */
@Tag("AC-09")
class RiskInputSchemaReviewViewTest {

    @Test
    @DisplayName("AC-09.1: the TERM_LIFE review view holds the declared inputs in declared order, BMI and health declarations included")
    void termLifeViewIncludesHealthInputs() {
        Map<String, Object> stored = termLife(40, true, "12000000.00", 20);

        Map<String, Object> view = RiskInputSchema.forModel(RatingModel.TERM_LIFE).reviewView(stored);

        assertThat(view.keySet()).containsExactly("age", "smoker", "sumInsured", "termYears", "bmi",
                "healthDeclarations");
        assertThat(view.get("sumInsured")).isEqualTo("12000000.00");
        assertThat(view.get("smoker")).isEqualTo(true);
        assertThat(view.get("healthDeclarations")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("AC-09.1: a key the rating model does not declare never appears in the review view, whatever the stored map holds")
    void undeclaredKeysNeverAppear() {
        Map<String, Object> stored = new LinkedHashMap<>(termLife(40, true, "12000000.00", 20));
        stored.put("aadhaar", "XXXX-XXXX-1234");
        stored.put("fullName", "Asha Test");
        stored.put("BMI", "31.4");

        Map<String, Object> view = RiskInputSchema.forModel(RatingModel.TERM_LIFE).reviewView(stored);

        assertThat(view.keySet()).containsExactly("age", "smoker", "sumInsured", "termYears", "bmi",
                "healthDeclarations");
    }

    @Test
    @DisplayName("AC-09.1: the MOTOR and HOUSEHOLD review views are exactly their declared inputs")
    void otherProductsViewTheirDeclaredInputs() {
        assertThat(RiskInputSchema.forModel(RatingModel.MOTOR).reviewView(motorAc012()).keySet())
                .containsExactly("vehicleValue", "vehicleAgeYears", "driverAge", "claimsLast3Years",
                        "noClaimBonusYears", "vehicleUse");
        assertThat(RiskInputSchema.forModel(RatingModel.HOUSEHOLD).reviewView(householdAc013()).keySet())
                .containsExactly("buildingSumInsured", "contentsSumInsured", "propertyAgeYears", "constructionType",
                        "floodZone", "hasSecuritySystem", "hasFloodDefences");
    }

    @Test
    @DisplayName("AC-09.1: a declared input that is absent or null is left out, and a missing map gives an empty view")
    void absentInputsAreLeftOut() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("age", 40);
        stored.put("smoker", null);

        assertThat(RiskInputSchema.forModel(RatingModel.TERM_LIFE).reviewView(stored).keySet())
                .containsExactly("age");
        assertThat(RiskInputSchema.forModel(RatingModel.TERM_LIFE).reviewView(null)).isEmpty();
    }
}
