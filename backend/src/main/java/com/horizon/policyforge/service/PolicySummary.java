package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One entry of a customer's policy list (C2): sum insured and premium come from the current version. */
public record PolicySummary(
        String policyNumber,
        String productCode,
        PolicyStatus status,
        BigDecimal sumInsured,
        BigDecimal annualPremium,
        LocalDate nextPremiumDueDate,
        int endorsementCount) {

    static PolicySummary from(PolicyHeader header) {
        return new PolicySummary(header.policyNumber(), header.productCode(), header.status(), header.sumInsured(),
                header.annualPremium(), header.nextPremiumDueDate(), header.endorsementCount());
    }
}
