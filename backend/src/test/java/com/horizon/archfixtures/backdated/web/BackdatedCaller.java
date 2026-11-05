package com.horizon.archfixtures.backdated.web;

import com.horizon.archfixtures.backdated.service.BackdatedIssuer;

/** Fixture: a web class that reaches the backdated path (rejected by the caller rule). */
public class BackdatedCaller {

    public void handle() {
        new BackdatedIssuer().issueBackdated();
    }
}
