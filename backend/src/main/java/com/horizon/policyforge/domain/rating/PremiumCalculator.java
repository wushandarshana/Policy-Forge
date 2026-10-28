package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.util.Map;

/**
 * Prices a quote: selects the rating model named by the rule set's {@code ratingModel} and returns the explainable
 * premium. Every factor, rate, band and fee comes from the rule set, so a new rule version needs no code change.
 */
public final class PremiumCalculator {

    /**
     * Prices {@code riskInputs} against {@code rules}.
     *
     * @throws InvalidRiskInputException when any input is missing or invalid; nothing is priced
     */
    public PremiumBreakdown calculate(RuleSetVersion rules, Map<String, Object> riskInputs) {
        return RatingAlgorithms.forModel(rules.model()).rate(rules, riskInputs);
    }
}
