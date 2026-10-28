package com.horizon.policyforge.domain.ruleset;

import java.math.BigDecimal;

/** Minimum and maximum sum insured for a product, both inclusive. A value is null only in an unvalidated draft. */
public record SumInsuredLimits(BigDecimal min, BigDecimal max) {
}
