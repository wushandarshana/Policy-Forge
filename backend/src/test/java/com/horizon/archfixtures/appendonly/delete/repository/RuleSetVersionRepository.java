package com.horizon.archfixtures.appendonly.delete.repository;

import org.springframework.data.repository.Repository;

/** VIOLATION: a delete method. */
public interface RuleSetVersionRepository extends Repository<Object, Long> {
    void deleteByProductCode(String productCode);
}
