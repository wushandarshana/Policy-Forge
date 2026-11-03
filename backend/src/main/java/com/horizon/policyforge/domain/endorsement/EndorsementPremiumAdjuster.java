package com.horizon.policyforge.domain.endorsement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Red stub. */
public final class EndorsementPremiumAdjuster {

    public PremiumAdjustment adjust(BigDecimal currentAnnualPremium, BigDecimal newAnnualPremium,
            LocalDate termStart, LocalDate termEnd, LocalDate effectiveDate) {
        return new PremiumAdjustment(BigDecimal.ZERO, 0, 0);
    }

    public Map<String, Object> modifiedRiskInputs(String productCode, Map<String, Object> currentRiskInputs,
            Map<String, BigDecimal> newSumInsured) {
        return currentRiskInputs;
    }
}
