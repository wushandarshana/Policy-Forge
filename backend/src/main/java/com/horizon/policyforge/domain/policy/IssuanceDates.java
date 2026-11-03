package com.horizon.policyforge.domain.policy;

import java.time.LocalDate;
import java.util.Objects;

/** The pure issuance date rules (AC-05.1). Today is always an argument; no clock is read here. */
public final class IssuanceDates {

    /** How many days after today a policy may start. */
    public static final int MAX_DAYS_AHEAD = 60;

    private IssuanceDates() {
    }

    /**
     * The effective date to issue with: {@code requested}, or {@code today} when it is null. It must lie from today to
     * today + 60 days, both inclusive.
     *
     * @throws InvalidEffectiveDateException when the date is outside that window
     */
    public static LocalDate resolveEffectiveDate(LocalDate requested, LocalDate today) {
        Objects.requireNonNull(today, "today");
        LocalDate effective = requested == null ? today : requested;
        if (effective.isBefore(today) || effective.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new InvalidEffectiveDateException();
        }
        return effective;
    }

    /** The last day of cover: {@code effectiveDate + termMonths - 1 day}. */
    public static LocalDate expiryDate(LocalDate effectiveDate, int termMonths) {
        if (termMonths < 1) {
            throw new IllegalArgumentException("termMonths must be at least 1");
        }
        return effectiveDate.plusMonths(termMonths).minusDays(1);
    }
}
