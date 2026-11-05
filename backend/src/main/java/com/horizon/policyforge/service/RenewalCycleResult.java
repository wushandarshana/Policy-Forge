package com.horizon.policyforge.service;

import java.time.LocalDate;
import java.util.List;

/**
 * What one run of the renewal cycle did (contract C1): the date it ran for, the policies that were offered a renewal term
 * and the policies that lapsed, both ascending by policy number. Policy numbers only (NFR-03).
 */
public record RenewalCycleResult(LocalDate runDate, List<String> offersCreated, List<String> lapsed) {

    public RenewalCycleResult {
        offersCreated = List.copyOf(offersCreated);
        lapsed = List.copyOf(lapsed);
    }
}
