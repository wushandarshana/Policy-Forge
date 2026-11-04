package com.horizon.policyforge.repository;

/**
 * A repository fragment of {@link PolicyRepository}: bumps a loaded policy's optimistic-lock version without changing
 * any of its data, so that a writer which changes no policy column (the renewal cycle's offer) still conflicts with a
 * concurrent change of the same policy (security review MINOR-2). It neither deletes nor updates business data.
 */
public interface PolicyLocking {

    /**
     * Increments the policy's {@code @Version} now, checking the version it was loaded with.
     *
     * @throws org.springframework.dao.ConcurrencyFailureException (translated) when another transaction changed the
     *                                                             policy since it was loaded
     */
    void bumpVersion(PolicyEntity policy);
}
