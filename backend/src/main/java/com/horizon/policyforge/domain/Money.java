package com.horizon.policyforge.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed-point money helper (NFR-01). Amounts are {@link BigDecimal}: scale 2 with {@link RoundingMode#HALF_UP} at the
 * boundary and scale 10 for intermediate steps. A value is never built from a binary floating-point number.
 */
public final class Money {

    /** Scale of an amount that is stored, compared or returned. */
    public static final int FINAL_SCALE = 2;

    /** Scale of every intermediate multiply or divide. */
    public static final int INTERMEDIATE_SCALE = 10;

    private Money() {
    }

    /** Parses a decimal string and rounds it to the boundary scale. */
    public static BigDecimal of(String amount) {
        return finalAmount(new BigDecimal(amount));
    }

    /** Rounds an intermediate result to scale 10, HALF_UP. */
    public static BigDecimal intermediate(BigDecimal value) {
        return value.setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Rounds a boundary amount to scale 2, HALF_UP. */
    public static BigDecimal finalAmount(BigDecimal value) {
        return value.setScale(FINAL_SCALE, RoundingMode.HALF_UP);
    }
}
