package com.horizon.policyforge.domain.ruleset;

/**
 * One problem found in a rule set: where it is ({@code path}, e.g. {@code sumInsured.min} or
 * {@code underwriting.rules[1].code}) and what is wrong. The message never repeats a submitted value.
 */
public record RuleSetViolation(String path, String message) {
}
