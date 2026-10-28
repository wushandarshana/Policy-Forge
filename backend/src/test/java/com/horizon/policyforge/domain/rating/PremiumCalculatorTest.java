package com.horizon.policyforge.domain.rating;

import static com.horizon.policyforge.domain.rating.RatingFixtures.householdAc013;
import static com.horizon.policyforge.domain.rating.RatingFixtures.motorAc012;
import static com.horizon.policyforge.domain.rating.RatingFixtures.termLife;
import static com.horizon.policyforge.domain.rating.RatingFixtures.termLifeAc011;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.Money;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The quote engine's domain arithmetic (AC-01.1 to AC-01.6 and AC-01.8), priced from the shipped v1 rule files. */
@Tag("AC-01")
@Tag("NFR-01")
class PremiumCalculatorTest {

    private final PremiumCalculator calculator = new PremiumCalculator();

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
        assertThat(actual.scale()).isEqualTo(2);
    }

    private static List<String> names(PremiumBreakdown quote) {
        return quote.factors().stream().map(PremiumFactor::factor).toList();
    }

    private static BigDecimal value(PremiumBreakdown quote, String name) {
        return quote.value(name).orElseThrow(() -> new AssertionError("breakdown lacks " + name));
    }

    @Test
    @Tag("AC-01.1")
    @DisplayName("AC-01.1: TERM_LIFE v1, age 35, non-smoker, 5,000,000.00, 20-year term prices at 6850.00")
    void termLifePremium() {
        PremiumBreakdown quote = calculator.calculate(RatingFixtures.termLifeV1(), termLifeAc011());

        assertMoney(quote.annualPremium(), "6850.00");
        assertMoney(quote.sumInsured(), "5000000.00");
    }

    @Test
    @Tag("AC-01.2")
    @DisplayName("AC-01.2: MOTOR v1, 800,000.00 vehicle, age 2, driver 30, 0 claims, 2 NCB years, PRIVATE prices at 15150.00")
    void motorPremium() {
        PremiumBreakdown quote = calculator.calculate(RatingFixtures.motorV1(), motorAc012());

        assertMoney(quote.annualPremium(), "15150.00");
        assertMoney(quote.sumInsured(), "800000.00");
    }

    @Test
    @Tag("AC-01.3")
    @DisplayName("AC-01.3: HOUSEHOLD v1, building 6,000,000.00, contents 1,000,000.00, BRICK, LOW, security system prices at 11350.00")
    void householdPremium() {
        PremiumBreakdown quote = calculator.calculate(RatingFixtures.householdV1(), householdAc013());

        assertMoney(quote.annualPremium(), "11350.00");
        assertMoney(quote.sumInsured(), "7000000.00");
    }

    @Test
    @Tag("AC-01.4")
    @DisplayName("AC-01.4: 100 quotes with the same product, inputs and rule version give 100 equal premiums")
    void quotingIsDeterministic() {
        RuleSetVersion rules = RatingFixtures.termLifeV1();
        Set<BigDecimal> premiums = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            premiums.add(calculator.calculate(rules, termLifeAc011()).annualPremium());
        }

        assertThat(premiums).hasSize(1).containsExactly(new BigDecimal("6850.00"));
    }

    @Test
    @Tag("AC-01.5")
    @DisplayName("AC-01.5: TERM_LIFE age 30 applies base rate 0.80 and age 31 applies 1.20, as the breakdown shows")
    void ageBandBoundary() {
        RuleSetVersion rules = RatingFixtures.termLifeV1();

        PremiumBreakdown thirty = calculator.calculate(rules, termLife(30, false, "5000000.00", 20));
        PremiumBreakdown thirtyOne = calculator.calculate(rules, termLife(31, false, "5000000.00", 20));

        assertThat(value(thirty, "baseRatePerThousand")).isEqualByComparingTo("0.80");
        assertThat(value(thirtyOne, "baseRatePerThousand")).isEqualByComparingTo("1.20");
        assertMoney(thirty.annualPremium(), "4650.00");
        assertMoney(thirtyOne.annualPremium(), "6850.00");
    }

    @Test
    @Tag("AC-01.5")
    @DisplayName("AC-01.5: bands are inclusive at both ends for every TERM_LIFE age band edge")
    void everyBandEdgeIsInclusive() {
        RuleSetVersion rules = RatingFixtures.termLifeV1();
        Map<Integer, String> expectedRate = Map.of(
                18, "0.80", 30, "0.80", 31, "1.20", 40, "1.20", 41, "2.50", 50, "2.50",
                51, "5.00", 60, "5.00", 61, "9.00", 65, "9.00");

        expectedRate.forEach((age, rate) -> {
            PremiumBreakdown quote = calculator.calculate(rules, termLife(age, false, "5000000.00", 20));
            assertThat(value(quote, "baseRatePerThousand")).as("age " + age).isEqualByComparingTo(rate);
        });
    }

    @Test
    @Tag("AC-01.6")
    @DisplayName("AC-01.6: a MOTOR rule set with baseRate 0.030 prices the AC-01.2 input at 18150.00 with no code change")
    void ratesComeFromTheRuleSet() {
        PremiumBreakdown v1 = calculator.calculate(RatingFixtures.motorV1(), motorAc012());
        PremiumBreakdown v2 = calculator.calculate(RatingFixtures.motorV2(), motorAc012());

        assertMoney(v1.annualPremium(), "15150.00");
        assertMoney(v2.annualPremium(), "18150.00");
        assertThat(value(v2, "baseRate")).isEqualByComparingTo("0.030");
    }

    @Test
    @Tag("AC-01.8")
    @DisplayName("AC-01.8: the TERM_LIFE breakdown lists every applied factor and recomputing from it alone gives the premium")
    void termLifeBreakdownRecomputes() {
        PremiumBreakdown quote = calculator.calculate(RatingFixtures.termLifeV1(), termLifeAc011());

        assertThat(names(quote)).containsExactly(
                "sumInsured", "baseRatePerThousand", "smokerFactor", "termFactor", "policyFee");
        assertThat(value(quote, "policyFee")).isEqualByComparingTo("250.00");

        BigDecimal rebuilt = Money.intermediate(
                value(quote, "sumInsured").divide(new BigDecimal("1000"), Money.INTERMEDIATE_SCALE, RoundingMode.HALF_UP));
        for (String factor : List.of("baseRatePerThousand", "smokerFactor", "termFactor")) {
            rebuilt = Money.intermediate(rebuilt.multiply(value(quote, factor)));
        }
        rebuilt = Money.finalAmount(rebuilt.add(value(quote, "policyFee")));

        assertThat(rebuilt).isEqualTo(quote.annualPremium());
    }

    @Test
    @Tag("AC-01.8")
    @DisplayName("AC-01.8: the MOTOR breakdown lists every applied factor and recomputing from it alone gives the premium")
    void motorBreakdownRecomputes() {
        Map<String, Object> busy = RatingFixtures.with(
                RatingFixtures.with(motorAc012(), "claimsLast3Years", 2), "vehicleUse", "COMMERCIAL");

        for (Map<String, Object> inputs : List.of(motorAc012(), busy)) {
            PremiumBreakdown quote = calculator.calculate(RatingFixtures.motorV1(), inputs);

            assertThat(names(quote)).containsExactly("vehicleValue", "baseRate", "vehicleAgeFactor", "driverAgeFactor",
                    "claimLoading", "claimsLast3Years", "ncbDiscount", "useFactor", "policyFee");
            BigDecimal rebuilt = Money.intermediate(value(quote, "vehicleValue").multiply(value(quote, "baseRate")));
            rebuilt = Money.intermediate(rebuilt.multiply(value(quote, "vehicleAgeFactor")));
            rebuilt = Money.intermediate(rebuilt.multiply(value(quote, "driverAgeFactor")));
            BigDecimal loading = BigDecimal.ONE.add(value(quote, "claimLoading").multiply(value(quote, "claimsLast3Years")));
            rebuilt = Money.intermediate(rebuilt.multiply(loading));
            rebuilt = Money.intermediate(rebuilt.multiply(BigDecimal.ONE.subtract(value(quote, "ncbDiscount"))));
            rebuilt = Money.intermediate(rebuilt.multiply(value(quote, "useFactor")));
            rebuilt = Money.finalAmount(rebuilt.add(value(quote, "policyFee")));

            assertThat(rebuilt).isEqualTo(quote.annualPremium());
        }
        assertMoney(calculator.calculate(RatingFixtures.motorV1(), busy).annualPremium(), "29550.00");
    }

    @Test
    @Tag("AC-01.8")
    @DisplayName("AC-01.8: the HOUSEHOLD breakdown lists every applied factor and recomputing from it alone gives the premium")
    void householdBreakdownRecomputes() {
        PremiumBreakdown quote = calculator.calculate(RatingFixtures.householdV1(), householdAc013());

        assertThat(names(quote)).containsExactly("buildingSumInsured", "buildingRate", "contentsSumInsured",
                "contentsRate", "constructionFactor", "floodFactor", "securityFactor", "policyFee");

        BigDecimal building = Money.intermediate(value(quote, "buildingSumInsured").multiply(value(quote, "buildingRate")));
        BigDecimal contents = Money.intermediate(value(quote, "contentsSumInsured").multiply(value(quote, "contentsRate")));
        BigDecimal rebuilt = Money.intermediate(building.add(contents));
        for (String factor : List.of("constructionFactor", "floodFactor", "securityFactor")) {
            rebuilt = Money.intermediate(rebuilt.multiply(value(quote, factor)));
        }
        rebuilt = Money.finalAmount(rebuilt.add(value(quote, "policyFee")));

        assertThat(rebuilt).isEqualTo(quote.annualPremium());
    }

    @Test
    @Tag("AC-01.1")
    @DisplayName("AC-01.1: the facade selects the rating model by the rule set's ratingModel")
    void modelIsSelectedByRatingModel() {
        assertThat(names(calculator.calculate(RatingFixtures.motorV1(), motorAc012()))).contains("vehicleValue");
        assertThat(names(calculator.calculate(RatingFixtures.householdV1(), householdAc013()))).contains("buildingRate");
        assertThat(names(calculator.calculate(RatingFixtures.termLifeV1(), termLifeAc011()))).contains("termFactor");
    }

    @Test
    @Tag("AC-01.7")
    @DisplayName("AC-01.7: pricing invalid risk inputs throws with one violation per invalid field and prices nothing")
    void invalidInputsAreNotPriced() {
        Map<String, Object> inputs = RatingFixtures.with(termLifeAc011(), "sumInsured", "100.00");

        assertThatThrownBy(() -> calculator.calculate(RatingFixtures.termLifeV1(), inputs))
                .isInstanceOfSatisfying(InvalidRiskInputException.class,
                        e -> assertThat(e.violations()).extracting(RiskInputViolation::field).containsExactly("sumInsured"));
    }
}
