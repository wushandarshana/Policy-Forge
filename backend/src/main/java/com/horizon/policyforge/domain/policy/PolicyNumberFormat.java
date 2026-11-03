package com.horizon.policyforge.domain.policy;

import java.util.Map;

/**
 * Formats a policy number {@code PF-<TL|MO|HH>-<YYYY>-<NNNNNN>} (AC-05.1): the product prefix, the 4-digit issue year
 * and the per-product, per-year sequence zero-padded to 6 digits. Allocating the sequence is the service's job.
 */
public final class PolicyNumberFormat {

    /** The largest sequence a 6-digit number can carry. */
    public static final int MAX_SEQUENCE = 999_999;

    private static final Map<String, String> PREFIXES = Map.of("TERM_LIFE", "TL", "MOTOR", "MO", "HOUSEHOLD", "HH");

    private PolicyNumberFormat() {
    }

    /**
     * @throws InvalidPolicyNumberException for an unknown product, a year that is not 4 digits or a sequence outside
     *                                      1 to 999999
     */
    public static String format(String productCode, int year, int sequence) {
        String prefix = productCode == null ? null : PREFIXES.get(productCode);
        if (prefix == null) {
            throw new InvalidPolicyNumberException("Unknown product for a policy number");
        }
        if (year < 1000 || year > 9999) {
            throw new InvalidPolicyNumberException("Policy number year must have 4 digits");
        }
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new InvalidPolicyNumberException("Policy number sequence must be between 1 and 999999");
        }
        return String.format("PF-%s-%04d-%06d", prefix, year, sequence);
    }
}
