package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Append-only access to policy versions (NFR-02): {@code save} and finders only, no delete, no update. */
public interface PolicyVersionRepository extends Repository<PolicyVersionEntity, Long> {

    PolicyVersionEntity save(PolicyVersionEntity entity);

    /** Every version of the policy, lowest first. */
    List<PolicyVersionEntity> findByPolicyNumberOrderByVersionNoAsc(String policyNumber);

    /** The versions of any of the policies, in no particular order. */
    List<PolicyVersionEntity> findByPolicyNumberIn(Collection<String> policyNumbers);
}
