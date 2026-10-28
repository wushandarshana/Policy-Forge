package com.horizon.policyforge.domain.rating;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * An explainable premium: the annual premium (scale 2), the sum insured it was priced for (scale 2) and every
 * applied factor in the order the formula uses it. Recomputing the product's formula from {@code factors} alone
 * reproduces {@code annualPremium}.
 */
public record PremiumBreakdown(BigDecimal annualPremium, BigDecimal sumInsured, List<PremiumFactor> factors) {

    public PremiumBreakdown {
        factors = List.copyOf(factors);
    }

    /** The value applied for {@code name}, if the breakdown lists it. */
    public Optional<BigDecimal> value(String name) {
        return factors.stream().filter(f -> f.factor().equals(name)).map(PremiumFactor::value).findFirst();
    }
}
