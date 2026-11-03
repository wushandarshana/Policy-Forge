package com.horizon.policyforge.service;

import java.util.Map;

/**
 * A customer's endorsement or preview request as the web layer received it (C4, C5): the authenticated username, the
 * policy number from the path and the raw JSON object of the body. The body holds personal data (an address or a
 * nominee), so the string form shows nothing of it (NFR-03).
 *
 * @param customerId   the authenticated username of the caller
 * @param policyNumber the policy named in the path
 * @param body         the request body, read but not yet checked
 */
public record EndorseCommand(String customerId, String policyNumber, Map<String, Object> body) {

    @Override
    public String toString() {
        return "EndorseCommand[policyNumber=" + policyNumber + "]";
    }
}
