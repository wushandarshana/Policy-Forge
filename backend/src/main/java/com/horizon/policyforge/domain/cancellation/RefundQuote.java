package com.horizon.policyforge.domain.cancellation;

import java.math.BigDecimal;

/**
 * The result of a refund calculation (AC-08). {@code fee} and {@code amount} are money at scale 2. {@code formula}
 * holds only the numbers of the calculation.
 */
public record RefundQuote(int termDays, int unexpiredDays, boolean coolingOff, BigDecimal fee, BigDecimal amount,
        String formula) {
}
