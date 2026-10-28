package com.horizon.archfixtures.purity.domain;

import com.fasterxml.jackson.databind.ObjectMapper;

/** VIOLATION: a third-party library (not java.*) in the domain. */
public class DomainUsesJackson {
    private ObjectMapper mapper;

    public ObjectMapper mapper() {
        return mapper;
    }
}
