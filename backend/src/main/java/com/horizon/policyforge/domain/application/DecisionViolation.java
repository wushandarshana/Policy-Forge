package com.horizon.policyforge.domain.application;

/** One problem with a manual decision or override request: the field name and a fixed message. */
public record DecisionViolation(String field, String message) {
}
