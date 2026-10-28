package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.util.List;
import java.util.Map;

/**
 * Validates risk inputs for the product a rule set rates: one violation per missing or invalid field, including a sum
 * insured outside the rule set's min and max. Inputs outside every rating band are violations, never defaulted.
 */
public final class RiskInputValidator {

    public List<RiskInputViolation> validate(RuleSetVersion rules, Map<String, Object> riskInputs) {
        return RatingAlgorithms.forModel(rules.model()).validate(rules, riskInputs);
    }
}
