package com.horizon.policyforge.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Append-only access to stored rule sets (AC-02.6, NFR-05): it extends {@link Repository}, not a CRUD repository, and
 * declares only {@code save} and finders. There is no delete and no update query.
 */
public interface RuleSetVersionRepository extends Repository<RuleSetVersionEntity, Long> {

    RuleSetVersionEntity save(RuleSetVersionEntity entity);

    boolean existsByProductCodeAndVersion(String productCode, int version);

    Optional<RuleSetVersionEntity> findByProductCodeAndVersion(String productCode, int version);

    List<RuleSetVersionEntity> findByProductCodeOrderByVersionAsc(String productCode);

    Optional<RuleSetVersionEntity> findFirstByProductCodeOrderByVersionDesc(String productCode);

    /** Every stored version, oldest row first, so products list in the order they were first stored. */
    List<RuleSetVersionEntity> findAllByOrderByIdAsc();
}
