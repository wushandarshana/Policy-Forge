package com.horizon.archfixtures.money.domain;

/** VIOLATION: a primitive double field. */
public class MoneyDoubleField {
    private double premium;

    public long cents() {
        return Math.round(premium * 100);
    }
}
