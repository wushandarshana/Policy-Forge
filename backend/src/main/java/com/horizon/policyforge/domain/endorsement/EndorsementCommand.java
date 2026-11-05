package com.horizon.policyforge.domain.endorsement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * A validated, normalised endorsement: the effective date is resolved, text is trimmed and amounts are scale 2.
 * {@code newSumInsured} holds only the components being replaced, keyed by risk-input name (see
 * {@link PolicySnapshot}); it is empty for the types that do not re-rate. The string form shows no personal data.
 */
public record EndorsementCommand(
        EndorsementType type,
        LocalDate effectiveDate,
        Address address,
        Nominee nominee,
        Map<String, BigDecimal> newSumInsured) {

    public EndorsementCommand {
        newSumInsured = Map.copyOf(newSumInsured);
    }

    @Override
    public String toString() {
        return "EndorsementCommand[type=" + type + "]";
    }
}
