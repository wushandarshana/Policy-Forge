package com.horizon.archfixtures.purity.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** VIOLATION: a logging library (not java.*) in the domain. */
public class DomainUsesSlf4j {
    private static final Logger LOG = LoggerFactory.getLogger(DomainUsesSlf4j.class);

    public Logger log() {
        return LOG;
    }
}
