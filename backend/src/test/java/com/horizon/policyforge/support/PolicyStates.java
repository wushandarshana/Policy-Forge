package com.horizon.policyforge.support;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.repository.PolicyStatusTransitionEntity;
import com.horizon.policyforge.repository.PolicyStatusTransitionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test set-up for the states that have no endpoint yet (P-6): moves a policy to RENEWED, LAPSED or CANCELLED through the
 * same path the services use, the policy aggregate's {@code applyTransition} (backed by {@code PolicyStateMachine})
 * plus an appended transition row, in one transaction. It never uses raw SQL.
 */
public final class PolicyStates {

    private final PolicyRepository policies;
    private final PolicyStatusTransitionRepository transitions;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PolicyStates(PolicyRepository policies, PolicyStatusTransitionRepository transitions,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.policies = policies;
        this.transitions = transitions;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Moves the policy to {@code target} and appends the transition row (actor {@code system}). An illegal move raises
     * {@code InvalidPolicyStateException} and nothing is written.
     */
    public void move(String policyNumber, PolicyStatus target, String reason) {
        transaction.executeWithoutResult(status -> {
            PolicyEntity policy = policies.findByPolicyNumber(policyNumber).orElseThrow();
            PolicyStatus from = policy.getStatus();
            policy.applyTransition(target, policy.getCurrentVersionNo());
            policies.saveAndFlush(policy);
            transitions.save(PolicyStatusTransitionEntity.of(policyNumber, from, target, reason, "system",
                    Instant.now(clock)));
        });
    }
}
