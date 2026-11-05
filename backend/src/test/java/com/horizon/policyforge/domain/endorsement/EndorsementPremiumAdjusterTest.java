package com.horizon.policyforge.domain.endorsement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.rating.PremiumCalculator;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.service.RuleSetJsonParser;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The pro-rata sum-insured adjustment (AC-06.2) against the premium-calc-evaluator oracle (premium_check.py), and the
 * re-rating of the modified risk inputs with the existing {@link PremiumCalculator}.
 */
@Tag("AC-06")
class EndorsementPremiumAdjusterTest {

    private static final LocalDate TERM_START = LocalDate.of(2026, 11, 1);
    private static final LocalDate TERM_END = LocalDate.of(2027, 10, 31);

    private final EndorsementPremiumAdjuster adjuster = new EndorsementPremiumAdjuster();
    private final PremiumCalculator calculator = new PremiumCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static RuleSetVersion rules(String slug) {
        RuleSetJsonParser.ParsedRuleSet parsed = new RuleSetJsonParser().parse(RuleSetFixtures.seedText(slug, 1));
        assertThat(parsed.violations()).isEmpty();
        return parsed.ruleSet();
    }

    private static Map<String, Object> termLifeInputs() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("age", 35);
        in.put("smoker", false);
        in.put("sumInsured", "5000000.00");
        in.put("termYears", 20);
        in.put("bmi", "24.5");
        in.put("healthDeclarations", List.of());
        return in;
    }

    private static Map<String, Object> motorInputs() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("vehicleValue", "800000.00");
        in.put("vehicleAgeYears", 2);
        in.put("driverAge", 30);
        in.put("claimsLast3Years", 0);
        in.put("noClaimBonusYears", 2);
        in.put("vehicleUse", "PRIVATE");
        return in;
    }

    private static Map<String, Object> householdInputs() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("buildingSumInsured", "6000000.00");
        in.put("contentsSumInsured", "1000000.00");
        in.put("propertyAgeYears", 12);
        in.put("constructionType", "BRICK");
        in.put("floodZone", "LOW");
        in.put("hasSecuritySystem", true);
        in.put("hasFloodDefences", false);
        return in;
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: 6850.00 to 9490.00 effective 2027-05-01 is 184 / 365 days and adjusts by 1330.85")
    void specWorkedExample() {
        PremiumAdjustment result = adjuster.adjust(d("6850.00"), d("9490.00"), TERM_START, TERM_END,
                LocalDate.of(2027, 5, 1));

        assertThat(result.unexpiredDays()).isEqualTo(184);
        assertThat(result.termDays()).isEqualTo(365);
        assertThat(result.adjustment()).isEqualTo(d("1330.85"));
        assertThat(result.adjustment().scale()).isEqualTo(2);
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: the reverse change (9490.00 to 6850.00) is a credit of -1330.85")
    void reverseChangeIsACredit() {
        PremiumAdjustment result = adjuster.adjust(d("9490.00"), d("6850.00"), TERM_START, TERM_END,
                LocalDate.of(2027, 5, 1));

        assertThat(result.adjustment()).isEqualTo(d("-1330.85"));
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: effective on the term start the adjustment is 2640.00, on the term end it is 7.23")
    void termBoundaries() {
        PremiumAdjustment onStart = adjuster.adjust(d("6850.00"), d("9490.00"), TERM_START, TERM_END, TERM_START);
        PremiumAdjustment onEnd = adjuster.adjust(d("6850.00"), d("9490.00"), TERM_START, TERM_END, TERM_END);

        assertThat(onStart.unexpiredDays()).isEqualTo(365);
        assertThat(onStart.adjustment()).isEqualTo(d("2640.00"));
        assertThat(onEnd.unexpiredDays()).isEqualTo(1);
        assertThat(onEnd.adjustment()).isEqualTo(d("7.23"));
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: an unchanged premium adjusts by 0.00 and a leap-year term uses 366 days")
    void unchangedPremiumAndLeapYear() {
        PremiumAdjustment same = adjuster.adjust(d("6850.00"), d("6850.00"), TERM_START, TERM_END,
                LocalDate.of(2027, 5, 1));
        PremiumAdjustment leap = adjuster.adjust(d("1000.00"), d("2000.00"), LocalDate.of(2027, 11, 1),
                LocalDate.of(2028, 10, 31), LocalDate.of(2027, 11, 1));

        assertThat(same.adjustment()).isEqualTo(d("0.00"));
        assertThat(same.adjustment().scale()).isEqualTo(2);
        assertThat(leap.termDays()).isEqualTo(366);
        assertThat(leap.adjustment()).isEqualTo(d("1000.00"));
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: rounding is HALF_UP at 2 dp: 0.01 over 2 of 4 days is 0.01 and 0.01 over 1 of 4 days is 0.00")
    void roundsHalfUp() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 4);

        assertThat(adjuster.adjust(d("0.00"), d("0.01"), start, end, LocalDate.of(2026, 1, 3)).adjustment())
                .isEqualTo(d("0.01"));
        assertThat(adjuster.adjust(d("0.00"), d("0.01"), start, end, LocalDate.of(2026, 1, 4)).adjustment())
                .isEqualTo(d("0.00"));
        assertThat(adjuster.adjust(d("0.00"), d("0.03"), start, end, LocalDate.of(2026, 1, 3)).adjustment())
                .isEqualTo(d("0.02"));
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: an effective date outside the term is a programming error")
    void effectiveDateOutsideTermIsRejected() {
        assertThatThrownBy(() -> adjuster.adjust(d("1.00"), d("2.00"), TERM_START, TERM_END, TERM_END.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adjuster.adjust(d("1.00"), d("2.00"), TERM_START, TERM_END, TERM_START.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: TERM_LIFE v1 re-rated at SI 7,000,000.00 (age 35, non-smoker, 20 years) prices at 9490.00")
    void termLifeReRate() {
        Map<String, Object> modified = adjuster.modifiedRiskInputs("TERM_LIFE", termLifeInputs(),
                Map.of("sumInsured", d("7000000.00")));

        assertThat(modified.get("sumInsured")).isEqualTo("7000000.00");
        assertThat(modified.get("age")).isEqualTo(35);
        assertThat(calculator.calculate(rules("term-life"), modified).annualPremium()).isEqualTo(d("9490.00"));
        assertThat(termLifeInputs().get("sumInsured")).as("the current inputs are not mutated").isEqualTo("5000000.00");
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: MOTOR v1 re-rated at vehicleValue 900000.00 prices at 17025.00 and the adjustment follows")
    void motorReRate() {
        Map<String, Object> modified = adjuster.modifiedRiskInputs("MOTOR", motorInputs(),
                Map.of("vehicleValue", d("900000.00")));
        BigDecimal rated = calculator.calculate(rules("motor"), modified).annualPremium();

        assertThat(modified.get("vehicleValue")).isEqualTo("900000.00");
        assertThat(rated).isEqualTo(d("17025.00"));
        assertThat(calculator.calculate(rules("motor"), motorInputs()).annualPremium()).isEqualTo(d("15150.00"));
        assertThat(adjuster.adjust(d("15150.00"), rated, TERM_START, TERM_END, TERM_START).adjustment())
                .isEqualTo(d("1875.00"));
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: HOUSEHOLD replaces only the supplied components, building and contents")
    void householdModifiedInputs() {
        Map<String, Object> buildingOnly = adjuster.modifiedRiskInputs("HOUSEHOLD", householdInputs(),
                Map.of("buildingSumInsured", d("7000000.00")));
        Map<String, Object> both = adjuster.modifiedRiskInputs("HOUSEHOLD", householdInputs(),
                Map.of("buildingSumInsured", d("7000000.00"), "contentsSumInsured", d("1500000.00")));

        assertThat(buildingOnly.get("buildingSumInsured")).isEqualTo("7000000.00");
        assertThat(buildingOnly.get("contentsSumInsured")).isEqualTo("1000000.00");
        assertThat(both.get("contentsSumInsured")).isEqualTo("1500000.00");
        assertThat(calculator.calculate(rules("household"), householdInputs()).annualPremium()).isEqualTo(d("11350.00"));
        assertThat(calculator.calculate(rules("household"), buildingOnly).annualPremium()).isGreaterThan(d("11350.00"));
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: a component the product does not have is rejected and an unknown product is rejected")
    void wrongComponentsAreRejected() {
        assertThatThrownBy(() -> adjuster.modifiedRiskInputs("MOTOR", motorInputs(), Map.of("sumInsured", d("1.00"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adjuster.modifiedRiskInputs("TERM_LIFE", termLifeInputs(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adjuster.modifiedRiskInputs("PET", motorInputs(), Map.of("sumInsured", d("1.00"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
