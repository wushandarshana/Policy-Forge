package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.PolicyForgeDomainException;
import java.util.List;

/** Raised when a premium is requested for risk inputs that fail validation. */
public class InvalidRiskInputException extends PolicyForgeDomainException {

    private final List<RiskInputViolation> violations;

    public InvalidRiskInputException(List<RiskInputViolation> violations) {
        super("Invalid risk input: " + violations.size() + " field(s)");
        this.violations = List.copyOf(violations);
    }

    public List<RiskInputViolation> violations() {
        return violations;
    }
}
