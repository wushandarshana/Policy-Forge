package com.horizon.archfixtures.purity.domain;

import com.horizon.archfixtures.purity.web.WebThing;

/** VIOLATION: the domain depends on a web class. */
public class DomainUsesWeb {
    private WebThing web;

    public WebThing web() {
        return web;
    }
}
