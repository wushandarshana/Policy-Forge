package com.horizon.archfixtures.appendonly.clean.repository;

import org.springframework.data.repository.Repository;

/** Allowed: save and finders only. */
public interface RuleSetVersionRepository extends Repository<Object, Long> {
    Object save(Object entity);

    Object findByProductCode(String productCode);
}
