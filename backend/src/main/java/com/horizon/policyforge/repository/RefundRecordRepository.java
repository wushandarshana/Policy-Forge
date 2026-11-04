package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Append-only access to refund records (NFR-02, AC-08.4): {@code save} and finders only, no delete, no update. Saving a
 * new refund record inserts at once, so a second record for the same policy fails on the unique key inside the caller's
 * transaction.
 */
public interface RefundRecordRepository extends Repository<RefundRecordEntity, Long> {

    RefundRecordEntity save(RefundRecordEntity entity);

    /** Every refund record of the policy, in the order they were appended (a policy has at most one). */
    List<RefundRecordEntity> findByPolicyNumberOrderByIdAsc(String policyNumber);

    /** The refund records of any of the policies, in no particular order. */
    List<RefundRecordEntity> findByPolicyNumberIn(Collection<String> policyNumbers);
}
