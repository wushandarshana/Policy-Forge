package com.horizon.archfixtures.layers.config;

import com.horizon.archfixtures.layers.repository.RepositoryMarker;

/** Allowed: config may depend on anything. */
public class ConfigToRepository {
    private RepositoryMarker target;

    public RepositoryMarker target() {
        return target;
    }
}
