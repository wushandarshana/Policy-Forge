package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Access to applications. They are a mutable aggregate (only the status changes, through the entity), but they are
 * never deleted: {@code save} and finders only, no delete, no update query (NFR-02).
 */
public interface ApplicationRepository extends Repository<ApplicationEntity, Long> {

    /**
     * Saves and flushes, so a duplicate quote (U-3) or a lost optimistic-lock race (U-9) surfaces here, inside the
     * caller's transaction, as an exception it can translate.
     */
    ApplicationEntity saveAndFlush(ApplicationEntity entity);

    Optional<ApplicationEntity> findByApplicationId(String applicationId);

    boolean existsByQuoteId(String quoteId);

    boolean existsByCustomerIdAndKycAadhaar(String customerId, String kycAadhaar);

    /** The customer's applications, newest first. The ID breaks ties between applications at the same instant. */
    List<ApplicationEntity> findByCustomerIdOrderBySubmittedAtDescIdDesc(String customerId);

    /** The applications in any of the given statuses (names), oldest first. The ID breaks ties (the underwriter queue). */
    List<ApplicationEntity> findByStatusInOrderBySubmittedAtAscIdAsc(Collection<String> statuses);
}
