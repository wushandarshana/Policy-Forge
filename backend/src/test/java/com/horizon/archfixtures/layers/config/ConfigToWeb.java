package com.horizon.archfixtures.layers.config;

import com.horizon.archfixtures.layers.web.WebMarker;

/** Allowed: config may depend on anything. */
public class ConfigToWeb {
    private WebMarker target;

    public WebMarker target() {
        return target;
    }
}
