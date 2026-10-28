package com.horizon.archfixtures.appendonly.delete.repository;

import org.springframework.data.repository.Repository;

/** VIOLATION: a remove method. */
public interface AuditEntryRepository extends Repository<Object, Long> {
    void removeById(Long id);
}
