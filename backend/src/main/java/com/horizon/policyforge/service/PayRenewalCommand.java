package com.horizon.policyforge.service;

import java.util.List;

/**
 * A customer's request to pay the renewal offer of one of their policies (contract C2). The body carries no key, so
 * {@code unknownKeys} are the keys the caller sent anyway, for the service to reject by name. No amount is ever taken
 * from the caller: the amount paid is the pending term's premium.
 */
public record PayRenewalCommand(String customerId, String policyNumber, List<String> unknownKeys) {

    public PayRenewalCommand {
        unknownKeys = List.copyOf(unknownKeys);
    }
}
