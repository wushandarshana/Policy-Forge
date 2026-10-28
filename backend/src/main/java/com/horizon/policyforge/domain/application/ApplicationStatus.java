package com.horizon.policyforge.domain.application;

import java.util.Set;

/** Where an application is in its lifecycle (underwriting spec, "Application"). */
public enum ApplicationStatus {
    SUBMITTED,
    UNDERWRITING,
    APPROVED,
    REFERRED,
    DECLINED,
    ISSUED;

    /** The statuses this one may move to, per the spec's transitions. */
    public Set<ApplicationStatus> allowedTargets() {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        throw new UnsupportedOperationException("not implemented");
    }
}
