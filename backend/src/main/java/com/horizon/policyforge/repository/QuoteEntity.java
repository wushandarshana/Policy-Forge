package com.horizon.policyforge.repository;

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
 * One stored quote (AC-01, NFR-02). Append-only: built once through {@link #of}, immutable to Hibernate, no setters.
 * The full risk inputs, health inputs included, stay in {@code riskInputsJson} and are read only server-side.
 */
@Entity
@Immutable
@Table(name = "quote")
public class QuoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_id", nullable = false, updatable = false)
    private String quoteId;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    @Column(name = "sum_insured", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "annual_premium", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal annualPremium;

    @Lob
    @Column(name = "breakdown_json", nullable = false, updatable = false)
    private String breakdownJson;

    @Lob
    @Column(name = "risk_inputs_json", nullable = false, updatable = false)
    private String riskInputsJson;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected QuoteEntity() {
    }

    private QuoteEntity(String quoteId, String productCode, int ruleVersion, BigDecimal sumInsured,
            BigDecimal annualPremium, String breakdownJson, String riskInputsJson, LocalDate validUntil,
            Instant createdAt) {
        this.quoteId = quoteId;
        this.productCode = productCode;
        this.ruleVersion = ruleVersion;
        this.sumInsured = sumInsured;
        this.annualPremium = annualPremium;
        this.breakdownJson = breakdownJson;
        this.riskInputsJson = riskInputsJson;
        this.validUntil = validUntil;
        this.createdAt = createdAt;
    }

    public static QuoteEntity of(String quoteId, String productCode, int ruleVersion, BigDecimal sumInsured,
            BigDecimal annualPremium, String breakdownJson, String riskInputsJson, LocalDate validUntil,
            Instant createdAt) {
        return new QuoteEntity(quoteId, productCode, ruleVersion, sumInsured, annualPremium, breakdownJson,
                riskInputsJson, validUntil, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public String getProductCode() {
        return productCode;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public BigDecimal getSumInsured() {
        return sumInsured;
    }

    public BigDecimal getAnnualPremium() {
        return annualPremium;
    }

    public String getBreakdownJson() {
        return breakdownJson;
    }

    public String getRiskInputsJson() {
        return riskInputsJson;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
