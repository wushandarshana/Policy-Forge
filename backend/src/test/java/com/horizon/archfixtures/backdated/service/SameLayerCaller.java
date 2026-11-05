package com.horizon.archfixtures.backdated.service;

/** Fixture: a service class calling the backdated path from its own package (allowed by the caller rule). */
public class SameLayerCaller {

    void seed() {
        new BackdatedIssuer().issueBackdatedQuietly();
    }
}
