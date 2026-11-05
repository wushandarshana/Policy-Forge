package com.horizon.policyforge.repository;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Append-only access to policy status transitions (NFR-02): {@code save} and finders only, no delete, no update. */
public interface PolicyStatusTransitionRepository extends Repository<PolicyStatusTransitionEntity, Long> {

    PolicyStatusTransitionEntity save(PolicyStatusTransitionEntity entity);

    /** Every transition of the policy, in the order they were appended. */
    List<PolicyStatusTransitionEntity> findByPolicyNumberOrderByIdAsc(String policyNumber);
}
