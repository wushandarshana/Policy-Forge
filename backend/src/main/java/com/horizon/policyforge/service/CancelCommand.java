package com.horizon.policyforge.service;

import java.util.List;

/**
 * A customer's request to cancel one of their policies (contract C4). {@code reason} and {@code cancellationDate} are
 * the body values as received: null when absent, the text when it was text, and an empty string when it was anything else
 * (a number, an object), which the service refuses as not a reason or not a date. {@code unknownKeys} are the other keys
 * the caller sent, for the service to reject by name.
 */
public record CancelCommand(String customerId, String policyNumber, String reason, String cancellationDate,
        List<String> unknownKeys) {

    public CancelCommand {
        unknownKeys = List.copyOf(unknownKeys);
    }
}
