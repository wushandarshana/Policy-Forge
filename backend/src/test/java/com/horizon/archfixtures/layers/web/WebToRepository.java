package com.horizon.archfixtures.layers.web;

import com.horizon.archfixtures.layers.repository.RepositoryMarker;

/** VIOLATION: web must not depend on repository. */
public class WebToRepository {
    private RepositoryMarker target;

    public RepositoryMarker target() {
        return target;
    }
}
