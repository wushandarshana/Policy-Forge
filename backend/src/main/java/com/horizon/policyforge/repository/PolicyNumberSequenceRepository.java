package com.horizon.policyforge.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/** Access to the policy number counters: save and finders only. */
public interface PolicyNumberSequenceRepository extends Repository<PolicyNumberSequenceEntity, Long> {

    PolicyNumberSequenceEntity saveAndFlush(PolicyNumberSequenceEntity entity);

    /**
     * The counter of the product and year, locked for writing until the caller's transaction ends, so concurrent issues
     * take their numbers one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PolicyNumberSequenceEntity> findByProductCodeAndYear(String productCode, int year);
}
