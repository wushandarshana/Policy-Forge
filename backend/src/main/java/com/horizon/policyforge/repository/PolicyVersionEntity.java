package com.horizon.policyforge.repository;

import com.horizon.policyforge.domain.endorsement.Address;
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
 * One immutable snapshot of a policy (AC-05.4, NFR-02): version 1 at issuance and one more for every endorsement. It
 * is built once through {@link #of}, immutable to Hibernate, with no setters; a change is a new row. The risk inputs
 * keep the full quote inputs, health inputs included, for re-rating, and are read only server-side. The insured
 * address and the nominees are personal data, so the string form shows the policy number and version only (NFR-03).
 */
@Entity
@Immutable
@Table(name = "policy_version")
public class PolicyVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, updatable = false)
    private String policyNumber;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "sum_insured", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "annual_premium", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal annualPremium;

    @Lob
    @Column(name = "risk_inputs_json", nullable = false, updatable = false)
    private String riskInputsJson;

    @Column(name = "address_line1", nullable = false, updatable = false)
    private String addressLine1;

    @Column(name = "address_city", nullable = false, updatable = false)
    private String addressCity;

    @Column(name = "address_postcode", nullable = false, updatable = false)
    private String addressPostcode;

    @Lob
    @Column(name = "nominees_json", nullable = false, updatable = false)
    private String nomineesJson;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date", nullable = false, updatable = false)
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected PolicyVersionEntity() {
    }

    private PolicyVersionEntity(String policyNumber, int versionNo, BigDecimal sumInsured, BigDecimal annualPremium,
            String riskInputsJson, Address address, String nomineesJson, int ruleVersion, LocalDate effectiveDate,
            LocalDate expiryDate, Instant createdAt) {
        this.policyNumber = policyNumber;
        this.versionNo = versionNo;
        this.sumInsured = sumInsured;
        this.annualPremium = annualPremium;
        this.riskInputsJson = riskInputsJson;
        this.addressLine1 = address.line1();
        this.addressCity = address.city();
        this.addressPostcode = address.postcode();
        this.nomineesJson = nomineesJson;
        this.ruleVersion = ruleVersion;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.createdAt = createdAt;
    }

    /** A new snapshot. {@code nomineesJson} is the JSON array of the nominees in force (empty for version 1). */
    public static PolicyVersionEntity of(String policyNumber, int versionNo, BigDecimal sumInsured,
            BigDecimal annualPremium, String riskInputsJson, Address address, String nomineesJson, int ruleVersion,
            LocalDate effectiveDate, LocalDate expiryDate, Instant createdAt) {
        return new PolicyVersionEntity(policyNumber, versionNo, sumInsured, annualPremium, riskInputsJson, address,
                nomineesJson, ruleVersion, effectiveDate, expiryDate, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public BigDecimal getSumInsured() {
        return sumInsured;
    }

    public BigDecimal getAnnualPremium() {
        return annualPremium;
    }

    /** The full risk inputs of the quote. Never part of an API response as stored (NFR-04.2). */
    public String getRiskInputsJson() {
        return riskInputsJson;
    }

    public Address getAddress() {
        return new Address(addressLine1, addressCity, addressPostcode);
    }

    public String getNomineesJson() {
        return nomineesJson;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The policy number and version only (NFR-03). */
    @Override
    public String toString() {
        return "PolicyVersionEntity[policyNumber=" + policyNumber + ", versionNo=" + versionNo + "]";
    }
}
