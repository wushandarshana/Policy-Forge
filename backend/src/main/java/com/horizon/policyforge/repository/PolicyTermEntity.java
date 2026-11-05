package com.horizon.policyforge.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One cover term of a policy (AC-05.1, AC-07): term 1 runs from the effective date to the expiry date and is paid at
 * issuance, and a renewal term is offered unpaid and paid later. The term records the rule version its premium was
 * rated with, fixed at construction (AC-07.3). A mutable aggregate in two intention-revealing respects only:
 * {@link #markPaid} sets the paid flag, one way (a paid term never becomes unpaid, and is never paid twice), and
 * {@link #repriceOffer} re-quotes the premium of an unpaid offer (AC-07.7). There are no setters.
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

    @Column(name = "premium", nullable = false, precision = 19, scale = 2)
    private BigDecimal premium;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    /** For JPA only. */
    protected PolicyTermEntity() {
    }

    private PolicyTermEntity(String policyNumber, int termNo, LocalDate startDate, LocalDate endDate,
            BigDecimal premium, boolean paid, int ruleVersion) {
        this.policyNumber = policyNumber;
        this.termNo = termNo;
        this.startDate = startDate;
        this.endDate = endDate;
        this.premium = premium;
        this.paid = paid;
        this.ruleVersion = ruleVersion;
    }

    /**
     * A new term rated with {@code ruleVersion}. Term 1 is created paid, because the first payment is simulated at
     * issuance; a renewal term is created unpaid.
     */
    public static PolicyTermEntity of(String policyNumber, int termNo, LocalDate startDate, LocalDate endDate,
            BigDecimal premium, boolean paid, int ruleVersion) {
        return new PolicyTermEntity(policyNumber, termNo, startDate, endDate, premium, paid, ruleVersion);
    }

    /**
     * The only change a term allows: its premium has been paid.
     *
     * @throws IllegalStateException when the term is already paid (a paid term is never paid twice)
     */
    public void markPaid() {
        if (paid) {
            throw new IllegalStateException("The term is already paid");
        }
        this.paid = true;
    }

    /**
     * The one change an unpaid renewal offer allows besides being paid: its premium is re-quoted because a
     * CHANGE_SUM_INSURED endorsement changed the rated inputs while the offer was pending (AC-07.7). The dates and the
     * rule version never change.
     *
     * @throws IllegalStateException when the term is paid (a paid term's premium never changes)
     */
    public void repriceOffer(BigDecimal newPremium) {
        Objects.requireNonNull(newPremium, "newPremium");
        if (paid) {
            throw new IllegalStateException("A paid term is never re-priced");
        }
        this.premium = newPremium;
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

    /** The version of the product's rule set this term's premium was rated with. */
    public int getRuleVersion() {
        return ruleVersion;
    }

    @Override
    public String toString() {
        return "PolicyTermEntity[policyNumber=" + policyNumber + ", termNo=" + termNo + "]";
    }
}
