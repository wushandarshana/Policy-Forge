package com.horizon.policyforge.domain.renewal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The renewal rules: current paid term (G-5), offer timing, renewal dates and the payment window. */
@Tag("AC-07")
class RenewalPolicyTest {

    private static final LocalDate TERM1_START = LocalDate.of(2026, 11, 1);
    private static final LocalDate TERM1_END = LocalDate.of(2027, 10, 31);
    private static final LocalDate TERM2_START = LocalDate.of(2027, 11, 1);
    private static final LocalDate TERM2_END = LocalDate.of(2028, 10, 31);

    private final RenewalPolicy policy = new RenewalPolicy();

    private static TermPeriod term1() {
        return new TermPeriod(1, TERM1_START, TERM1_END, true);
    }

    private static TermPeriod term2(boolean paid) {
        return new TermPeriod(2, TERM2_START, TERM2_END, paid);
    }

    @Test
    @Tag("AC-07.1")
    @DisplayName("AC-07.1: term 2026-11-01..2027-10-31 with 30 offer days: an offer is due on 2027-10-01, not on 2027-09-30")
    void offerDueThirtyDaysBeforeExpiry() {
        List<TermPeriod> terms = List.of(term1());
        assertThat(policy.isOfferDue(terms, 30, LocalDate.of(2027, 10, 1))).isTrue();
        assertThat(policy.isOfferDue(terms, 30, LocalDate.of(2027, 9, 30))).isFalse();
        assertThat(policy.isOfferDue(terms, 30, LocalDate.of(2027, 10, 20))).isTrue();
    }

    @Test
    @Tag("AC-07.1")
    @DisplayName("AC-07.1: the renewal term of 2026-11-01..2027-10-31 is 2027-11-01..2028-10-31")
    void renewalTermDates() {
        RenewalDates dates = policy.renewalDates(TERM1_END, 12);
        assertThat(dates.startDate()).isEqualTo(TERM2_START);
        assertThat(dates.endDate()).isEqualTo(TERM2_END);
        assertThat(policy.renewalDates(LocalDate.of(2027, 1, 31), 1).endDate()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @Tag("AC-07.1")
    @DisplayName("AC-07.1: an unpaid offer leaves term 1 as the current paid term; a paid renewal is current before it starts")
    void currentPaidTermFollowsPayment() {
        assertThat(policy.currentPaidTerm(List.of(term1(), term2(false)))).contains(term1());
        assertThat(policy.currentPaidTerm(List.of(term1(), term2(true)))).contains(term2(true));
        assertThat(policy.currentPaidTerm(List.of(term2(true), term1()))).contains(term2(true));
        assertThat(policy.currentPaidTerm(List.of(new TermPeriod(1, TERM1_START, TERM1_END, false)))).isEmpty();
        assertThat(policy.currentPaidTerm(List.of())).isEmpty();
    }

    @Test
    @Tag("AC-07.1")
    @DisplayName("AC-07.1: the term in force on a date is the paid term with start <= date <= end")
    void termInForceOnDate() {
        List<TermPeriod> terms = List.of(term1(), term2(true));
        assertThat(policy.termInForce(terms, TERM1_START)).contains(term1());
        assertThat(policy.termInForce(terms, TERM1_END)).contains(term1());
        assertThat(policy.termInForce(terms, TERM2_START)).contains(term2(true));
        assertThat(policy.termInForce(terms, TERM1_START.minusDays(1))).isEmpty();
        assertThat(policy.termInForce(terms, TERM2_END.plusDays(1))).isEmpty();
        assertThat(policy.termInForce(List.of(term1(), term2(false)), TERM2_START)).isEmpty();
    }

    @Test
    @Tag("AC-07.1")
    @DisplayName("AC-07.1: the pending term is the unpaid term above the current paid term")
    void pendingTerm() {
        assertThat(policy.pendingTerm(List.of(term1(), term2(false)))).contains(term2(false));
        assertThat(policy.pendingTerm(List.of(term1(), term2(true)))).isEmpty();
        assertThat(policy.pendingTerm(List.of(term1()))).isEmpty();
    }

    @Test
    @Tag("AC-07.2")
    @DisplayName("AC-07.2: an existing later term, paid or not, means no offer is due")
    void existingLaterTermMeansNoOffer() {
        LocalDate runDate = LocalDate.of(2027, 10, 1);
        assertThat(policy.isOfferDue(List.of(term1(), term2(false)), 30, runDate)).isFalse();
        assertThat(policy.isOfferDue(List.of(term1(), term2(true)), 30, runDate)).isFalse();
        assertThat(policy.isOfferDue(List.of(), 30, runDate)).isFalse();
    }

    @Test
    @Tag("AC-07.5")
    @DisplayName("AC-07.5: with grace 30 payment is allowed on 2027-11-10 and 2027-11-30, refused on 2027-12-01")
    void paymentWindowWithGraceThirty() {
        List<TermPeriod> terms = List.of(term1(), term2(false));
        assertThat(policy.isPaymentAllowed(terms, 30, LocalDate.of(2027, 11, 10))).isTrue();
        assertThat(policy.isPaymentAllowed(terms, 30, LocalDate.of(2027, 11, 30))).isTrue();
        assertThat(policy.isPaymentAllowed(terms, 30, LocalDate.of(2027, 12, 1))).isFalse();
        assertThat(policy.payableUntil(TERM1_END, 30)).isEqualTo(LocalDate.of(2027, 11, 30));
    }

    @Test
    @Tag("AC-07.5")
    @DisplayName("AC-07.5: payment is refused when no unpaid later term exists")
    void paymentRefusedWithoutPendingTerm() {
        LocalDate today = LocalDate.of(2027, 11, 10);
        assertThat(policy.isPaymentAllowed(List.of(term1()), 30, today)).isFalse();
        assertThat(policy.isPaymentAllowed(List.of(term1(), term2(true)), 30, today)).isFalse();
    }
}
