package com.horizon.archfixtures.backdated.service;

/** Fixture: a public backdated path (rejected: it must not be public) and a package-private one (allowed). */
public class BackdatedIssuer {

    public void issueBackdated() {
    }

    void issueBackdatedQuietly() {
    }
}
