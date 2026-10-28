package com.horizon.archfixtures.purity.domain;

import java.math.BigDecimal;
import java.util.List;

/** Allowed: java.* only. */
public record DomainPure(BigDecimal amount, List<String> codes) {
}
