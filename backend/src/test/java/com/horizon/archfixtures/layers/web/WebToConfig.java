package com.horizon.archfixtures.layers.web;

import com.horizon.archfixtures.layers.config.ConfigMarker;

/** VIOLATION: web may depend only on service and domain. */
public class WebToConfig {
    private ConfigMarker target;

    public ConfigMarker target() {
        return target;
    }
}
