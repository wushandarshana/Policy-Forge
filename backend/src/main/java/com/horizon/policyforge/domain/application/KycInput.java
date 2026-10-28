package com.horizon.policyforge.domain.application;

/** The KYC details as submitted: raw text, so the validator can report every format error at once. */
public record KycInput(String fullName, String dateOfBirth, String aadhaar, String pan, KycAddress address) {

    /** The string form shows no name, date of birth, Aadhaar or PAN (P-7, NFR-03). */
    @Override
    public String toString() {
        return "KycInput[redacted]";
    }
}
