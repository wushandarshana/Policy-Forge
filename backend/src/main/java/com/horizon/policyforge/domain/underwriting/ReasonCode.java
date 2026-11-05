package com.horizon.policyforge.domain.underwriting;

/**
 * One reason behind an underwriting decision: a rule code and its description, both taken from the rule set that
 * fired it. {@link #OK} is the single reason of an automatic bind (U-8).
 */
public record ReasonCode(String code, String description) {

    /** The reason of an AUTO_BIND result, when no rule fired. */
    public static final ReasonCode OK = new ReasonCode("UW-OK", "No underwriting rule fired");

    public ReasonCode {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("A reason code needs a code");
        }
        description = description == null ? "" : description;
    }
}
