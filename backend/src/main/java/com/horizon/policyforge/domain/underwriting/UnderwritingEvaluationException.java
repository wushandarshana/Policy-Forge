package com.horizon.policyforge.domain.underwriting;

import com.horizon.policyforge.domain.PolicyForgeDomainException;

/**
 * A rule cannot be evaluated: a condition names a field that is absent or a value that cannot be compared (U-6). The
 * evaluator fails closed with this exception and never skips the rule. Messages name the rule and field, never the
 * input value.
 */
public class UnderwritingEvaluationException extends PolicyForgeDomainException {

    public UnderwritingEvaluationException(String message) {
        super(message);
    }
}
