package com.horizon.policyforge.domain.ruleset;

import java.util.List;

/**
 * A declarative underwriting rule: it fires when all of its conditions hold. {@code outcome} is {@code DECLINE} or
 * {@code REFER}.
 */
public record UnderwritingRule(String code, String outcome, String description, List<Condition> all) {

    public UnderwritingRule {
        all = all == null ? List.of() : List.copyOf(all);
    }
}
