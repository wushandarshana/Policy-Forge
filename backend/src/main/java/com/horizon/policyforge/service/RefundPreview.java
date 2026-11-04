package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.cancellation.RefundQuote;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What cancelling on {@code cancellationDate} would refund (contract C3): the current paid term, the premium basis the
 * refund is calculated on (the term premium plus the adjustments of the endorsements effective in that term) and the
 * domain's quote. Numbers, dates and the formula string only.
 */
public record RefundPreview(LocalDate cancellationDate, int termNo, LocalDate termStart, LocalDate termEnd,
        BigDecimal premium, RefundQuote quote) {
}
