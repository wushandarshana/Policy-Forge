package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.PolicyForgeDomainException;
import java.util.List;

/** A manual decision or override request failed validation (400 at the web edge). Nothing has changed. */
public class InvalidDecisionException extends PolicyForgeDomainException {

    private final List<DecisionViolation> violations;

    public InvalidDecisionException(List<DecisionViolation> violations) {
        super("Invalid decision: " + violations.size() + " field(s)");
        this.violations = List.copyOf(violations);
    }

    public List<DecisionViolation> violations() {
        return violations;
    }
}
