package com.horizon.policyforge.domain.renewal;

import java.time.LocalDate;

/** The pure lapse rule (AC-07.4, AC-07.6). */
public final class GracePeriodPolicy {

    /** Whether an unpaid term has lapsed on {@code runDate}. */
    public boolean isLapsed(LocalDate termEnd, int graceDays, LocalDate runDate, boolean paid) {
        return false;
    }
}
