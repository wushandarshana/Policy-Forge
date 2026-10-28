package com.horizon.archfixtures.appendonly.clean.repository;

import org.springframework.data.repository.Repository;

/** Allowed: save and finders only. */
public interface QuoteRepository extends Repository<Object, Long> {
    Object save(Object entity);
}
