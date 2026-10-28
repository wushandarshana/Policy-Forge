package com.horizon.policyforge.domain.rating;

import static com.horizon.policyforge.domain.rating.RatingFixtures.termLifeAc011;
import static com.horizon.policyforge.domain.rating.RatingFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-01.10 at the domain level: TERM_LIFE healthDeclarations is at most 10 codes matching ^[A-Z][A-Z0-9_]{0,63}$. */
@Tag("AC-01")
@Tag("AC-01.10")
class HealthDeclarationsRuleTest {

    private final RiskInputValidator validator = new RiskInputValidator();
    private final RuleSetVersion termLife = RatingFixtures.termLifeV1();

    private List<RiskInputViolation> violations(Object healthDeclarations) {
        return validator.validate(termLife, with(termLifeAc011(), "healthDeclarations", healthDeclarations));
    }

    private static List<String> codes(int count) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            codes.add("CODE_" + i);
        }
        return codes;
    }

    @Test
    @DisplayName("AC-01.10: objects, numbers, lower-case, 65-character codes and 11 entries are each one violation on healthDeclarations")
    void illegalDeclarationsAreOneViolation() {
        List<Object> illegal = List.of(
                List.of(Map.of("aadhaar", "XXXX-XXXX-1234")),
                List.of(123),
                List.of("diabetes"),
                List.of("A".repeat(65)),
                List.of("DIABETES", "has space"),
                List.of("1DIABETES"),
                codes(11));
        for (Object value : illegal) {
            List<RiskInputViolation> found = violations(value);
            assertThat(found).extracting(RiskInputViolation::field).containsExactly("healthDeclarations");
            assertThat(found.get(0).message()).doesNotContain("XXXX").doesNotContain("diabetes");
        }
    }

    @Test
    @DisplayName("AC-01.10: an empty list, [DIABETES, ASTHMA], a 64-character code and exactly 10 codes are accepted")
    void legalDeclarationsAreAccepted() {
        assertThat(violations(List.of())).isEmpty();
        assertThat(violations(List.of("DIABETES", "ASTHMA"))).isEmpty();
        assertThat(violations(List.of("A" + "B_9".repeat(21)))).isEmpty();
        assertThat(violations(codes(10))).isEmpty();
    }
}
