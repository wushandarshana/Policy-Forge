package com.horizon.policyforge.service;

/**
 * Thrown by services when a request conflicts with the current state of a resource, for example a second application
 * for the same quote. The web layer maps it to 409 problem+json with the named {@code code} and a fixed detail
 * (contract C3). The message never contains request input.
 */
public class ConflictException extends RuntimeException {

    /** The quote already has an application (U-3). */
    public static final String QUOTE_ALREADY_APPLIED = "QUOTE_ALREADY_APPLIED";

    /** The application is not in a state that allows the action (U-10). */
    public static final String INVALID_APPLICATION_STATE = "INVALID_APPLICATION_STATE";

    /** Another endorsement of the same policy was written first (AC-06.6): nothing of this request was written. */
    public static final String POLICY_UPDATE_CONFLICT = "POLICY_UPDATE_CONFLICT";

    /** The policy has no unpaid renewal term to pay: no offer yet, or it is already paid (AC-07.5). */
    public static final String NO_PENDING_RENEWAL = "NO_PENDING_RENEWAL";

    /** The renewal can no longer be paid: today is after the current paid term's end plus its grace days (AC-07.5). */
    public static final String RENEWAL_PAYMENT_WINDOW_CLOSED = "RENEWAL_PAYMENT_WINDOW_CLOSED";

    private final String code;

    public ConflictException(String code) {
        super("Conflict: " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
