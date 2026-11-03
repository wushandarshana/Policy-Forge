package com.horizon.policyforge.repository;

import com.horizon.policyforge.domain.policy.PolicyStateMachine;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * One policy (AC-05, AC-10). A mutable aggregate: built once through {@link #issue}, and afterwards only its status and
 * its current version number can change, through {@link #applyTransition}, which applies the domain
 * {@link PolicyStateMachine} and refuses any other move. There are no setters. The {@code version} column is an
 * optimistic lock, so of two concurrent changes to one policy the second one fails.
 *
 * <p>The policy's status always equals the latest {@code PolicyStatusTransition}'s target: the service appends the
 * transition row in the same transaction as every {@code applyTransition}. The string form shows the number only.
 */
@Entity
@Table(name = "policy")
public class PolicyEntity {

    private static final PolicyStateMachine STATE_MACHINE = new PolicyStateMachine();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "policy_number", nullable = false, updatable = false)
    private String policyNumber;

    @Column(name = "application_id", nullable = false, updatable = false)
    private String applicationId;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    /** The {@link PolicyStatus} name. Kept as text so the column maps without any enum type handling. */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    /** For JPA only. */
    protected PolicyEntity() {
    }

    private PolicyEntity(String policyNumber, String applicationId, String productCode, String customerId,
            Instant issuedAt) {
        this.policyNumber = policyNumber;
        this.applicationId = applicationId;
        this.productCode = productCode;
        this.customerId = customerId;
        // A new policy: the state machine has exactly one way in, (new) to ACTIVE.
        this.status = STATE_MACHINE.transition(null, PolicyStatus.ACTIVE).name();
        this.currentVersionNo = 1;
        this.issuedAt = issuedAt;
    }

    /** A new policy in status ACTIVE at version 1. */
    public static PolicyEntity issue(String policyNumber, String applicationId, String productCode, String customerId,
            Instant issuedAt) {
        return new PolicyEntity(policyNumber, applicationId, productCode, customerId, issuedAt);
    }

    /**
     * The only way the status and the current version number change: the move must be one the domain state machine
     * allows, otherwise {@code InvalidPolicyStateException} is raised and nothing changes.
     *
     * @param target    the new status
     * @param versionNo the version number that is current after the move (never lower than today's)
     */
    public void applyTransition(PolicyStatus target, int versionNo) {
        if (versionNo < currentVersionNo) {
            throw new IllegalArgumentException("The current version number cannot go back");
        }
        this.status = STATE_MACHINE.transition(getStatus(), target).name();
        this.currentVersionNo = versionNo;
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getCustomerId() {
        return customerId;
    }

    public PolicyStatus getStatus() {
        return PolicyStatus.valueOf(status);
    }

    public int getCurrentVersionNo() {
        return currentVersionNo;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    /** The policy number only (NFR-03). */
    @Override
    public String toString() {
        return "PolicyEntity[policyNumber=" + policyNumber + "]";
    }
}
