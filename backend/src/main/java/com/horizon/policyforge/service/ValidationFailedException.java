package com.horizon.policyforge.service;

import java.util.List;

/**
 * Thrown by services when input fails validation. The web layer maps it to 400 problem+json with {@code errors[]} of
 * {@code {field, message}} (contract C3), plus the top-level {@code code} when the failure has a business name.
 */
public class ValidationFailedException extends RuntimeException {

    private final String code;
    private final List<FieldViolation> violations;

    public ValidationFailedException(List<FieldViolation> violations) {
        this(null, violations);
    }

    /** A named business error (for example {@code QUOTE_EXPIRED}): the code is returned beside {@code errors[]}. */
    public ValidationFailedException(String code, List<FieldViolation> violations) {
        super("Validation failed for " + violations.size() + " field(s)");
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    /** The named business error, or null for a plain field validation failure. */
    public String code() {
        return code;
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    /** One invalid input: the field name and a message that does not echo the submitted value. */
    public record FieldViolation(String field, String message) {
    }
}
