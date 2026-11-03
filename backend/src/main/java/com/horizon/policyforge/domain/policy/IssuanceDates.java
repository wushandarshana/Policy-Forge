package com.horizon.policyforge.domain.policy;

import java.time.LocalDate;

/** Red stub. */
public final class IssuanceDates {

    public static final int MAX_DAYS_AHEAD = 60;

    private IssuanceDates() {
    }

    public static LocalDate resolveEffectiveDate(LocalDate requested, LocalDate today) {
        return null;
    }

    public static LocalDate expiryDate(LocalDate effectiveDate, int termMonths) {
        return null;
    }
}
