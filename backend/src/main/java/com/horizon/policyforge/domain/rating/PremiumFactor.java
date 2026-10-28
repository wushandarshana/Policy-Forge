package com.horizon.policyforge.domain.rating;

import java.math.BigDecimal;

/**
 * One applied value of a premium calculation: a rate, factor, fee or the amount it was applied to.
 *
 * @param factor the name used in the rule set or the risk input, e.g. {@code baseRatePerThousand}
 * @param value  the applied value, exactly as used in the calculation
 */
public record PremiumFactor(String factor, BigDecimal value) {
}
