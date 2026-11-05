package com.horizon.policyforge.domain.renewal;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The pure renewal rules (AC-07.1, AC-07.2, AC-07.5): current paid term, offer timing, renewal dates and the payment
 * window. Every date is an argument; no clock is read here.
 */
public final class RenewalPolicy {

    /**
     * The current paid term is the paid term with the highest term number (G-5): term 1 until a renewal is paid, and
     * the renewal term from the moment it is paid, even before it starts.
     */
    public Optional<TermPeriod> currentPaidTerm(List<TermPeriod> terms) {
        return terms.stream().filter(TermPeriod::paid).max(Comparator.comparingInt(TermPeriod::termNo));
    }

    /** The paid term whose start is on or before {@code date} and whose end is on or after it (P-11). */
    public Optional<TermPeriod> termInForce(List<TermPeriod> terms, LocalDate date) {
        Objects.requireNonNull(date, "date");
        return terms.stream()
                .filter(TermPeriod::paid)
                .filter(term -> !date.isBefore(term.startDate()) && !date.isAfter(term.endDate()))
                .max(Comparator.comparingInt(TermPeriod::termNo));
    }

    /** The lowest-numbered unpaid term above the current paid term, if any. */
    public Optional<TermPeriod> pendingTerm(List<TermPeriod> terms) {
        return currentPaidTerm(terms).flatMap(current -> terms.stream()
                .filter(term -> term.termNo() > current.termNo() && !term.paid())
                .min(Comparator.comparingInt(TermPeriod::termNo)));
    }

    /**
     * Whether a renewal offer is due: {@code runDate >= currentPaidTermEnd - offerDaysBeforeExpiry} and no later term
     * exists.
     */
    public boolean isOfferDue(List<TermPeriod> terms, int offerDaysBeforeExpiry, LocalDate runDate) {
        Objects.requireNonNull(runDate, "runDate");
        return currentPaidTerm(terms).map(current -> {
            boolean laterTermExists = terms.stream().anyMatch(term -> term.termNo() > current.termNo());
            boolean inOfferWindow = !runDate.isBefore(current.endDate().minusDays(offerDaysBeforeExpiry));
            return inOfferWindow && !laterTermExists;
        }).orElse(false);
    }

    /** The renewal term starts the day after {@code termEnd} and ends at {@code start + termMonths - 1 day}. */
    public RenewalDates renewalDates(LocalDate termEnd, int termMonths) {
        Objects.requireNonNull(termEnd, "termEnd");
        if (termMonths < 1) {
            throw new IllegalArgumentException("termMonths must be at least 1");
        }
        LocalDate start = termEnd.plusDays(1);
        return new RenewalDates(start, start.plusMonths(termMonths).minusDays(1));
    }

    /** The last day the pending renewal can be paid: the current paid term's end plus its grace days. */
    public LocalDate payableUntil(LocalDate currentPaidTermEnd, int graceDays) {
        return currentPaidTermEnd.plusDays(graceDays);
    }

    /** Whether an unpaid later term exists and {@code today <= currentPaidTermEnd + graceDays}. */
    public boolean isPaymentAllowed(List<TermPeriod> terms, int graceDays, LocalDate today) {
        Objects.requireNonNull(today, "today");
        return pendingTerm(terms).isPresent() && currentPaidTerm(terms)
                .map(current -> !today.isAfter(payableUntil(current.endDate(), graceDays)))
                .orElse(false);
    }

    /** How many days before today the admin job may run for (AC-07.8). */
    public static final int JOB_DATE_MAX_DAYS_BACK = 30;

    /** Whether the admin job may run for {@code date}: {@code today - 30 <= date <= today} (AC-07.8). */
    public boolean isJobDateAllowed(LocalDate date, LocalDate today) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(today, "today");
        return !date.isAfter(today) && !date.isBefore(today.minusDays(JOB_DATE_MAX_DAYS_BACK));
    }
}
