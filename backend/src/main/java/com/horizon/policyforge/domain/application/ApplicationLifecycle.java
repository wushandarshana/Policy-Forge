package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.PolicyForgeDomainException;
import com.horizon.policyforge.domain.underwriting.UnderwritingOutcome;

/** The application status transitions. Every move goes through here; anything the spec does not allow is refused. */
public final class ApplicationLifecycle {

    private ApplicationLifecycle() {
    }

    /** SUBMITTED to UNDERWRITING. */
    public static ApplicationStatus startUnderwriting(ApplicationStatus current) {
        return transition(current, ApplicationStatus.UNDERWRITING);
    }

    /** UNDERWRITING to the status the outcome maps to; any other current status is refused. */
    public static ApplicationStatus applyOutcome(ApplicationStatus current, UnderwritingOutcome outcome) {
        if (outcome == null) {
            throw new PolicyForgeDomainException("An underwriting outcome is required");
        }
        ApplicationStatus target = switch (outcome) {
            case AUTO_BIND -> ApplicationStatus.APPROVED;
            case MANUAL_REVIEW -> ApplicationStatus.REFERRED;
            case DECLINE -> ApplicationStatus.DECLINED;
        };
        if (current != ApplicationStatus.UNDERWRITING) {
            throw new InvalidApplicationStateException(current, target);
        }
        return transition(current, target);
    }

    /** APPROVED to ISSUED (issuance has no endpoint this sprint, U-15). */
    public static ApplicationStatus issue(ApplicationStatus current) {
        return transition(current, ApplicationStatus.ISSUED);
    }

    /** Any single transition in the table; the manual and override rules use it after their own checks. */
    public static ApplicationStatus transition(ApplicationStatus current, ApplicationStatus target) {
        if (target == null) {
            throw new PolicyForgeDomainException("A target application status is required");
        }
        if (current == null || !current.canTransitionTo(target)) {
            throw new InvalidApplicationStateException(current, target);
        }
        return target;
    }
}
