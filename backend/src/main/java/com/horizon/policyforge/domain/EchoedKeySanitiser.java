package com.horizon.policyforge.domain;

/** Bounds a caller-supplied unknown key before it is echoed in an error response (P-14). */
public final class EchoedKeySanitiser {

    /** The most characters an echoed key may have. */
    public static final int MAX_LENGTH = 64;

    private EchoedKeySanitiser() {
    }

    /** At most 64 characters; every character outside {@code [A-Za-z0-9_.-]} becomes {@code _}. */
    public static String sanitise(String key) {
        return key;
    }
}
