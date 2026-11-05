package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.endorsement.EndorsementType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What an endorsement would do, without doing it (C5): the resolved effective date, the premium now and after the
 * change, the pro-rata adjustment and the day counts it comes from. A type that does not re-rate keeps the current
 * premium and has an adjustment of 0.00.
 */
public record EndorsementPreview(
        EndorsementType type,
        LocalDate effectiveDate,
        BigDecimal currentAnnualPremium,
        BigDecimal newAnnualPremium,
        BigDecimal adjustment,
        long unexpiredDays,
        long termDays) {
}
