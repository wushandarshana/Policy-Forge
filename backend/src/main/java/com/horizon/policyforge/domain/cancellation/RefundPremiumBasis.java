package com.horizon.policyforge.domain.cancellation;

import com.horizon.policyforge.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** The premium a refund is calculated on (AC-08.1): the term premium plus the adjustments effective in that term. */
public final class RefundPremiumBasis {

    /**
     * The basis for the term {@code termStart..termEnd} (both inclusive), at scale 2. Adjustments whose effective date
     * lies outside the term belong to another term and are ignored.
     */
    public BigDecimal basis(BigDecimal termPremium, LocalDate termStart, LocalDate termEnd,
            List<DatedAdjustment> adjustments) {
        Objects.requireNonNull(termPremium, "termPremium");
        Objects.requireNonNull(termStart, "termStart");
        Objects.requireNonNull(termEnd, "termEnd");
        BigDecimal total = termPremium;
        for (DatedAdjustment adjustment : adjustments) {
            LocalDate effective = adjustment.effectiveDate();
            if (!effective.isBefore(termStart) && !effective.isAfter(termEnd)) {
                total = total.add(adjustment.amount());
            }
        }
        return Money.finalAmount(total);
    }
}
