package com.horizon.policyforge.domain.renewal;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The dates and payment state of one policy term, as the renewal and portfolio rules see it (AC-07). Both dates are
 * inclusive. Term numbers start at 1 and only grow.
 */
public record TermPeriod(int termNo, LocalDate startDate, LocalDate endDate, boolean paid) {

    public TermPeriod {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
    }
}
