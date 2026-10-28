package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.rating.PremiumFactor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A priced, stored quote as the public API may show it. {@code riskInputs} never holds health inputs (NFR-04.2): the
 * full inputs stay server-side.
 */
public record QuoteResult(
        String quoteId,
        String productCode,
        int ruleVersion,
        BigDecimal sumInsured,
        BigDecimal annualPremium,
        List<PremiumFactor> breakdown,
        LocalDate validUntil,
        Map<String, Object> riskInputs) {
}
