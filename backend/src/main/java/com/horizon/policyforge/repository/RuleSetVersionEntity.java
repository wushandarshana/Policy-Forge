package com.horizon.policyforge.repository;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
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
 * One stored, published rule-set version (AC-02.6, NFR-05). Append-only: it is built once through
 * {@link #publish}, Hibernate treats it as {@link Immutable}, and it has no setters.
 */
@Entity
@Immutable
@Table(name = "rule_set_version")
public class RuleSetVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "rating_model", nullable = false, updatable = false)
    private String ratingModel;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "sum_insured_min", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal sumInsuredMin;

    @Column(name = "sum_insured_max", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal sumInsuredMax;

    @Column(name = "cancellation_fee", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal cancellationFee;

    @Lob
    @Column(name = "rules_json", nullable = false, updatable = false)
    private String rulesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    /** For JPA only. */
    protected RuleSetVersionEntity() {
    }

    private RuleSetVersionEntity(RuleSetVersion ruleSet, String rulesJson, Instant createdAt, String createdBy) {
        this.productCode = ruleSet.productCode();
        this.version = ruleSet.version();
        this.productName = ruleSet.productName();
        this.ratingModel = ruleSet.ratingModel();
        this.effectiveFrom = ruleSet.effectiveFrom();
        this.sumInsuredMin = ruleSet.sumInsured().min();
        this.sumInsuredMax = ruleSet.sumInsured().max();
        this.cancellationFee = ruleSet.lifecycle().cancellationFee();
        this.rulesJson = rulesJson;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    /** Builds the row for a validated rule set. {@code rulesJson} is stored exactly as given. */
    public static RuleSetVersionEntity publish(RuleSetVersion ruleSet, String rulesJson, Instant createdAt,
            String createdBy) {
        return new RuleSetVersionEntity(ruleSet, rulesJson, createdAt, createdBy);
    }

    public Long getId() {
        return id;
    }

    public String getProductCode() {
        return productCode;
    }

    public int getVersion() {
        return version;
    }

    public String getProductName() {
        return productName;
    }

    public String getRatingModel() {
        return ratingModel;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public BigDecimal getSumInsuredMin() {
        return sumInsuredMin;
    }

    public BigDecimal getSumInsuredMax() {
        return sumInsuredMax;
    }

    public BigDecimal getCancellationFee() {
        return cancellationFee;
    }

    public String getRulesJson() {
        return rulesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
