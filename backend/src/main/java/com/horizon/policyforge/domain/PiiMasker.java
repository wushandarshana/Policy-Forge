package com.horizon.policyforge.domain;

/**
 * Masks personal identifiers for display to someone who is not the owner (U-7): {@code *} repeated (length minus 4)
 * times, then the last 4 characters. A value of 4 characters or fewer is masked completely, so a short value is never
 * revealed. {@code XXXX-XXXX-1234} gives {@code **********1234} and {@code ABCDE1234F} gives {@code ******234F}.
 */
public final class PiiMasker {

    private static final int VISIBLE = 4;

    private PiiMasker() {
    }

    /** The masked value, or null when there is no value (nothing is invented). */
    public static String mask(String value) {
        if (value == null) {
            return null;
        }
        int length = value.length();
        if (length <= VISIBLE) {
            return "*".repeat(length);
        }
        return "*".repeat(length - VISIBLE) + value.substring(length - VISIBLE);
    }
}
