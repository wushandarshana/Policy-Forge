package com.horizon.archfixtures.layers.domain;

import com.horizon.archfixtures.layers.web.WebMarker;

/** VIOLATION: domain must not depend on web. */
public class DomainToWeb {
    private WebMarker target;

    public WebMarker target() {
        return target;
    }
}
