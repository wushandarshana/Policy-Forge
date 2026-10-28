package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.underwriting.ReasonCode;
import java.util.List;
import java.util.Optional;

/** The manual and override reason codes with their descriptions, and which decision each is offered for (U-10). */
public final class ManualReasonCatalogue {

    private ManualReasonCatalogue() {
    }

    /** The codes a decision may carry: APPROVE offers two, DECLINE one. */
    public static List<ReasonCode> forDecision(ManualDecision decision) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The codes valid only on the override endpoint. */
    public static List<ReasonCode> overrideCodes() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Every code in the catalogue. */
    public static List<ReasonCode> all() {
        throw new UnsupportedOperationException("not implemented");
    }

    public static Optional<ReasonCode> find(String code) {
        throw new UnsupportedOperationException("not implemented");
    }
}
