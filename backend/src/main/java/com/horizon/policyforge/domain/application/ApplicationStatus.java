package com.horizon.policyforge.domain.application;

import java.util.EnumSet;
import java.util.Set;

/** Where an application is in its lifecycle (underwriting spec, "Application"). */
public enum ApplicationStatus {
    SUBMITTED,
    UNDERWRITING,
    APPROVED,
    REFERRED,
    DECLINED,
    ISSUED;

    /**
     * The statuses this one may move to, per the spec's transitions: SUBMITTED to UNDERWRITING; UNDERWRITING to
     * APPROVED, REFERRED or DECLINED; REFERRED to APPROVED or DECLINED (manual decision); DECLINED to APPROVED
     * (override); APPROVED to ISSUED.
     */
    public Set<ApplicationStatus> allowedTargets() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDERWRITING);
            case UNDERWRITING -> EnumSet.of(APPROVED, REFERRED, DECLINED);
            case REFERRED -> EnumSet.of(APPROVED, DECLINED);
            case DECLINED -> EnumSet.of(APPROVED);
            case APPROVED -> EnumSet.of(ISSUED);
            case ISSUED -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return target != null && allowedTargets().contains(target);
    }
}
