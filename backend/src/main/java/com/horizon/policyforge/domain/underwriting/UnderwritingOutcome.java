package com.horizon.policyforge.domain.underwriting;

/** The result of underwriting a risk. Precedence is {@code DECLINE}, then {@code MANUAL_REVIEW}, then {@code AUTO_BIND}. */
public enum UnderwritingOutcome {
    AUTO_BIND,
    MANUAL_REVIEW,
    DECLINE
}
