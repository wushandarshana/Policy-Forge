package com.horizon.archfixtures.layers.domain;

import com.horizon.archfixtures.layers.repository.RepositoryMarker;

/** VIOLATION: domain must not depend on repository. */
public class DomainToRepository {
    private RepositoryMarker target;

    public RepositoryMarker target() {
        return target;
    }
}
