package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.SumInsuredLimits;
import java.math.BigDecimal;

/** Checks a sum insured against a rule set's inclusive minimum and maximum. */
final class SumInsuredCheck {

    private SumInsuredCheck() {
    }

    /** Records a violation on {@code field} when {@code amount} lies outside the limits. */
    static void within(String field, BigDecimal amount, SumInsuredLimits limits, InputReader reader) {
        if (amount.compareTo(limits.min()) < 0 || amount.compareTo(limits.max()) > 0) {
            reader.reject(field, "must be between " + limits.min().toPlainString() + " and " + limits.max().toPlainString());
        }
    }
}
