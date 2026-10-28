package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.underwriting.UnderwritingOutcome;

/** The application status transitions. Every move goes through here; anything the spec does not allow is refused. */
public final class ApplicationLifecycle {

    private ApplicationLifecycle() {
    }

    /** SUBMITTED to UNDERWRITING. */
    public static ApplicationStatus startUnderwriting(ApplicationStatus current) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** UNDERWRITING to the status the outcome maps to; any other current status is refused. */
    public static ApplicationStatus applyOutcome(ApplicationStatus current, UnderwritingOutcome outcome) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** APPROVED to ISSUED (issuance has no endpoint this sprint, U-15). */
    public static ApplicationStatus issue(ApplicationStatus current) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Any single transition in the table; the manual and override rules use it after their own checks. */
    public static ApplicationStatus transition(ApplicationStatus current, ApplicationStatus target) {
        throw new UnsupportedOperationException("not implemented");
    }
}
