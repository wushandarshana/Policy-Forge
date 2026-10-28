package com.horizon.policyforge.domain.application;

/**
 * The rules for manual decisions and overrides (U-10). Input is checked first, then the application's state, so a
 * caller can map the failures in the C3 order: 400, then 409.
 */
public final class ManualDecisionRules {

    public static final int MIN_COMMENT_LENGTH = 10;
    public static final int MAX_COMMENT_LENGTH = 2000;

    private ManualDecisionRules() {
    }

    /** An underwriter decision. Valid only from REFERRED: APPROVE gives APPROVED and DECLINE gives DECLINED. */
    public static ManualDecisionOutcome decide(
            ApplicationStatus current, ManualDecision decision, String reasonCode, String comment) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** An admin override. Valid only from DECLINED, and gives APPROVED. */
    public static ManualDecisionOutcome override(ApplicationStatus current, String reasonCode, String comment) {
        throw new UnsupportedOperationException("not implemented");
    }
}
