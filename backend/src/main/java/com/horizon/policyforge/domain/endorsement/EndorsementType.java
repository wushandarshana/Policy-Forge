package com.horizon.policyforge.domain.endorsement;

/** The three endorsement types (AC-06). Only a sum-insured change re-rates the premium. */
public enum EndorsementType {
    CHANGE_ADDRESS(false),
    ADD_NOMINEE(false),
    CHANGE_SUM_INSURED(true);

    private final boolean reRates;

    EndorsementType(boolean reRates) {
        this.reRates = reRates;
    }

    /** Whether this type re-quotes the premium with the policy's own rule version. */
    public boolean reRates() {
        return reRates;
    }
}
