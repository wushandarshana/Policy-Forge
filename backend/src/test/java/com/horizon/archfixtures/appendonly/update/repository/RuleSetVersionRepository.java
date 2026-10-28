package com.horizon.archfixtures.appendonly.update.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.Repository;

/** VIOLATION: a @Modifying query. */
public interface RuleSetVersionRepository extends Repository<Object, Long> {
    @Modifying
    int overwrite(String rulesJson);
}
