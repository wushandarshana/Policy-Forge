package com.horizon.policyforge.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Access to applications. They are a mutable aggregate (only the status changes, through the entity), but they are
 * never deleted: {@code save} and finders only, no delete, no update query (NFR-02).
 */
public interface ApplicationRepository extends Repository<ApplicationEntity, Long> {

    /** Inserts and flushes, so a duplicate quote surfaces here as a constraint violation (U-3). */
    ApplicationEntity saveAndFlush(ApplicationEntity entity);

    Optional<ApplicationEntity> findByApplicationId(String applicationId);

    boolean existsByQuoteId(String quoteId);

    boolean existsByCustomerIdAndKycAadhaar(String customerId, String kycAadhaar);

    /** The customer's applications, newest first. The ID breaks ties between applications at the same instant. */
    List<ApplicationEntity> findByCustomerIdOrderBySubmittedAtDescIdDesc(String customerId);
}
