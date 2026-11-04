package com.horizon.policyforge.domain.cancellation;

import java.util.Optional;

/** Why a customer cancels (plan G-4). Red-phase stub: {@link #parse} does not yet recognise any name. */
public enum CancellationReason {
    NO_LONGER_NEEDED,
    REPLACED_BY_OTHER_COVER,
    AFFORDABILITY,
    OTHER;

    /** The reason with exactly this name, or empty when there is none. */
    public static Optional<CancellationReason> parse(String text) {
        return Optional.empty();
    }
}
