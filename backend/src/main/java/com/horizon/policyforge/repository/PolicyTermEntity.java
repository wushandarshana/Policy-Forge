package com.horizon.policyforge.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One cover term of a policy (AC-05.1): term 1 runs from the effective date to the expiry date and is paid at
 * issuance. A mutable aggregate in one respect only: {@link #markPaid} sets the paid flag. There are no setters.
 */
@Entity
@Table(name = "policy_term")
public class PolicyTermEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, updatable = false)
    private String policyNumber;

    @Column(name = "term_no", nullable = false, updatable = false)
    private int termNo;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "premium", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal premium;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    /** For JPA only. */
    protected PolicyTermEntity() {
    }

    private PolicyTermEntity(String policyNumber, int termNo, LocalDate startDate, LocalDate endDate,
            BigDecimal premium, boolean paid) {
        this.policyNumber = policyNumber;
        this.termNo = termNo;
        this.startDate = startDate;
        this.endDate = endDate;
        this.premium = premium;
        this.paid = paid;
    }

    /** A new term. Term 1 is created paid, because the first payment is simulated at issuance. */
    public static PolicyTermEntity of(String policyNumber, int termNo, LocalDate startDate, LocalDate endDate,
            BigDecimal premium, boolean paid) {
        return new PolicyTermEntity(policyNumber, termNo, startDate, endDate, premium, paid);
    }

    /**
     * Red-step stub (AC-07.3): accepts the rule version a term is rated with but does not keep it yet.
     */
    public static PolicyTermEntity of(String policyNumber, int termNo, LocalDate startDate, LocalDate endDate,
            BigDecimal premium, boolean paid, int ruleVersion) {
        return of(policyNumber, termNo, startDate, endDate, premium, paid);
    }

    /** Red-step stub (AC-07.3): not stored yet. */
    public int getRuleVersion() {
        return 0;
    }

    /** The only change a term allows: its premium has been paid. */
    public void markPaid() {
        this.paid = true;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public boolean isPaid() {
        return paid;
    }

    @Override
    public String toString() {
        return "PolicyTermEntity[policyNumber=" + policyNumber + ", termNo=" + termNo + "]";
    }
}
