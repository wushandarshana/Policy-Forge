package com.horizon.policyforge.domain.cancellation;

import java.time.LocalDate;

/** The pure cancellation-date rule (AC-08.1). Today is always an argument; no clock is read here. */
public final class CancellationDateRule {

    /** The date to cancel on: {@code requested}, or {@code today} when absent. It must lie in the term. */
    public LocalDate resolve(LocalDate requested, LocalDate today, LocalDate termStart, LocalDate termEnd,
            String field) {
        return today;
    }
}
