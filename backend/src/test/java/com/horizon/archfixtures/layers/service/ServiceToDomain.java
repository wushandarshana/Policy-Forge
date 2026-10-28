package com.horizon.archfixtures.layers.service;

import com.horizon.archfixtures.layers.domain.DomainMarker;

/** Allowed: service may depend on domain. */
public class ServiceToDomain {
    private DomainMarker target;

    public DomainMarker target() {
        return target;
    }
}
