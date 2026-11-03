package com.horizon.policyforge.service;

import java.util.List;

/**
 * A customer's request to issue an APPROVED application, already mapped from the wire shape (C1).
 *
 * @param customerId    the authenticated username of the caller
 * @param applicationId the application to issue
 * @param effectiveDate the requested start date as text, or null when the body had none (it then defaults to today). A
 *                      value that was not a JSON string arrives as an empty string, which is never a valid date
 * @param unknownKeys   the keys the body carried that the contract does not define
 */
public record IssuePolicyCommand(String customerId, String applicationId, String effectiveDate,
        List<String> unknownKeys) {

    public IssuePolicyCommand {
        unknownKeys = unknownKeys == null ? List.of() : List.copyOf(unknownKeys);
    }
}
