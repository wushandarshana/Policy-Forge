package com.horizon.policyforge.domain.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The cancellation-date rule (AC-08.1): default to today, accept the term's first and last day, name the field. */
@Tag("AC-08")
class CancellationDateRuleTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2027, 10, 31);

    private final CancellationDateRule rule = new CancellationDateRule();

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: an absent cancellation date defaults to today")
    void defaultsToToday() {
        LocalDate today = LocalDate.of(2027, 2, 9);
        assertThat(rule.resolve(null, today, START, END, "cancellationDate")).isEqualTo(today);
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: the term start and the term end are accepted")
    void termBoundsAccepted() {
        LocalDate today = LocalDate.of(2027, 2, 9);
        assertThat(rule.resolve(START, today, START, END, "cancellationDate")).isEqualTo(START);
        assertThat(rule.resolve(END, today, START, END, "cancellationDate")).isEqualTo(END);
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: the days just outside the term are rejected naming the field with CANCELLATION_DATE_OUT_OF_TERM")
    void outsideTermRejected() {
        LocalDate today = LocalDate.of(2027, 2, 9);
        for (LocalDate bad : new LocalDate[] {START.minusDays(1), END.plusDays(1)}) {
            assertThatThrownBy(() -> rule.resolve(bad, today, START, END, "cancellationDate"))
                    .isInstanceOfSatisfying(InvalidCancellationDateException.class, e -> {
                        assertThat(e.field()).isEqualTo("cancellationDate");
                        assertThat(e.code()).isEqualTo("CANCELLATION_DATE_OUT_OF_TERM");
                        assertThat(e.getMessage()).doesNotContain(bad.toString());
                    });
        }
        assertThatThrownBy(() -> rule.resolve(END.plusDays(1), today, START, END, "date"))
                .isInstanceOfSatisfying(InvalidCancellationDateException.class,
                        e -> assertThat(e.field()).isEqualTo("date"));
    }

    @Test
    @Tag("AC-08.1")
    @DisplayName("AC-08.1: an absent date with today outside the term is rejected as well")
    void defaultedTodayOutsideTermRejected() {
        assertThatThrownBy(() -> rule.resolve(null, END.plusDays(40), START, END, "cancellationDate"))
                .isInstanceOf(InvalidCancellationDateException.class);
    }
}
