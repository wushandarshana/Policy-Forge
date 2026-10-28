package com.horizon.policyforge.domain.application;

/** The postal address of the KYC details, as submitted (unvalidated text). */
public record KycAddress(String line1, String city, String postcode) {
}
