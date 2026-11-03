package com.horizon.policyforge.domain.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The pro-rata refund against the premium-calc-evaluator oracle (premium_check.py). */
@Tag("AC-08")
class ProRataRefundCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2027, 10, 31);

    private final ProRataRefundCalculator calculator = new ProRataRefundCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
        assertThat(actual.scale()).isEqualTo(2);
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: TERM_LIFE 6850.00, 365 days, cancelled 2027-02-09 with fee 500.00 refunds 4473.29")
    void termLifeProRata() {
        RefundQuote quote = calculator.calculate(d("6850.00"), START, END, LocalDate.of(2027, 2, 9), START, true, 15,
                d("500.00"));
        assertThat(quote.termDays()).isEqualTo(365);
        assertThat(quote.unexpiredDays()).isEqualTo(265);
        assertThat(quote.coolingOff()).isFalse();
        assertMoney(quote.fee(), "500.00");
        assertMoney(quote.amount(), "4473.29");
        assertThat(quote.formula()).isEqualTo("max(0, 6850.00 * 265 / 365 - 500.00) = 4473.29");
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: MOTOR 15150.00 with fee 250.00 refunds 10749.32 and HOUSEHOLD 11350.00 with fee 200.00 refunds 8040.41")
    void motorAndHouseholdProRata() {
        LocalDate cancel = LocalDate.of(2027, 2, 9);
        RefundQuote motor = calculator.calculate(d("15150.00"), START, END, cancel, START, true, 7, d("250.00"));
        RefundQuote household = calculator.calculate(d("11350.00"), START, END, cancel, START, true, 14, d("200.00"));
        assertMoney(motor.amount(), "10749.32");
        assertMoney(household.amount(), "8040.41");
        assertThat(motor.formula()).isEqualTo("max(0, 15150.00 * 265 / 365 - 250.00) = 10749.32");
    }

    @Test
    @Tag("AC-08.2")
    @DisplayName("AC-08.2: cooling-off 15, cancelled 2026-11-11 refunds the full 6850.00 with fee 0.00")
    void coolingOffRefundsFullPremium() {
        RefundQuote quote = calculator.calculate(d("6850.00"), START, END, LocalDate.of(2026, 11, 11), START, true, 15,
                d("500.00"));
        assertThat(quote.coolingOff()).isTrue();
        assertMoney(quote.fee(), "0.00");
        assertMoney(quote.amount(), "6850.00");
        assertThat(quote.formula()).isEqualTo("cooling-off: full premium 6850.00 = 6850.00");
        assertThat(quote.termDays()).isEqualTo(365);
        assertThat(quote.unexpiredDays()).isEqualTo(355);
    }

    @Test
    @Tag("AC-08.2")
    @DisplayName("AC-08.2: day 14 (2026-11-15) is still full premium and day 15 (2026-11-16) is pro-rata 6068.49")
    void coolingOffBoundary() {
        RefundQuote day14 = calculator.calculate(d("6850.00"), START, END, LocalDate.of(2026, 11, 15), START, true, 15,
                d("500.00"));
        RefundQuote day15 = calculator.calculate(d("6850.00"), START, END, LocalDate.of(2026, 11, 16), START, true, 15,
                d("500.00"));
        assertThat(day14.coolingOff()).isTrue();
        assertMoney(day14.amount(), "6850.00");
        assertThat(day15.coolingOff()).isFalse();
        assertMoney(day15.fee(), "500.00");
        assertMoney(day15.amount(), "6068.49");
    }

    @Test
    @Tag("AC-08.2")
    @DisplayName("AC-08.2: a renewal term cancelled on 2027-11-03 refunds 6312.57 and never the full premium")
    void renewalTermHasNoCoolingOff() {
        RefundQuote quote = calculator.calculate(d("6850.00"), LocalDate.of(2027, 11, 1), LocalDate.of(2028, 10, 31),
                LocalDate.of(2027, 11, 3), START, false, 15, d("500.00"));
        assertThat(quote.coolingOff()).isFalse();
        assertThat(quote.termDays()).isEqualTo(366);
        assertThat(quote.unexpiredDays()).isEqualTo(364);
        assertMoney(quote.amount(), "6312.57");
    }

    @Test
    @Tag("AC-08.2")
    @DisplayName("AC-08.2: zero cooling-off days never gives a full refund on the first day")
    void zeroCoolingOffDays() {
        RefundQuote quote = calculator.calculate(d("6850.00"), START, END, START, START, true, 0, d("500.00"));
        assertThat(quote.coolingOff()).isFalse();
        assertMoney(quote.amount(), "6350.00");
    }

    @Test
    @Tag("AC-08.3")
    @DisplayName("AC-08.3: cancelled on the last day (1 unexpired day) with fee 500.00 refunds 0.00, never negative")
    void feeLargerThanProRataFloorsAtZero() {
        RefundQuote quote = calculator.calculate(d("6850.00"), START, END, END, START, true, 15, d("500.00"));
        assertThat(quote.unexpiredDays()).isEqualTo(1);
        assertMoney(quote.amount(), "0.00");
        assertThat(quote.amount().signum()).isZero();
        assertThat(quote.formula()).isEqualTo("max(0, 6850.00 * 1 / 365 - 500.00) = 0.00");
    }
}
