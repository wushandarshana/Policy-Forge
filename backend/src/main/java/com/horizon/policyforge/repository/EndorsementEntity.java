package com.horizon.policyforge.repository;

import com.horizon.policyforge.domain.endorsement.EndorsementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/**
 * One immutable endorsement of a policy (AC-06, AC-06.7, NFR-02): built once through {@link #of}, immutable to
 * Hibernate, with no setters, and its repository has no delete. The payload JSON is the normalised request payload (the
 * address or the nominee, or the replaced sum-insured components) and is personal data, so the string form shows the
 * policy number and sequence only (NFR-03).
 */
@Entity
@Immutable
@Table(name = "endorsement")
public class EndorsementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, updatable = false)
    private String policyNumber;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    /** The {@link EndorsementType} name. Kept as text so the column maps without any enum type handling. */
    @Column(name = "type", nullable = false, updatable = false)
    private String type;

    @Lob
    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;

    @Column(name = "before_version_no", nullable = false, updatable = false)
    private int beforeVersionNo;

    @Column(name = "after_version_no", nullable = false, updatable = false)
    private int afterVersionNo;

    @Column(name = "new_annual_premium", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal newAnnualPremium;

    @Column(name = "adjustment", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal adjustment;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected EndorsementEntity() {
    }

    private EndorsementEntity(String policyNumber, int sequenceNo, EndorsementType type, String payloadJson,
            int beforeVersionNo, int afterVersionNo, BigDecimal newAnnualPremium, BigDecimal adjustment,
            LocalDate effectiveDate, String actorId, Instant createdAt) {
        this.policyNumber = policyNumber;
        this.sequenceNo = sequenceNo;
        this.type = type.name();
        this.payloadJson = payloadJson;
        this.beforeVersionNo = beforeVersionNo;
        this.afterVersionNo = afterVersionNo;
        this.newAnnualPremium = newAnnualPremium;
        this.adjustment = adjustment;
        this.effectiveDate = effectiveDate;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    /** A new endorsement. {@code payloadJson} is the normalised payload; money is scale 2 (NFR-01). */
    public static EndorsementEntity of(String policyNumber, int sequenceNo, EndorsementType type, String payloadJson,
            int beforeVersionNo, int afterVersionNo, BigDecimal newAnnualPremium, BigDecimal adjustment,
            LocalDate effectiveDate, String actorId, Instant createdAt) {
        return new EndorsementEntity(policyNumber, sequenceNo, type, payloadJson, beforeVersionNo, afterVersionNo,
                newAnnualPremium, adjustment, effectiveDate, actorId, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public EndorsementType getType() {
        return EndorsementType.valueOf(type);
    }

    /** The normalised payload. Never part of an API response as stored: the views are built from an allow-list. */
    public String getPayloadJson() {
        return payloadJson;
    }

    public int getBeforeVersionNo() {
        return beforeVersionNo;
    }

    public int getAfterVersionNo() {
        return afterVersionNo;
    }

    public BigDecimal getNewAnnualPremium() {
        return newAnnualPremium;
    }

    public BigDecimal getAdjustment() {
        return adjustment;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The policy number and sequence only (NFR-03). */
    @Override
    public String toString() {
        return "EndorsementEntity[policyNumber=" + policyNumber + ", sequenceNo=" + sequenceNo + "]";
    }
}
