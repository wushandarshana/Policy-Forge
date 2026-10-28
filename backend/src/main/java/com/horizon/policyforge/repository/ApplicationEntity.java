package com.horizon.policyforge.repository;

import com.horizon.policyforge.domain.application.ApplicationLifecycle;
import com.horizon.policyforge.domain.application.ApplicationStatus;
import com.horizon.policyforge.domain.application.KycInput;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One application (AC-03). A mutable aggregate: built once through {@link #submit}, and afterwards only its status can
 * change, through {@link #applyTransition}, which applies the domain transition table and refuses any other move. There
 * are no setters. The {@code version} column is an optimistic lock, so of two concurrent decisions on one application the
 * second one fails (U-9).
 *
 * <p>The KYC details are stored here and never logged: the string form of this entity shows the application ID only
 * (NFR-03).
 */
@Entity
@Table(name = "application")
public class ApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "application_id", nullable = false, updatable = false)
    private String applicationId;

    @Column(name = "quote_id", nullable = false, updatable = false)
    private String quoteId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    /** The {@link ApplicationStatus} name. Kept as text so the column maps without any enum type handling. */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "kyc_full_name", nullable = false, updatable = false)
    private String kycFullName;

    @Column(name = "kyc_date_of_birth", nullable = false, updatable = false)
    private LocalDate kycDateOfBirth;

    @Column(name = "kyc_aadhaar", nullable = false, updatable = false)
    private String kycAadhaar;

    @Column(name = "kyc_pan", nullable = false, updatable = false)
    private String kycPan;

    @Column(name = "kyc_address_line1", nullable = false, updatable = false)
    private String kycAddressLine1;

    @Column(name = "kyc_address_city", nullable = false, updatable = false)
    private String kycAddressCity;

    @Column(name = "kyc_address_postcode", nullable = false, updatable = false)
    private String kycAddressPostcode;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    /** For JPA only. */
    protected ApplicationEntity() {
    }

    private ApplicationEntity(String applicationId, String quoteId, String customerId, String productCode,
            KycInput kyc, Instant submittedAt) {
        this.applicationId = applicationId;
        this.quoteId = quoteId;
        this.customerId = customerId;
        this.productCode = productCode;
        this.status = ApplicationStatus.SUBMITTED.name();
        this.kycFullName = kyc.fullName();
        this.kycDateOfBirth = LocalDate.parse(kyc.dateOfBirth());
        this.kycAadhaar = kyc.aadhaar();
        this.kycPan = kyc.pan();
        this.kycAddressLine1 = kyc.address().line1();
        this.kycAddressCity = kyc.address().city();
        this.kycAddressPostcode = kyc.address().postcode();
        this.submittedAt = submittedAt;
    }

    /** A new application in status SUBMITTED. The KYC has already passed the domain validator. */
    public static ApplicationEntity submit(String applicationId, String quoteId, String customerId,
            String productCode, KycInput kyc, Instant submittedAt) {
        return new ApplicationEntity(applicationId, quoteId, customerId, productCode, kyc, submittedAt);
    }

    /**
     * The only way the status changes: the move must be one the domain transition table allows, otherwise
     * {@code InvalidApplicationStateException} is raised and the status is unchanged.
     */
    public void applyTransition(ApplicationStatus target) {
        this.status = ApplicationLifecycle.transition(getStatus(), target).name();
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public ApplicationStatus getStatus() {
        return ApplicationStatus.valueOf(status);
    }

    /** The submitted full name. Only the underwriter queue shows it (U-7). */
    public String getKycFullName() {
        return kycFullName;
    }

    /** The submitted Aadhaar. Only ever shown masked (U-7). */
    public String getKycAadhaar() {
        return kycAadhaar;
    }

    /** The submitted PAN. Only ever shown masked (U-7). */
    public String getKycPan() {
        return kycPan;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    /** The application ID only, never the KYC details (NFR-03). */
    @Override
    public String toString() {
        return "ApplicationEntity[applicationId=" + applicationId + "]";
    }
}
