package com.horizon.policyforge.service;

import java.util.List;

/**
 * An admin's request to run the renewal cycle (contract C1). {@code date} is the raw {@code date} query parameter, or
 * null for today; the service reads it. {@code unknownKeys} are the body keys the contract does not define, so the
 * service can reject each by name. The body carries no personal data, so the default string form is safe.
 */
public record RunCycleCommand(String actorId, String date, List<String> unknownKeys) {

    public RunCycleCommand {
        unknownKeys = List.copyOf(unknownKeys);
    }
}
