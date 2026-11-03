package com.horizon.policyforge.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.renewal.TermPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The portfolio figures of the admin dashboard: premium collected, renewal pipeline and lapse forecast. */
@Tag("AC-11")
class PortfolioRulesTest {

    private static final LocalDate AS_OF = LocalDate.of(2027, 10, 15);

    private final PortfolioRules rules = new PortfolioRules();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** A single paid term that ends {@code daysAfterAsOf} days after {@link #AS_OF} and started a year earlier. */
    private static List<TermPeriod> paidTermEnding(int daysAfterAsOf) {
        LocalDate end = AS_OF.plusDays(daysAfterAsOf);
        return List.of(new TermPeriod(1, end.minusYears(1).plusDays(1), end, true));
    }

    @Test
    @Tag("AC-11.2")
    @DisplayName("AC-11.2: 6850.00 + 6850.00 + 1330.85 - 4473.29 is exactly 10557.56 at scale 2")
    void premiumCollectedIsExact() {
        BigDecimal collected = rules.premiumCollected(List.of(d("6850.00"), d("6850.00")), List.of(d("1330.85")),
                List.of(d("4473.29")));
        assertThat(collected).isEqualByComparingTo("10557.56");
        assertThat(collected.scale()).isEqualTo(2);
    }

    @Test
    @Tag("AC-11.2")
    @DisplayName("AC-11.2: an empty product collects 0.00 and credits (negative adjustments) reduce the figure")
    void emptyAndCredits() {
        BigDecimal empty = rules.premiumCollected(List.of(), List.of(), List.of());
        assertThat(empty).isEqualByComparingTo("0.00");
        assertThat(empty.scale()).isEqualTo(2);
        assertThat(rules.premiumCollected(List.of(d("100.10")), List.of(d("-0.20")), List.of()))
                .isEqualByComparingTo("99.90");
    }

    @Test
    @Tag("AC-11.2")
    @DisplayName("AC-11.2: the total equals the exact sum of the per-product figures with no floating-point drift")
    void totalIsTheSumOfTheProducts() {
        BigDecimal termLife = rules.premiumCollected(List.of(d("6850.00"), d("6850.00")), List.of(d("1330.85")),
                List.of(d("4473.29")));
        BigDecimal motor = rules.premiumCollected(List.of(d("15150.00")), List.of(), List.of());
        BigDecimal household = rules.premiumCollected(List.of(d("0.10")), List.of(d("0.20")), List.of());
        BigDecimal total = rules.total(List.of(termLife, motor, household));
        assertThat(termLife).isEqualByComparingTo("10557.56");
        assertThat(total).isEqualByComparingTo("25707.86");
        assertThat(total.scale()).isEqualTo(2);
        assertThat(rules.total(List.of(d("0.10"), d("0.20")))).isEqualByComparingTo("0.30");
    }

    @Test
    @Tag("AC-11.3")
    @DisplayName("AC-11.3: a term ending in 20 days is in the pipeline and one ending in 90 days is not")
    void pipelineTwentyAndNinetyDays() {
        assertThat(rules.inRenewalPipeline(PolicyStatus.ACTIVE, paidTermEnding(20), AS_OF)).isTrue();
        assertThat(rules.inRenewalPipeline(PolicyStatus.ACTIVE, paidTermEnding(90), AS_OF)).isFalse();
    }

    @Test
    @Tag("AC-11.3")
    @DisplayName("AC-11.3: the pipeline boundary is 60 days in and 61 days out")
    void pipelineBoundary() {
        assertThat(rules.inRenewalPipeline(PolicyStatus.ACTIVE, paidTermEnding(60), AS_OF)).isTrue();
        assertThat(rules.inRenewalPipeline(PolicyStatus.ACTIVE, paidTermEnding(61), AS_OF)).isFalse();
        assertThat(rules.inRenewalPipeline(PolicyStatus.ACTIVE, paidTermEnding(0), AS_OF)).isTrue();
    }

    @Test
    @Tag("AC-11.3")
    @DisplayName("AC-11.3: only in-force policies with a paid term in force on asOf are in the pipeline")
    void pipelineNeedsInForceAndATermInForce() {
        assertThat(rules.inRenewalPipeline(PolicyStatus.RENEWED, paidTermEnding(20), AS_OF)).isTrue();
        assertThat(rules.inRenewalPipeline(PolicyStatus.ENDORSED, paidTermEnding(20), AS_OF)).isTrue();
        assertThat(rules.inRenewalPipeline(PolicyStatus.LAPSED, paidTermEnding(20), AS_OF)).isFalse();
        assertThat(rules.inRenewalPipeline(PolicyStatus.CANCELLED, paidTermEnding(20), AS_OF)).isFalse();
        assertThat(rules.inRenewalPipeline(PolicyStatus.ACTIVE, paidTermEnding(-1), AS_OF)).isFalse();
    }

    @Test
    @Tag("AC-11.3")
    @DisplayName("AC-11.3: a paid renewal does not hide the term in force, so a policy renewing early stays in the pipeline")
    void pipelineUsesTheTermInForce() {
        LocalDate end = AS_OF.plusDays(20);
        List<TermPeriod> terms = List.of(
                new TermPeriod(1, end.minusYears(1).plusDays(1), end, true),
                new TermPeriod(2, end.plusDays(1), end.plusYears(1), true));
        assertThat(rules.inRenewalPipeline(PolicyStatus.RENEWED, terms, AS_OF)).isTrue();
    }

    @Test
    @Tag("AC-11.4")
    @DisplayName("AC-11.4: an in-force policy whose current paid term ended before asOf with no paid renewal is in the forecast")
    void forecastIncludesExpiredUnpaid() {
        LocalDate end = AS_OF.minusDays(5);
        List<TermPeriod> terms = List.of(
                new TermPeriod(1, end.minusYears(1).plusDays(1), end, true),
                new TermPeriod(2, end.plusDays(1), end.plusYears(1), false));
        assertThat(rules.inLapseForecast(PolicyStatus.ACTIVE, terms, AS_OF)).isTrue();
        assertThat(rules.inLapseForecast(PolicyStatus.ENDORSED, terms, AS_OF)).isTrue();
    }

    @Test
    @Tag("AC-11.4")
    @DisplayName("AC-11.4: a paid renewal, LAPSED, CANCELLED and a not-yet-ended term are out of the forecast")
    void forecastExclusions() {
        LocalDate end = AS_OF.minusDays(5);
        TermPeriod paidFirst = new TermPeriod(1, end.minusYears(1).plusDays(1), end, true);
        List<TermPeriod> expiredUnpaid = List.of(paidFirst,
                new TermPeriod(2, end.plusDays(1), end.plusYears(1), false));
        List<TermPeriod> paidRenewal = List.of(paidFirst,
                new TermPeriod(2, end.plusDays(1), end.plusYears(1), true));

        assertThat(rules.inLapseForecast(PolicyStatus.RENEWED, paidRenewal, AS_OF)).isFalse();
        assertThat(rules.inLapseForecast(PolicyStatus.LAPSED, expiredUnpaid, AS_OF)).isFalse();
        assertThat(rules.inLapseForecast(PolicyStatus.CANCELLED, expiredUnpaid, AS_OF)).isFalse();
        assertThat(rules.inLapseForecast(PolicyStatus.ACTIVE, paidTermEnding(20), AS_OF)).isFalse();
        assertThat(rules.inLapseForecast(PolicyStatus.ACTIVE, paidTermEnding(0), AS_OF)).isFalse();
    }
}
