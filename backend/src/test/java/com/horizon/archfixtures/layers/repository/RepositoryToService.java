package com.horizon.archfixtures.layers.repository;

import com.horizon.archfixtures.layers.service.ServiceMarker;

/** VIOLATION: repository must not depend on service. */
public class RepositoryToService {
    private ServiceMarker target;

    public ServiceMarker target() {
        return target;
    }
}
