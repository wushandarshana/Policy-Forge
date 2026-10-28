package com.horizon.archfixtures.money.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Allowed: BigDecimal only. */
public class MoneyOk {
    private BigDecimal premium;

    public BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal premium() {
        return premium;
    }
}
