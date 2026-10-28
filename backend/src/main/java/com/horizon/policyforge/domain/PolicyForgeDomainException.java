package com.horizon.policyforge.domain;

/** Base of every exception raised by a domain rule. */
public class PolicyForgeDomainException extends RuntimeException {

    public PolicyForgeDomainException(String message) {
        super(message);
    }
}
