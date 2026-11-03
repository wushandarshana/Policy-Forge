package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.application.ApplicationStatus;
import com.horizon.policyforge.domain.underwriting.ReasonCode;
import com.horizon.policyforge.domain.underwriting.UnderwritingOutcome;
import java.time.Instant;
import java.util.List;

/**
 * An application as its owner may see it (U-7): the current status and the result of the automated {@code RULES}
 * evaluation. It carries no KYC field.
 *
 * @param decision    the outcome of the automated evaluation (it does not change when an underwriter decides later)
 * @param reasons     every reason of that evaluation, sorted by code (U-8)
 * @param ruleVersion the rule version that evaluation used
 */
public record ApplicationResult(
        String applicationId,
        String quoteId,
        String productCode,
        ApplicationStatus status,
        UnderwritingOutcome decision,
        List<ReasonCode> reasons,
        int ruleVersion,
        Instant submittedAt) {

    public ApplicationResult {
        reasons = List.copyOf(reasons);
    }
}
