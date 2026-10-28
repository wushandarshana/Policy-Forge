package com.horizon.archfixtures.money.domain;

/** VIOLATION: a boxed Float field. */
public class MoneyBoxedFloatField {
    private Float rate;

    public boolean hasRate() {
        return rate != null;
    }
}
