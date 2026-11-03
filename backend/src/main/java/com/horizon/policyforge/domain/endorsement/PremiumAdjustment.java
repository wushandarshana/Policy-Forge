package com.horizon.policyforge.domain.endorsement;

import java.math.BigDecimal;

/** A pro-rata premium adjustment (scale 2, HALF_UP) with the day counts it was computed from. */
public record PremiumAdjustment(BigDecimal adjustment, long unexpiredDays, long termDays) {
}
