package com.horizon.policyforge.domain.endorsement;

import com.horizon.policyforge.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The pro-rata premium adjustment of a sum-insured change (AC-06.2) and the modified risk inputs it is re-rated from.
 *
 * <p>{@code adjustment = (new - current) x unexpiredDays / termDays}, where
 * {@code unexpiredDays = DAYS(effectiveDate, termEnd + 1)} and {@code termDays = DAYS(termStart, termEnd + 1)}.
 * Intermediate steps use scale 10 and the result is rounded HALF_UP to 2 dp. The re-rating itself is done by the
 * existing {@code PremiumCalculator} with the policy's own rule version; no rating logic is duplicated here.
 */
public final class EndorsementPremiumAdjuster {

    private static final Map<String, Set<String>> COMPONENTS = Map.of(
            "TERM_LIFE", Set.of("sumInsured"),
            "MOTOR", Set.of("vehicleValue"),
            "HOUSEHOLD", Set.of("buildingSumInsured", "contentsSumInsured"));

    /**
     * The adjustment for moving the annual premium from {@code currentAnnualPremium} to {@code newAnnualPremium}
     * effective {@code effectiveDate} within the term {@code termStart..termEnd} (both inclusive).
     *
     * @throws IllegalArgumentException when the effective date is outside the term
     */
    public PremiumAdjustment adjust(BigDecimal currentAnnualPremium, BigDecimal newAnnualPremium,
            LocalDate termStart, LocalDate termEnd, LocalDate effectiveDate) {
        if (effectiveDate.isBefore(termStart) || effectiveDate.isAfter(termEnd)) {
            throw new IllegalArgumentException("The effective date is outside the term");
        }
        LocalDate termEndExclusive = termEnd.plusDays(1);
        long termDays = ChronoUnit.DAYS.between(termStart, termEndExclusive);
        long unexpiredDays = ChronoUnit.DAYS.between(effectiveDate, termEndExclusive);

        BigDecimal difference = newAnnualPremium.subtract(currentAnnualPremium);
        BigDecimal prorated = Money.intermediate(difference.multiply(BigDecimal.valueOf(unexpiredDays)));
        BigDecimal perTerm = prorated.divide(BigDecimal.valueOf(termDays), Money.INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        return new PremiumAdjustment(Money.finalAmount(perTerm), unexpiredDays, termDays);
    }

    /**
     * A copy of {@code currentRiskInputs} with the sum-insured components replaced: TERM_LIFE {@code sumInsured},
     * MOTOR {@code vehicleValue}, HOUSEHOLD {@code buildingSumInsured} and/or {@code contentsSumInsured}. Amounts are
     * stored as scale-2 decimal strings, as the quote engine reads them. {@code currentRiskInputs} is not changed.
     *
     * @throws IllegalArgumentException for an unknown product, an empty replacement or a component the product lacks
     */
    public Map<String, Object> modifiedRiskInputs(String productCode, Map<String, Object> currentRiskInputs,
            Map<String, BigDecimal> newSumInsured) {
        Set<String> allowed = COMPONENTS.get(productCode);
        if (allowed == null) {
            throw new IllegalArgumentException("Unknown product for a sum-insured change");
        }
        if (newSumInsured.isEmpty() || !allowed.containsAll(newSumInsured.keySet())) {
            throw new IllegalArgumentException("Sum-insured components do not match the product");
        }
        Map<String, Object> modified = new LinkedHashMap<>(currentRiskInputs);
        newSumInsured.forEach((key, value) -> modified.put(key, Money.finalAmount(value).toPlainString()));
        return modified;
    }
}
