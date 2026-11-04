package com.horizon.policyforge.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/**
 * The refund of one cancelled policy (AC-08, NFR-02): built once through {@link #of}, immutable to Hibernate, with no
 * setters, and its repository has no delete. The policy number is unique, so a policy has at most one refund record. It
 * holds numbers, dates and the formula string only (the formula shows the numbers of the calculation), no personal data.
 * Money is scale 2 (NFR-01).
 */
@Entity
@Immutable
@Table(name = "refund_record")
public class RefundRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, updatable = false)
    private String policyNumber;

    @Column(name = "term_no", nullable = false, updatable = false)
    private int termNo;

    @Column(name = "cancellation_date", nullable = false, updatable = false)
    private LocalDate cancellationDate;

    /** The premium the refund was calculated on: the term premium plus the endorsement adjustments of that term. */
    @Column(name = "premium", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal premium;

    @Column(name = "term_days", nullable = false, updatable = false)
    private int termDays;

    @Column(name = "unexpired_days", nullable = false, updatable = false)
    private int unexpiredDays;

    @Column(name = "cooling_off", nullable = false, updatable = false)
    private boolean coolingOff;

    @Column(name = "fee", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "formula", nullable = false, updatable = false)
    private String formula;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected RefundRecordEntity() {
    }

    private RefundRecordEntity(String policyNumber, int termNo, LocalDate cancellationDate, BigDecimal premium,
            int termDays, int unexpiredDays, boolean coolingOff, BigDecimal fee, BigDecimal amount, String formula,
            String actorId, Instant createdAt) {
        this.policyNumber = policyNumber;
        this.termNo = termNo;
        this.cancellationDate = cancellationDate;
        this.premium = premium;
        this.termDays = termDays;
        this.unexpiredDays = unexpiredDays;
        this.coolingOff = coolingOff;
        this.fee = fee;
        this.amount = amount;
        this.formula = formula;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    /** A new refund record. Money is scale 2 (NFR-01); the formula holds only the numbers of the calculation. */
    public static RefundRecordEntity of(String policyNumber, int termNo, LocalDate cancellationDate,
            BigDecimal premium, int termDays, int unexpiredDays, boolean coolingOff, BigDecimal fee,
            BigDecimal amount, String formula, String actorId, Instant createdAt) {
        return new RefundRecordEntity(policyNumber, termNo, cancellationDate, premium, termDays, unexpiredDays,
                coolingOff, fee, amount, formula, actorId, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public int getTermNo() {
        return termNo;
    }

    public LocalDate getCancellationDate() {
        return cancellationDate;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public int getTermDays() {
        return termDays;
    }

    public int getUnexpiredDays() {
        return unexpiredDays;
    }

    public boolean isCoolingOff() {
        return coolingOff;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFormula() {
        return formula;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The policy number and term only (NFR-03). */
    @Override
    public String toString() {
        return "RefundRecordEntity[policyNumber=" + policyNumber + ", termNo=" + termNo + "]";
    }
}
