package com.horizon.policyforge.service;

/**
 * A customer's request to preview the refund of cancelling one of their policies (contract C3). {@code date} is the
 * query parameter as received, or null for none (today): a malformed one is the service's 400 to give, naming
 * {@code date}.
 */
public record RefundPreviewCommand(String customerId, String policyNumber, String date) {
}
