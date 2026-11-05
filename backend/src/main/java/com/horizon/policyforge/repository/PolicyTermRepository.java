package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Access to policy terms: {@code save} and finders only. A term is never deleted, and only its paid flag changes. */
public interface PolicyTermRepository extends Repository<PolicyTermEntity, Long> {

    PolicyTermEntity save(PolicyTermEntity entity);

    /** Every term of the policy, lowest first. */
    List<PolicyTermEntity> findByPolicyNumberOrderByTermNoAsc(String policyNumber);

    /** The terms of any of the policies, in no particular order. */
    List<PolicyTermEntity> findByPolicyNumberIn(Collection<String> policyNumbers);
}
