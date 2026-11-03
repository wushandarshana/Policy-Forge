package com.horizon.policyforge.domain.endorsement;

/** A nominee on a TERM_LIFE policy (PII). {@code sharePercent} is a whole percentage. The string form shows no name. */
public record Nominee(String name, String relationship, int sharePercent) {

    @Override
    public String toString() {
        return "Nominee[redacted]";
    }
}
