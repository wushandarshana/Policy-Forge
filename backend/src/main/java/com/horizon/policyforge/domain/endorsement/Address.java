package com.horizon.policyforge.domain.endorsement;

/** An insured address (PII). The string form shows no address text. */
public record Address(String line1, String city, String postcode) {

    @Override
    public String toString() {
        return "Address[redacted]";
    }
}
