package com.horizon.policyforge.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Issuance date rules (AC-05.1): the effective-date window and the expiry date. Today is always passed in. */
@Tag("AC-05")
class IssuanceDatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Test
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: expiry is effectiveDate + termMonths - 1 day: 2026-11-01 with 12 months gives 2027-10-31")
    void expiryDate() {
        assertThat(IssuanceDates.expiryDate(LocalDate.of(2026, 11, 1), 12)).isEqualTo(LocalDate.of(2027, 10, 31));
        assertThat(IssuanceDates.expiryDate(LocalDate.of(2026, 1, 31), 1)).isEqualTo(LocalDate.of(2026, 2, 27));
    }

    @Test
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: a missing effective date defaults to today")
    void defaultsToToday() {
        assertThat(IssuanceDates.resolveEffectiveDate(null, TODAY)).isEqualTo(TODAY);
    }

    @Test
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: today and today + 60 days are accepted")
    void windowBoundsAccepted() {
        assertThat(IssuanceDates.resolveEffectiveDate(TODAY, TODAY)).isEqualTo(TODAY);
        assertThat(IssuanceDates.resolveEffectiveDate(TODAY.plusDays(60), TODAY)).isEqualTo(TODAY.plusDays(60));
    }

    @Test
    @Tag("AC-05.1")
    @DisplayName("AC-05.1: today - 1 and today + 61 are rejected naming effectiveDate with EFFECTIVE_DATE_OUT_OF_RANGE")
    void outsideWindowRejected() {
        for (LocalDate bad : new LocalDate[] {TODAY.minusDays(1), TODAY.plusDays(61)}) {
            assertThatThrownBy(() -> IssuanceDates.resolveEffectiveDate(bad, TODAY))
                    .isInstanceOf(InvalidEffectiveDateException.class)
                    .satisfies(e -> {
                        InvalidEffectiveDateException ex = (InvalidEffectiveDateException) e;
                        assertThat(ex.field()).isEqualTo("effectiveDate");
                        assertThat(ex.code()).isEqualTo("EFFECTIVE_DATE_OUT_OF_RANGE");
                        assertThat(ex.getMessage()).doesNotContain(bad.toString());
                    });
        }
    }
}
