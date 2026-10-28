package com.horizon.archfixtures.appendonly.crud.repository;

import org.springframework.data.repository.CrudRepository;

/** VIOLATION: a CRUD repository exposes delete. */
public interface RuleSetVersionRepository extends CrudRepository<Object, Long> {

}
