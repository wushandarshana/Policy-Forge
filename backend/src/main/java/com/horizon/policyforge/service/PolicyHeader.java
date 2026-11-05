package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyVersionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The summary of a policy as its owner sees it (C1): identity, status, the dates of the current paid term (G-5), and the sum
 * insured and premium of the current version. It carries no KYC field (P-9).
 *
 * @param effectiveDate      the start of the current paid term
 * @param expiryDate         the last day of the current paid term
 * @param nextPremiumDueDate the current paid term's end date + 1 day
 * @param endorsementCount   how many endorsements the policy has had
 */
public record PolicyHeader(
        String policyNumber,
        String applicationId,
        String productCode,
        PolicyStatus status,
        int currentVersionNo,
        int ruleVersion,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        BigDecimal sumInsured,
        BigDecimal annualPremium,
        LocalDate nextPremiumDueDate,
        int endorsementCount) {

    /** The header of a policy from its row, its current version and its current paid term (G-5). */
    static PolicyHeader of(PolicyEntity policy, PolicyVersionEntity current, PolicyTermEntity term,
            int endorsementCount) {
        return new PolicyHeader(
                policy.getPolicyNumber(),
                policy.getApplicationId(),
                policy.getProductCode(),
                policy.getStatus(),
                policy.getCurrentVersionNo(),
                current.getRuleVersion(),
                term.getStartDate(),
                term.getEndDate(),
                current.getSumInsured(),
                current.getAnnualPremium(),
                term.getEndDate().plusDays(1),
                endorsementCount);
    }
}
