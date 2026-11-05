package com.horizon.policyforge.domain.policy;

/**
 * The lifecycle status of a policy (AC-10). ACTIVE, ENDORSED and RENEWED are in force; LAPSED and CANCELLED are
 * terminal. The allowed moves between them are {@link PolicyStateMachine}'s table.
 */
public enum PolicyStatus {
    ACTIVE(true, false),
    ENDORSED(true, false),
    RENEWED(true, false),
    LAPSED(false, true),
    CANCELLED(false, true);

    private final boolean inForce;
    private final boolean terminal;

    PolicyStatus(boolean inForce, boolean terminal) {
        this.inForce = inForce;
        this.terminal = terminal;
    }

    /** Whether the policy currently provides cover and can be endorsed (ACTIVE, ENDORSED or RENEWED). */
    public boolean isInForce() {
        return inForce;
    }

    /** Whether no further transition is possible (LAPSED or CANCELLED). */
    public boolean isTerminal() {
        return terminal;
    }
}
