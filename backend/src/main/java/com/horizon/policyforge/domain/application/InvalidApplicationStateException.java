package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.PolicyForgeDomainException;

/** An application was asked to make a transition its current status does not allow. Maps to 409 at the web edge. */
public class InvalidApplicationStateException extends PolicyForgeDomainException {

    private final ApplicationStatus from;
    private final ApplicationStatus to;

    public InvalidApplicationStateException(ApplicationStatus from, ApplicationStatus to) {
        super("Invalid application transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public ApplicationStatus from() {
        return from;
    }

    public ApplicationStatus to() {
        return to;
    }
}
