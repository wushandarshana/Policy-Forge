package com.horizon.archfixtures.layers.service;

import com.horizon.archfixtures.layers.web.WebMarker;

/** VIOLATION: service must not depend on web. */
public class ServiceToWeb {
    private WebMarker target;

    public WebMarker target() {
        return target;
    }
}
