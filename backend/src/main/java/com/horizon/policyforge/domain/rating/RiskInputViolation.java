package com.horizon.policyforge.domain.rating;

/**
 * One invalid or missing risk input: the input name and a message that never repeats the submitted value.
 */
public record RiskInputViolation(String field, String message) {
}
