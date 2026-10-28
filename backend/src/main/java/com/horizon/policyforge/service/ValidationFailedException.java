package com.horizon.policyforge.service;

import java.util.List;

/**
 * Thrown by services when input fails validation. The web layer maps it to 400 problem+json with {@code errors[]} of
 * {@code {field, message}} (contract C3).
 */
public class ValidationFailedException extends RuntimeException {

    private final List<FieldViolation> violations;

    public ValidationFailedException(List<FieldViolation> violations) {
        super("Validation failed for " + violations.size() + " field(s)");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    /** One invalid input: the field name and a message that does not echo the submitted value. */
    public record FieldViolation(String field, String message) {
    }
}
