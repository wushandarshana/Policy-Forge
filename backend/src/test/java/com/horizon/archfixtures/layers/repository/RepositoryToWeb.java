package com.horizon.archfixtures.layers.repository;

import com.horizon.archfixtures.layers.web.WebMarker;

/** VIOLATION: repository must not depend on web. */
public class RepositoryToWeb {
    private WebMarker target;

    public WebMarker target() {
        return target;
    }
}
