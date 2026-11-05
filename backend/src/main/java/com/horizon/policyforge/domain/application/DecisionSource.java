package com.horizon.policyforge.domain.application;

/** Where an underwriting decision came from (U-9): the automated rules, an underwriter, or an admin override. */
public enum DecisionSource {
    /** The automated evaluation of the active rule version, recorded with actor {@code system}. */
    RULES,
    /** An underwriter's decision on a REFERRED application. */
    MANUAL,
    /** An admin's override of a DECLINED application. */
    OVERRIDE
}
