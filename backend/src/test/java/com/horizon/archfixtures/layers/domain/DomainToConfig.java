package com.horizon.archfixtures.layers.domain;

import com.horizon.archfixtures.layers.config.ConfigMarker;

/** VIOLATION: domain must not depend on config. */
public class DomainToConfig {
    private ConfigMarker target;

    public ConfigMarker target() {
        return target;
    }
}
