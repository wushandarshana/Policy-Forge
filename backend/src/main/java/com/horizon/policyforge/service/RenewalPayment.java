package com.horizon.policyforge.service;

/**
 * The result of a renewal payment (contract C2): the policy header after it (RENEWED, at the renewal term's dates) and the
 * newly paid term.
 */
public record RenewalPayment(PolicyHeader policy, PolicyDetail.TermView term) {
}
