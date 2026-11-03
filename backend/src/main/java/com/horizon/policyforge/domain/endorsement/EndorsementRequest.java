package com.horizon.policyforge.domain.endorsement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An endorsement request after the web layer has checked its shape (P-5): the type, an optional effective date and the
 * payload of that type. Fields of other types are null. TERM_LIFE and MOTOR sum-insured changes carry
 * {@code newSumInsured}; HOUSEHOLD carries {@code newBuildingSumInsured} and/or {@code newContentsSumInsured}.
 * The string form shows no personal data.
 */
public record EndorsementRequest(
        EndorsementType type,
        LocalDate effectiveDate,
        Address address,
        Nominee nominee,
        BigDecimal newSumInsured,
        BigDecimal newBuildingSumInsured,
        BigDecimal newContentsSumInsured) {

    public static EndorsementRequest changeAddress(LocalDate effectiveDate, Address address) {
        return new EndorsementRequest(EndorsementType.CHANGE_ADDRESS, effectiveDate, address, null, null, null, null);
    }

    public static EndorsementRequest addNominee(LocalDate effectiveDate, Nominee nominee) {
        return new EndorsementRequest(EndorsementType.ADD_NOMINEE, effectiveDate, null, nominee, null, null, null);
    }

    public static EndorsementRequest changeSumInsured(LocalDate effectiveDate, BigDecimal newSumInsured) {
        return new EndorsementRequest(EndorsementType.CHANGE_SUM_INSURED, effectiveDate, null, null, newSumInsured,
                null, null);
    }

    public static EndorsementRequest changeHouseholdSumInsured(
            LocalDate effectiveDate, BigDecimal newBuildingSumInsured, BigDecimal newContentsSumInsured) {
        return new EndorsementRequest(EndorsementType.CHANGE_SUM_INSURED, effectiveDate, null, null, null,
                newBuildingSumInsured, newContentsSumInsured);
    }

    @Override
    public String toString() {
        return "EndorsementRequest[type=" + type + "]";
    }
}
