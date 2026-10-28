package com.horizon.archfixtures.appendonly.notrepo.repository;


/** VIOLATION: does not extend Repository. */
public interface RuleSetVersionRepository  {
    Object save(Object entity);
}
