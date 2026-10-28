package com.horizon.policyforge.service;

/**
 * Thrown when a product has no rule-set version in force on the requested day: it is unknown, or every version has an
 * {@code effectiveFrom} in the future. Maps to 404 through {@link ResourceNotFoundException}.
 */
public class NoActiveRuleSetException extends ResourceNotFoundException {

    public NoActiveRuleSetException(String message) {
        super(message);
    }
}
