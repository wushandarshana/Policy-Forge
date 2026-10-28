package com.horizon.archfixtures.layers.repository;

import com.horizon.archfixtures.layers.domain.DomainMarker;

/** Allowed: repository may depend on domain. */
public class RepositoryToDomain {
    private DomainMarker target;

    public DomainMarker target() {
        return target;
    }
}
