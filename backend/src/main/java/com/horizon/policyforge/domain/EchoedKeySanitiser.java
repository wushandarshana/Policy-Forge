package com.horizon.policyforge.domain;

/** Bounds a caller-supplied unknown key before it is echoed in an error response (P-14). */
public final class EchoedKeySanitiser {

    /** The most characters an echoed key may have. */
    public static final int MAX_LENGTH = 64;

    private EchoedKeySanitiser() {
    }

    /**
     * At most {@value #MAX_LENGTH} characters; every character outside {@code [A-Za-z0-9_.-]} becomes {@code _}. A
     * null key gives an empty string.
     */
    public static String sanitise(String key) {
        if (key == null) {
            return "";
        }
        int length = Math.min(key.length(), MAX_LENGTH);
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = key.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-';
            out.append(allowed ? c : '_');
        }
        return out.toString();
    }
}
