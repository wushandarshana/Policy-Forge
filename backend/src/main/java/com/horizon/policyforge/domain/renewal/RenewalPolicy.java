package com.horizon.policyforge.domain.renewal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** The pure renewal rules (AC-07.1, AC-07.2, AC-07.5): current paid term, offer timing, renewal dates, payment window. */
public final class RenewalPolicy {

    /** The paid term with the highest term number (G-5). */
    public Optional<TermPeriod> currentPaidTerm(List<TermPeriod> terms) {
        return Optional.empty();
    }

    /** The paid term whose start is on or before {@code date} and whose end is on or after it (P-11). */
    public Optional<TermPeriod> termInForce(List<TermPeriod> terms, LocalDate date) {
        return Optional.empty();
    }

    /** The unpaid term numbered above the current paid term, if any. */
    public Optional<TermPeriod> pendingTerm(List<TermPeriod> terms) {
        return Optional.empty();
    }

    /** Whether a renewal offer is due on {@code runDate}. */
    public boolean isOfferDue(List<TermPeriod> terms, int offerDaysBeforeExpiry, LocalDate runDate) {
        return false;
    }

    /** The dates of the renewal term that follows a term ending on {@code termEnd}. */
    public RenewalDates renewalDates(LocalDate termEnd, int termMonths) {
        return new RenewalDates(termEnd, termEnd);
    }

    /** The last day the pending renewal can be paid: the current paid term's end plus its grace days. */
    public LocalDate payableUntil(LocalDate currentPaidTermEnd, int graceDays) {
        return currentPaidTermEnd;
    }

    /** Whether the pending renewal can be paid on {@code today}. */
    public boolean isPaymentAllowed(List<TermPeriod> terms, int graceDays, LocalDate today) {
        return false;
    }
}
