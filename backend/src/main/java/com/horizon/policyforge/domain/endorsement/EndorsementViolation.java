package com.horizon.policyforge.domain.endorsement;

/**
 * One business-rule failure of an endorsement request: the dotted request field, the machine code (E2 to E10) and a
 * fixed message that never contains the submitted value.
 */
public record EndorsementViolation(String field, String code, String message) {
}
