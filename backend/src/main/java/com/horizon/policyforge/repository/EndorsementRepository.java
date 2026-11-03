package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Append-only access to endorsements (NFR-02, AC-06.7): {@code save} and finders only, no delete, no update. Saving a
 * new endorsement inserts at once, so a duplicate (policy, sequence) fails inside the caller's transaction.
 */
public interface EndorsementRepository extends Repository<EndorsementEntity, Long> {

    EndorsementEntity save(EndorsementEntity entity);

    /** Every endorsement of the policy, lowest sequence first. */
    List<EndorsementEntity> findByPolicyNumberOrderBySequenceNoAsc(String policyNumber);

    /** The latest endorsement of the policy, if it has any. */
    Optional<EndorsementEntity> findFirstByPolicyNumberOrderBySequenceNoDesc(String policyNumber);

    /** The endorsements of any of the policies, in no particular order. */
    List<EndorsementEntity> findByPolicyNumberIn(Collection<String> policyNumbers);
}
