package com.horizon.policyforge.domain.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The refund premium basis (AC-08.1): term premium plus the adjustments effective in that term. */
@Tag("AC-08")
class RefundPremiumBasisTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2027, 10, 31);

    private final RefundPremiumBasis basis = new RefundPremiumBasis();
    private final ProRataRefundCalculator calculator = new ProRataRefundCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: 6850.00 + 1330.85 effective 2027-05-01 gives a basis of 8180.85 and a 2929.23 refund on 2027-06-01")
    void endorsementAdjustmentRaisesTheBasis() {
        BigDecimal result = basis.basis(d("6850.00"), START, END,
                List.of(new DatedAdjustment(LocalDate.of(2027, 5, 1), d("1330.85"))));
        assertThat(result).isEqualByComparingTo("8180.85");
        assertThat(result.scale()).isEqualTo(2);

        RefundQuote quote = calculator.calculate(result, START, END, LocalDate.of(2027, 6, 1), START, true, 15,
                d("500.00"));
        assertThat(quote.unexpiredDays()).isEqualTo(153);
        assertThat(quote.amount()).isEqualByComparingTo("2929.23");
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: an endorsement effective in another term is excluded from the basis")
    void otherTermAdjustmentsExcluded() {
        BigDecimal result = basis.basis(d("6850.00"), START, END, List.of(
                new DatedAdjustment(START.minusDays(1), d("100.00")),
                new DatedAdjustment(END.plusDays(1), d("200.00")),
                new DatedAdjustment(LocalDate.of(2027, 5, 1), d("1330.85"))));
        assertThat(result).isEqualByComparingTo("8180.85");
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: adjustments on the first and last day of the term are included and credits reduce the basis")
    void boundaryAndCreditAdjustments() {
        BigDecimal result = basis.basis(d("6850.00"), START, END, List.of(
                new DatedAdjustment(START, d("100.00")),
                new DatedAdjustment(END, d("-50.50"))));
        assertThat(result).isEqualByComparingTo("6899.50");
        assertThat(basis.basis(d("6850.00"), START, END, List.of())).isEqualByComparingTo("6850.00");
        assertThat(basis.basis(d("6850"), START, END, List.of()).scale()).isEqualTo(2);
    }
}
