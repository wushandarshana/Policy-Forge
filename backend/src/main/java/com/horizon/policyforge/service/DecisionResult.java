package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.application.ApplicationStatus;

/** What a manual decision or an override changed (contract C5): the application and its status before and after. */
public record DecisionResult(String applicationId, ApplicationStatus previousStatus, ApplicationStatus status) {
}
