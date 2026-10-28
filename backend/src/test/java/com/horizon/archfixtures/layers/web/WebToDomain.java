package com.horizon.archfixtures.layers.web;

import com.horizon.archfixtures.layers.domain.DomainMarker;

/** Allowed: web may depend on domain. */
public class WebToDomain {
    private DomainMarker target;

    public DomainMarker target() {
        return target;
    }
}
