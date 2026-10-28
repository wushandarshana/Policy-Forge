package com.horizon.policyforge.domain.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** KYC stub validation (underwriting spec, "Application", U-4). The date is passed in: the domain has no clock. */
public final class KycValidator {

    public static final String FORMAT_CODE = "KYC_INVALID";
    public static final String AGE_MISMATCH_CODE = "KYC_AGE_MISMATCH";
    public static final String DATE_OF_BIRTH_FIELD = "kyc.dateOfBirth";

    /** Every format violation at once, in field order. */
    public List<KycViolation> validate(KycInput kyc, LocalDate today) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Age in completed years on {@code today}. */
    public static int ageInYears(LocalDate dateOfBirth, LocalDate today) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The age mismatch of U-4, if any: TERM_LIFE against {@code age}, MOTOR against {@code driverAge}, none else. */
    public Optional<KycViolation> checkAge(
            String productCode, Map<String, Object> riskInputs, KycInput kyc, LocalDate today) {
        throw new UnsupportedOperationException("not implemented");
    }
}
