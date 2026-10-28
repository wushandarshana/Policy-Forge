package com.horizon.policyforge.domain.application;

/** One KYC problem: the dotted field path, a machine code and a fixed message that never contains the submitted value. */
public record KycViolation(String field, String code, String message) {
}
