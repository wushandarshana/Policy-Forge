package com.horizon.policyforge.repository;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One status change of a policy (AC-05.4, AC-10.4, NFR-02). Append-only: built once through {@link #of}, immutable to
 * Hibernate, no setters. The policy's status always equals the latest transition's target. A null {@code from} means the
 * move that created the policy (null to ACTIVE).
 */
@Entity
@Immutable
@Table(name = "policy_status_transition")
public class PolicyStatusTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, updatable = false)
    private String policyNumber;

    @Column(name = "from_status", updatable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** For JPA only. */
    protected PolicyStatusTransitionEntity() {
    }

    private PolicyStatusTransitionEntity(String policyNumber, PolicyStatus from, PolicyStatus to, String reason,
            String actorId, Instant occurredAt) {
        this.policyNumber = policyNumber;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = to.name();
        this.reason = reason;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }

    /** A new transition; {@code from} is null for a new policy. */
    public static PolicyStatusTransitionEntity of(String policyNumber, PolicyStatus from, PolicyStatus to,
            String reason, String actorId, Instant occurredAt) {
        return new PolicyStatusTransitionEntity(policyNumber, from, to, reason, actorId, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    /** The status before the move, or null for the move that created the policy. */
    public PolicyStatus getFromStatus() {
        return fromStatus == null ? null : PolicyStatus.valueOf(fromStatus);
    }

    public PolicyStatus getToStatus() {
        return PolicyStatus.valueOf(toStatus);
    }

    public String getReason() {
        return reason;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return "PolicyStatusTransitionEntity[policyNumber=" + policyNumber + ", to=" + toStatus + "]";
    }
}
