package com.horizon.policyforge.service;

/**
 * The outcome of a successful endorsement (C4): the new endorsement and the policy header after it. The endorsement's
 * string form hides its changes (an address or a nominee).
 */
public record EndorsementResult(PolicyDetail.EndorsementView endorsement, PolicyHeader policy) {

    @Override
    public String toString() {
        return "EndorsementResult[policyNumber=" + policy.policyNumber() + ", sequenceNo="
                + endorsement.sequenceNo() + "]";
    }
}
