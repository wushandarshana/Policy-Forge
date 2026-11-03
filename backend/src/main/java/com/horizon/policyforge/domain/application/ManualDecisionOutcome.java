package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.underwriting.ReasonCode;

/** What an accepted manual decision or override changes: the new status, the reason and the trimmed comment. */
public record ManualDecisionOutcome(ApplicationStatus newStatus, ReasonCode reason, String comment) {
}
