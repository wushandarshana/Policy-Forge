package com.horizon.policyforge.domain.cancellation;

import java.time.LocalDate;
import java.util.Objects;

/** The pure cancellation-date rule (AC-08.1, AC-08.6). Today is always an argument; no clock is read here. */
public final class CancellationDateRule {

    /**
     * The date to cancel on: {@code requested}, or {@code today} when it is absent. It must satisfy
     * {@code max(today, termStart) <= date <= termEnd}: within the current paid term and never in the past, so a
     * cancellation cannot be backdated into cooling-off or a larger pro-rata refund (AC-08.6).
     *
     * @param field the request field named in the failure (for example {@code cancellationDate} or {@code date})
     * @throws InvalidCancellationDateException when the date is outside the term or before today
     */
    public LocalDate resolve(LocalDate requested, LocalDate today, LocalDate termStart, LocalDate termEnd,
            String field) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(termStart, "termStart");
        Objects.requireNonNull(termEnd, "termEnd");
        LocalDate date = requested == null ? today : requested;
        LocalDate earliest = today.isAfter(termStart) ? today : termStart;
        if (date.isBefore(earliest) || date.isAfter(termEnd)) {
            throw new InvalidCancellationDateException(field);
        }
        return date;
    }
}
