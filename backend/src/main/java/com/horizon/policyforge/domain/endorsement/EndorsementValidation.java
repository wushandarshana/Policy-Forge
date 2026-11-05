package com.horizon.policyforge.domain.endorsement;

import java.util.List;

/**
 * The outcome of {@link EndorsementValidator}: either a normalised command (no violations) or every business-rule
 * violation, in E-order.
 */
public record EndorsementValidation(EndorsementCommand command, List<EndorsementViolation> violations) {

    public EndorsementValidation {
        violations = List.copyOf(violations);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    /** The code of the first violation in E-order (C6), or null when valid. */
    public String firstCode() {
        return violations.isEmpty() ? null : violations.get(0).code();
    }
}
