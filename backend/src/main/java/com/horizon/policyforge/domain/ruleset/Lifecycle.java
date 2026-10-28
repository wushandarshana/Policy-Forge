package com.horizon.policyforge.domain.ruleset;

import java.math.BigDecimal;

/**
 * Lifecycle parameters of a product version. A value is null only in an unvalidated draft that is missing it.
 *
 * @param cancellationFee money, scale 2
 */
public record Lifecycle(
        Integer termMonths,
        Integer gracePeriodDays,
        Integer coolingOffDays,
        BigDecimal cancellationFee,
        Integer renewalOfferDaysBeforeExpiry,
        Integer quoteValidityDays) {
}
