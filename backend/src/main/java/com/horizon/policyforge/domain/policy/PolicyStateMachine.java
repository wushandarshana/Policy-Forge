package com.horizon.policyforge.domain.policy;

/** Red stub: allows everything. */
public final class PolicyStateMachine {

    public PolicyStatus transition(PolicyStatus from, PolicyStatus to) {
        return to;
    }

    public boolean canTransition(PolicyStatus from, PolicyStatus to) {
        return true;
    }
}
