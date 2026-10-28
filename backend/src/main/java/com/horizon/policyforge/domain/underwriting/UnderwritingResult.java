package com.horizon.policyforge.domain.underwriting;

import java.util.List;

/**
 * What the evaluator decided: the outcome, every fired rule's reason sorted by code (or {@code UW-OK}), and the version
 * of the rule set that was evaluated, so a stored decision can say which rules produced it.
 */
public record UnderwritingResult(UnderwritingOutcome outcome, List<ReasonCode> reasons, int ruleVersion) {

    public UnderwritingResult {
        reasons = List.copyOf(reasons);
    }

    /** The reason codes in the same order as {@link #reasons()}. */
    public List<String> reasonCodes() {
        return reasons.stream().map(ReasonCode::code).toList();
    }
}
