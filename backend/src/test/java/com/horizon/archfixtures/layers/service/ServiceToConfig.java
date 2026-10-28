package com.horizon.archfixtures.layers.service;

import com.horizon.archfixtures.layers.config.ConfigMarker;

/** VIOLATION: service may depend only on repository and domain. */
public class ServiceToConfig {
    private ConfigMarker target;

    public ConfigMarker target() {
        return target;
    }
}
