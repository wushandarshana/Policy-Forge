package com.horizon.archfixtures.layers.service;

import com.horizon.archfixtures.layers.repository.RepositoryMarker;

/** Allowed: service may depend on repository. */
public class ServiceToRepository {
    private RepositoryMarker target;

    public RepositoryMarker target() {
        return target;
    }
}
