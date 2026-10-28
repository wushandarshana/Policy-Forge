package com.horizon.policyforge.domain.underwriting;

import com.horizon.policyforge.domain.ruleset.Condition;
import java.util.Map;

/** Evaluates one underwriting condition against the facts of a risk. Stateless and deterministic. */
public final class ConditionEvaluator {

    public boolean holds(Condition condition, Map<String, Object> facts) {
        throw new UnsupportedOperationException("not implemented");
    }
}
