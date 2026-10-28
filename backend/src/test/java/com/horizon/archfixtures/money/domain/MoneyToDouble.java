package com.horizon.archfixtures.money.domain;

import java.math.BigDecimal;

/** VIOLATION: converts a BigDecimal to a double or float, with no double in any signature. */
public class MoneyToDouble {
    public int viaDoubleValue(BigDecimal amount) {
        return (int) amount.doubleValue();
    }

    public int viaFloatValue(BigDecimal amount) {
        return (int) amount.floatValue();
    }
}
