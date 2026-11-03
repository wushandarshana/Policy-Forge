package com.horizon.policyforge.domain.cancellation;

import com.horizon.policyforge.domain.PolicyForgeDomainException;

/** The cancellation date is outside the current paid term (AC-08.1). Names the offending request field. */
public class InvalidCancellationDateException extends PolicyForgeDomainException {

    /** The machine code of this failure. */
    public static final String CODE = "CANCELLATION_DATE_OUT_OF_TERM";

    private final String field;

    public InvalidCancellationDateException(String field) {
        super("Cancellation date must be within the current term");
        this.field = field;
    }

    public String field() {
        return field;
    }

    public String code() {
        return CODE;
    }
}
