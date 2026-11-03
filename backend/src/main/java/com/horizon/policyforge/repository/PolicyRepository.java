package com.horizon.policyforge.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Access to policies. They are a mutable aggregate (only the status and the current version number change, through the
 * entity), but they are never deleted: {@code save} and finders only, no delete, no update query (NFR-02).
 */
public interface PolicyRepository extends Repository<PolicyEntity, Long> {

    /**
     * Saves and flushes, so a duplicate application (one policy per application) or a lost optimistic-lock race
     * surfaces here, inside the caller's transaction, as an exception it can translate.
     */
    PolicyEntity saveAndFlush(PolicyEntity entity);

    Optional<PolicyEntity> findByPolicyNumber(String policyNumber);

    /** The customer's policies, newest first. The ID breaks ties between policies issued at the same instant. */
    List<PolicyEntity> findByCustomerIdOrderByIssuedAtDescIdDesc(String customerId);
}
