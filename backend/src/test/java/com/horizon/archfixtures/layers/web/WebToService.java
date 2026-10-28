package com.horizon.archfixtures.layers.web;

import com.horizon.archfixtures.layers.service.ServiceMarker;

/** Allowed: web may depend on service. */
public class WebToService {
    private ServiceMarker target;

    public ServiceMarker target() {
        return target;
    }
}
