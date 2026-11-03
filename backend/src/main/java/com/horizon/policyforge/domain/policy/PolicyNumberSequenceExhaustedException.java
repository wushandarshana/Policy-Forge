package com.horizon.policyforge.domain.policy;

import com.horizon.policyforge.domain.PolicyForgeDomainException;

/**
 * The 6-digit sequence of a product and issue year has handed out its last number (999999). It is an availability
 * condition, not a caller error: the issue is refused as 503 {@code POLICY_NUMBER_SEQUENCE_EXHAUSTED} at the web edge
 * and nothing is written (AC-05.2).
 */
public class PolicyNumberSequenceExhaustedException extends PolicyForgeDomainException {

    public PolicyNumberSequenceExhaustedException() {
        super("The policy number sequence is exhausted");
    }
}
