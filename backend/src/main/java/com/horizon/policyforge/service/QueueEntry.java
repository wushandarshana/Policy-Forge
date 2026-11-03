package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.application.ApplicationStatus;
import com.horizon.policyforge.domain.underwriting.ReasonCode;
import com.horizon.policyforge.domain.underwriting.UnderwritingOutcome;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One case in the underwriter queue (U-7, contract C4). It is built from an allow-list: the full name, the Aadhaar and
 * PAN already masked (the full values never leave the service), the product's declared risk inputs (health inputs
 * included, because the underwriter must review them), and the result of the automated evaluation. It carries no date
 * of birth and no address.
 *
 * @param decision    the outcome of the automated evaluation (it does not change when an underwriter decides later)
 * @param reasons     every reason of that evaluation, sorted by code (U-8)
 * @param ruleVersion the rule version that evaluation used
 */
public record QueueEntry(
        String applicationId,
        String quoteId,
        String productCode,
        ApplicationStatus status,
        Instant submittedAt,
        int ruleVersion,
        UnderwritingOutcome decision,
        List<ReasonCode> reasons,
        String fullName,
        String maskedAadhaar,
        String maskedPan,
        Map<String, Object> riskInputs) {

    public QueueEntry {
        reasons = List.copyOf(reasons);
        // A copy that keeps the declared order (Map.copyOf would not).
        riskInputs = Collections.unmodifiableMap(new LinkedHashMap<>(riskInputs));
    }

    /** The application ID only: the name and the risk inputs are personal data and are never printed (NFR-03). */
    @Override
    public String toString() {
        return "QueueEntry[applicationId=" + applicationId + "]";
    }
}
