package com.horizon.policyforge.domain.endorsement;

import java.time.LocalDate;
import java.util.List;

/** Red stub: accepts everything. */
public final class EndorsementValidator {

    public EndorsementValidation validate(PolicySnapshot policy, EndorsementRequest request, LocalDate today) {
        return new EndorsementValidation(null, List.of());
    }
}
