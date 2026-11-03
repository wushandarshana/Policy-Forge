package com.horizon.policyforge.domain.application;

/** The postal address of the KYC details, as submitted (unvalidated text). */
public record KycAddress(String line1, String city, String postcode) {

    /** The string form shows no address text (P-7). */
    @Override
    public String toString() {
        return "KycAddress[redacted]";
    }
}
