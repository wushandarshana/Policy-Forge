package com.horizon.policyforge.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * The {@link PolicyLocking} fragment: a forced version increment through JPA, which runs
 * {@code update policy set version = v + 1 where id = ? and version = v} immediately, so a stale policy fails here
 * inside the caller's transaction. Spring Data translates the failure into a {@code ConcurrencyFailureException}.
 */
class PolicyLockingImpl implements PolicyLocking {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void bumpVersion(PolicyEntity policy) {
        entityManager.lock(policy, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
}
