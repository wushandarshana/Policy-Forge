package com.horizon.archfixtures.money.domain;

import java.math.BigDecimal;

/** VIOLATION: builds a BigDecimal from a double (binary rounding error), with no double in any signature. */
public class MoneyFromDouble {
    public BigDecimal viaConstructor() {
        return new BigDecimal(1.1d);
    }

    public BigDecimal viaValueOf() {
        return BigDecimal.valueOf(2.2d);
    }
}
