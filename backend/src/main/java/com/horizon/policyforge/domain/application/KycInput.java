package com.horizon.policyforge.domain.application;

/** The KYC details as submitted: raw text, so the validator can report every format error at once. */
public record KycInput(String fullName, String dateOfBirth, String aadhaar, String pan, KycAddress address) {
}
