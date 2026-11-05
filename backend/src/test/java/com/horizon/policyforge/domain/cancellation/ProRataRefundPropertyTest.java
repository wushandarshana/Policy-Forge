package com.horizon.policyforge.domain.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-08.5: a fixed-seed property test over 1,000 random cases. Money is built from long cents, never a double. */
@Tag("AC-08")
class ProRataRefundPropertyTest {

    private static final int CASES = 1000;

    @Test
    @Tag("AC-08.5")
    @DisplayName("AC-08.5: over 1,000 random cases 0.00 <= refund <= premium, scale exactly 2, and nothing throws")
    void refundStaysWithinPremium() {
        Random random = new Random(20261104L);
        ProRataRefundCalculator calculator = new ProRataRefundCalculator();
        int coolingOffCases = 0;
        int proRataCases = 0;
        for (int i = 0; i < CASES; i++) {
            LocalDate start = LocalDate.of(2026, 1, 1).plusDays(random.nextInt(3650));
            LocalDate end = start.plusMonths(1 + random.nextInt(24)).minusDays(1);
            long termDays = ChronoUnit.DAYS.between(start, end.plusDays(1));
            LocalDate cancellation = start.plusDays(random.nextInt((int) termDays));
            BigDecimal premium = BigDecimal.valueOf(random.nextInt(1_000_000_000), 2);
            BigDecimal fee = BigDecimal.valueOf(random.nextInt(50_000_000), 2);
            int coolingOffDays = random.nextInt(31);
            boolean firstTerm = random.nextBoolean();

            RefundQuote quote = calculator.calculate(premium, start, end, cancellation, start, firstTerm,
                    coolingOffDays, fee);

            String context = "case " + i + " premium=" + premium + " fee=" + fee + " " + start + ".." + end
                    + " cancel=" + cancellation;
            assertThat(quote.amount().scale()).as(context).isEqualTo(2);
            assertThat(quote.amount()).as(context).isGreaterThanOrEqualTo(new BigDecimal("0.00"));
            assertThat(quote.amount()).as(context).isLessThanOrEqualTo(premium);
            assertThat(quote.fee().scale()).as(context).isEqualTo(2);
            assertThat(quote.termDays()).as(context).isEqualTo((int) termDays);
            if (quote.coolingOff()) {
                coolingOffCases++;
                assertThat(quote.amount()).as(context).isEqualByComparingTo(premium);
            } else {
                proRataCases++;
            }
        }
        assertThat(coolingOffCases).as("the generator exercises cooling-off").isPositive();
        assertThat(proRataCases).as("the generator exercises pro-rata").isPositive();
    }
}
