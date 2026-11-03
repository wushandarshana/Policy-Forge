package com.horizon.policyforge.domain.policy;

import com.horizon.policyforge.domain.PolicyForgeDomainException;

/** A policy number was requested for an unknown product, a non 4-digit year or a sequence outside 1 to 999999. */
public class InvalidPolicyNumberException extends PolicyForgeDomainException {

    public InvalidPolicyNumberException(String message) {
        super(message);
    }
}
