package com.horizon.archfixtures.appendonly.update.repository;

import org.springframework.data.repository.Repository;

/** VIOLATION: an update method. */
public interface AuditEntryRepository extends Repository<Object, Long> {
    int updateActor(String actor);
}
