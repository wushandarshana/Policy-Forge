package com.horizon.policyforge.domain.policy;

import static com.horizon.policyforge.domain.policy.PolicyStatus.ACTIVE;
import static com.horizon.policyforge.domain.policy.PolicyStatus.CANCELLED;
import static com.horizon.policyforge.domain.policy.PolicyStatus.ENDORSED;
import static com.horizon.policyforge.domain.policy.PolicyStatus.LAPSED;
import static com.horizon.policyforge.domain.policy.PolicyStatus.RENEWED;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The policy lifecycle (AC-10): a literal translation of the table in {@code specs/policy-issuance_spec.md}. A null
 * {@code from} means a new policy. If the spec table changes, change the spec first and regenerate this class; never
 * edit {@link #ALLOWED} to differ from it.
 *
 * <pre>
 * From \ To   ACTIVE ENDORSED RENEWED LAPSED CANCELLED
 * (new)         yes    no       no      no     no
 * ACTIVE        no     yes      yes     yes    yes
 * ENDORSED      no     yes      yes     yes    yes
 * RENEWED       no     yes      yes     yes    yes
 * LAPSED        no     no       no      no     no
 * CANCELLED     no     no       no      no     no
 * </pre>
 */
public final class PolicyStateMachine {

    /** The targets a new policy may take. */
    private static final Set<PolicyStatus> INITIAL_ALLOWED = EnumSet.of(ACTIVE);

    private static final Map<PolicyStatus, Set<PolicyStatus>> ALLOWED = new EnumMap<>(PolicyStatus.class);

    static {
        ALLOWED.put(ACTIVE, EnumSet.of(ENDORSED, RENEWED, LAPSED, CANCELLED));
        ALLOWED.put(ENDORSED, EnumSet.of(ENDORSED, RENEWED, LAPSED, CANCELLED));
        ALLOWED.put(RENEWED, EnumSet.of(ENDORSED, RENEWED, LAPSED, CANCELLED));
        ALLOWED.put(LAPSED, EnumSet.noneOf(PolicyStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(PolicyStatus.class));
    }

    /**
     * Returns {@code to} when the move is allowed.
     *
     * @param from the current status, or null for a policy that does not exist yet
     * @throws InvalidPolicyStateException when the table forbids the move
     */
    public PolicyStatus transition(PolicyStatus from, PolicyStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidPolicyStateException(from, to);
        }
        return to;
    }

    /** Whether the table allows {@code from} to {@code to}; a null {@code from} is a new policy. */
    public boolean canTransition(PolicyStatus from, PolicyStatus to) {
        Objects.requireNonNull(to, "to");
        Set<PolicyStatus> targets = from == null ? INITIAL_ALLOWED : ALLOWED.get(from);
        return targets.contains(to);
    }
}
