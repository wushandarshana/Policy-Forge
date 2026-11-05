package com.horizon.policyforge.domain.policy;

import com.horizon.policyforge.domain.PolicyForgeDomainException;

/** The requested policy effective date is outside the issuance window (AC-05.1). Names the offending field. */
public class InvalidEffectiveDateException extends PolicyForgeDomainException {

    /** The machine code of this failure. */
    public static final String CODE = "EFFECTIVE_DATE_OUT_OF_RANGE";

    /** The request field the failure is about. */
    public static final String FIELD = "effectiveDate";

    public InvalidEffectiveDateException() {
        super("Effective date must be between today and 60 days from today");
    }

    public String field() {
        return FIELD;
    }

    public String code() {
        return CODE;
    }
}
