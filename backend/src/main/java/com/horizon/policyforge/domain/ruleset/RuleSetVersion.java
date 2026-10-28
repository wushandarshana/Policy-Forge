package com.horizon.policyforge.domain.ruleset;

import java.time.LocalDate;
import java.util.List;

/**
 * One immutable, versioned rule set of a product: rating factors, underwriting rules and lifecycle parameters.
 *
 * <p>The record also represents a draft that has not been validated yet, so a missing section is {@code null} and the
 * rating model is kept as the string that was written. {@link RuleSetValidator} reports everything wrong with a draft,
 * and only a rule set with no violations is stored or used for pricing.
 */
public record RuleSetVersion(
        String productCode,
        String productName,
        int version,
        LocalDate effectiveFrom,
        String ratingModel,
        String currency,
        SumInsuredLimits sumInsured,
        RatingFactors rating,
        List<UnderwritingRule> underwritingRules,
        Lifecycle lifecycle) {

    public RuleSetVersion {
        underwritingRules = underwritingRules == null ? null : List.copyOf(underwritingRules);
    }

    /** The rating model of a validated rule set. */
    public RatingModel model() {
        return RatingModel.parse(ratingModel)
                .orElseThrow(() -> new IllegalStateException("Unknown rating model on rule set " + productCode));
    }

    /** Whether this version is in force on {@code today}: its effective date is on or before that day. */
    public boolean isEffectiveOn(LocalDate today) {
        return effectiveFrom != null && !effectiveFrom.isAfter(today);
    }
}
