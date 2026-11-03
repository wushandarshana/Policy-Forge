package com.horizon.policyforge.domain.policy;

import com.horizon.policyforge.domain.PolicyForgeDomainException;

/**
 * A policy was asked to make a transition its current status does not allow (AC-10). A null {@code from} means a new
 * policy. Maps to 409 {@code /problems/invalid-policy-state} at the web edge.
 */
public class InvalidPolicyStateException extends PolicyForgeDomainException {

    private final PolicyStatus from;
    private final PolicyStatus to;

    public InvalidPolicyStateException(PolicyStatus from, PolicyStatus to) {
        super(String.format("Invalid policy state transition: %s -> %s", from, to));
        this.from = from;
        this.to = to;
    }

    /** The current status, or null for a policy that does not exist yet. */
    public PolicyStatus from() {
        return from;
    }

    public PolicyStatus to() {
        return to;
    }
}
