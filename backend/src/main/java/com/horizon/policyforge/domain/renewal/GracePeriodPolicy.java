package com.horizon.policyforge.domain.renewal;

import java.time.LocalDate;
import java.util.Objects;

/** The pure lapse rule (AC-07.4, AC-07.6). */
public final class GracePeriodPolicy {

    /**
     * Whether a term has lapsed on {@code runDate}: it is unpaid and {@code runDate} is after
     * {@code termEnd + graceDays}. A paid term never lapses.
     */
    public boolean isLapsed(LocalDate termEnd, int graceDays, LocalDate runDate, boolean paid) {
        Objects.requireNonNull(termEnd, "termEnd");
        Objects.requireNonNull(runDate, "runDate");
        return !paid && runDate.isAfter(termEnd.plusDays(graceDays));
    }
}
