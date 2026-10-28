package com.horizon.archfixtures.appendonly.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

/** VIOLATION: a JPA repository exposes delete. */
public interface RuleSetVersionRepository extends JpaRepository<Object, Long> {

}
