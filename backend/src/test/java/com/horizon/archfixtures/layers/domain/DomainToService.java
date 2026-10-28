package com.horizon.archfixtures.layers.domain;

import com.horizon.archfixtures.layers.service.ServiceMarker;

/** VIOLATION: domain must not depend on service. */
public class DomainToService {
    private ServiceMarker target;

    public ServiceMarker target() {
        return target;
    }
}
