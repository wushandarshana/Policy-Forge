package com.horizon.policyforge.service;

/**
 * The result of a cancellation (contract C4): the refund record that was written and the policy header after it
 * (CANCELLED).
 */
public record CancellationResult(PolicyDetail.RefundView refund, PolicyHeader policy) {
}
