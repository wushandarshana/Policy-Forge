package com.horizon.policyforge.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The counter behind the policy numbers of one product in one issue year (AC-05.2). The issuing transaction reads it
 * under a pessimistic lock, calls {@link #allocateNext} and commits it with the policy, so a rolled-back issue gives its
 * number back and the numbers stay contiguous. It is a counter, not a business fact, so it is not append-only.
 */
@Entity
@Table(name = "policy_number_sequence")
public class PolicyNumberSequenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Column(name = "seq_year", nullable = false, updatable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private int lastValue;

    /** For JPA only. */
    protected PolicyNumberSequenceEntity() {
    }

    private PolicyNumberSequenceEntity(String productCode, int year) {
        this.productCode = productCode;
        this.year = year;
        this.lastValue = 0;
    }

    /** A counter that has issued nothing yet: the first number it allocates is 1. */
    public static PolicyNumberSequenceEntity startAt(String productCode, int year) {
        return new PolicyNumberSequenceEntity(productCode, year);
    }

    /** The next sequence number of this product and year. */
    public int allocateNext() {
        lastValue++;
        return lastValue;
    }

    public String getProductCode() {
        return productCode;
    }

    public int getYear() {
        return year;
    }

    public int getLastValue() {
        return lastValue;
    }
}
