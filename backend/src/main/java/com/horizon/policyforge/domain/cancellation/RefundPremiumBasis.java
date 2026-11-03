package com.horizon.policyforge.domain.cancellation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The premium a refund is calculated on (AC-08.1): the term premium plus the adjustments effective in that term. */
public final class RefundPremiumBasis {

    /** The basis for a term {@code termStart..termEnd} (both inclusive). */
    public BigDecimal basis(BigDecimal termPremium, LocalDate termStart, LocalDate termEnd,
            List<DatedAdjustment> adjustments) {
        return BigDecimal.ZERO;
    }
}
