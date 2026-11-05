package com.horizon.policyforge.domain.renewal;

import java.time.LocalDate;

/** The first and last day (both inclusive) of a renewal term. */
public record RenewalDates(LocalDate startDate, LocalDate endDate) {
}
