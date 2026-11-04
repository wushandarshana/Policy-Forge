package com.horizon.policyforge.domain.cancellation;

import java.util.Optional;

/**
 * Why a customer cancels (plan G-4): a closed set of four. The chosen reason is the reason of the {@code -> CANCELLED}
 * transition. A name matches exactly: case, spaces and any other text are refused.
 */
public enum CancellationReason {
    NO_LONGER_NEEDED,
    REPLACED_BY_OTHER_COVER,
    AFFORDABILITY,
    OTHER;

    /** The reason with exactly this name, or empty when there is none (including for null). */
    public static Optional<CancellationReason> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        for (CancellationReason reason : values()) {
            if (reason.name().equals(text)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
