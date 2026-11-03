package com.horizon.policyforge.domain.cancellation;

import com.horizon.policyforge.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The pro-rata refund of a cancelled term (AC-08.1 to AC-08.5).
 *
 * <p>{@code refund = max(0, premium x unexpiredDays / termDays - fee)}. Each multiply and divide is rounded to scale 10
 * HALF_UP, in that order (the premium-calc-evaluator oracle's order), and the result is rounded once to 2 dp HALF_UP.
 * Inside the cooling-off period of a first term the full premium is refunded and no fee applies.
 */
public final class ProRataRefundCalculator {

    /**
     * @param premium the term premium including any endorsement adjustments of that term
     * @param termStart first day of the term (inclusive)
     * @param termEnd last day of the term (inclusive)
     * @param cancellationDate the day cover ends; it must lie in the term
     * @param policyEffectiveDate the policy's effective date, the start of the cooling-off period
     * @param firstTerm whether the term is the policy's first (renewal terms never get cooling-off)
     * @param coolingOffDays cooling-off length in days
     * @param cancellationFee the fee deducted from a pro-rata refund
     * @throws IllegalArgumentException when the term is empty, the date is outside it, or an amount is negative
     */
    public RefundQuote calculate(BigDecimal premium, LocalDate termStart, LocalDate termEnd,
            LocalDate cancellationDate, LocalDate policyEffectiveDate, boolean firstTerm, int coolingOffDays,
            BigDecimal cancellationFee) {
        Objects.requireNonNull(premium, "premium");
        Objects.requireNonNull(termStart, "termStart");
        Objects.requireNonNull(termEnd, "termEnd");
        Objects.requireNonNull(cancellationDate, "cancellationDate");
        Objects.requireNonNull(policyEffectiveDate, "policyEffectiveDate");
        Objects.requireNonNull(cancellationFee, "cancellationFee");
        if (termEnd.isBefore(termStart)) {
            throw new IllegalArgumentException("The term ends before it starts");
        }
        if (cancellationDate.isBefore(termStart) || cancellationDate.isAfter(termEnd)) {
            throw new IllegalArgumentException("The cancellation date is outside the term");
        }
        if (premium.signum() < 0 || cancellationFee.signum() < 0) {
            throw new IllegalArgumentException("Premium and fee must not be negative");
        }

        LocalDate termEndExclusive = termEnd.plusDays(1);
        int termDays = Math.toIntExact(ChronoUnit.DAYS.between(termStart, termEndExclusive));
        int unexpiredDays = Math.toIntExact(ChronoUnit.DAYS.between(cancellationDate, termEndExclusive));
        BigDecimal premiumText = Money.finalAmount(premium);

        if (firstTerm && ChronoUnit.DAYS.between(policyEffectiveDate, cancellationDate) < coolingOffDays) {
            BigDecimal fee = Money.finalAmount(BigDecimal.ZERO);
            return new RefundQuote(termDays, unexpiredDays, true, fee, premiumText,
                    "cooling-off: full premium " + premiumText.toPlainString() + " = " + premiumText.toPlainString());
        }

        BigDecimal fee = Money.finalAmount(cancellationFee);
        BigDecimal multiplied = Money.intermediate(premium.multiply(BigDecimal.valueOf(unexpiredDays)));
        BigDecimal proRata = multiplied.divide(BigDecimal.valueOf(termDays), Money.INTERMEDIATE_SCALE,
                RoundingMode.HALF_UP);
        BigDecimal amount = Money.finalAmount(proRata.subtract(fee).max(BigDecimal.ZERO));
        String formula = "max(0, " + premiumText.toPlainString() + " * " + unexpiredDays + " / " + termDays + " - "
                + fee.toPlainString() + ") = " + amount.toPlainString();
        return new RefundQuote(termDays, unexpiredDays, false, fee, amount, formula);
    }
}
