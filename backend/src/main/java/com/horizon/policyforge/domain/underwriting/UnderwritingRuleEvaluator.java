package com.horizon.policyforge.domain.underwriting;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.math.BigDecimal;
import java.util.Map;

/** Evaluates every underwriting rule of a rule-set version against a risk (AC-04). Stateless and deterministic. */
public final class UnderwritingRuleEvaluator {

    public UnderwritingResult evaluate(RuleSetVersion ruleSet, Map<String, Object> facts) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static Map<String, Object> withDerived(Map<String, Object> riskInputs, BigDecimal sumInsured, Integer age) {
        throw new UnsupportedOperationException("not implemented");
    }
}
