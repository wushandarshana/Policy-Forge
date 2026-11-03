package com.horizon.policyforge.domain.cancellation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The pro-rata refund of a cancelled term (AC-08.1 to AC-08.5). */
public final class ProRataRefundCalculator {

    /** Calculates the refund. */
    public RefundQuote calculate(BigDecimal premium, LocalDate termStart, LocalDate termEnd,
            LocalDate cancellationDate, LocalDate policyEffectiveDate, boolean firstTerm, int coolingOffDays,
            BigDecimal cancellationFee) {
        return new RefundQuote(0, 0, false, BigDecimal.ZERO, BigDecimal.ZERO, "");
    }
}
