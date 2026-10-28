package com.horizon.policyforge.domain.ruleset;

import java.math.BigDecimal;

/** One row of a banded factor table. {@code min} and {@code max} are whole numbers, inclusive at both ends. */
public record Band(int min, int max, BigDecimal factor) {

    /** True when {@code value} lies in the band, inclusive at both ends. */
    public boolean contains(int value) {
        return value >= min && value <= max;
    }
}
