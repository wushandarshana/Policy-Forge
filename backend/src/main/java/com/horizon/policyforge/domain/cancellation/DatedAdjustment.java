package com.horizon.policyforge.domain.cancellation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A signed endorsement premium adjustment (charge positive, credit negative) and the date it takes effect. */
public record DatedAdjustment(LocalDate effectiveDate, BigDecimal amount) {
}
