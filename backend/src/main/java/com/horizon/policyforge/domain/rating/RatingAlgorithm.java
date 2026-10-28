package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.util.List;
import java.util.Map;

/** One product's rating model: validates its risk inputs against a rule set and prices them. */
public interface RatingAlgorithm {

    /** The input keys this model accepts and which of them are health data (AC-01.9, NFR-04.2). */
    RiskInputSchema schema();

    /** One violation per missing or invalid input; empty when the inputs can be priced. */
    List<RiskInputViolation> validate(RuleSetVersion rules, Map<String, Object> riskInputs);

    /** Prices inputs that have passed {@link #validate}; throws {@link InvalidRiskInputException} otherwise. */
    PremiumBreakdown rate(RuleSetVersion rules, Map<String, Object> riskInputs);
}
