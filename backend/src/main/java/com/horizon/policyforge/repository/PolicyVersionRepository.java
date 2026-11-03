package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Append-only access to policy versions (NFR-02): {@code save} and finders only, no delete, no update. */
public interface PolicyVersionRepository extends Repository<PolicyVersionEntity, Long> {

    PolicyVersionEntity save(PolicyVersionEntity entity);

    /** Every version of the policy, lowest first. */
    List<PolicyVersionEntity> findByPolicyNumberOrderByVersionNoAsc(String policyNumber);

    /** One version of the policy, for example its current one. */
    Optional<PolicyVersionEntity> findByPolicyNumberAndVersionNo(String policyNumber, int versionNo);

    /** The versions of any of the policies, in no particular order. */
    List<PolicyVersionEntity> findByPolicyNumberIn(Collection<String> policyNumbers);
}
