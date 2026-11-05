package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.CancellationRows;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * No backdated cancellation (AC-08.6, security review BLOCKER-1): the cancellation date must satisfy
 * {@code max(today, termStart) <= cancellationDate <= termEnd}. A date before today is a 400
 * {@code CANCELLATION_DATE_OUT_OF_TERM} naming {@code cancellationDate} (cancel) or {@code date} (preview), and nothing
 * is written. The spec's numbers: today 2027-10-20, a TERM_LIFE first term 2026-11-01..2027-10-31 with cooling-off 10
 * days and fee 500.00.
 */
@Tag("AC-08")
@Tag("AC-08.6")
class CancellationBackdateIT extends CancellationTestSupport {

    @Test
    @DisplayName("AC-08.6: on 2027-10-20 a cancel with cancellationDate 2026-11-02 is 400 CANCELLATION_DATE_OUT_OF_TERM naming cancellationDate, and no refund record or transition is written")
    void aBackdatedCancelIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-20");
        CancellationRows.Snapshot before = snapshot(number);

        MvcResult cancel = cancellations.cancel("customer1", number, cancelBody("OTHER", "2026-11-02"));

        assertOutOfTerm(cancel, "cancellationDate");
        assertThat(cancel.getResponse().getContentAsString()).as("the detail never echoes the date")
                .doesNotContain("2026-11-02");
        assertThat(snapshot(number)).as("no refund record, no transition, nothing else").isEqualTo(before);
        assertThat(CancellationRows.refunds(jdbc, number)).isEmpty();
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("AC-08.6: on 2027-10-20 the preview with date=2026-11-02 is 400 CANCELLATION_DATE_OUT_OF_TERM naming date, and so is yesterday 2027-10-19 for both the preview and the cancel")
    void aBackdatedPreviewIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-20");
        CancellationRows.Snapshot before = snapshot(number);

        assertOutOfTerm(cancellations.preview("customer1", number, "2026-11-02"), "date");
        assertOutOfTerm(cancellations.preview("customer1", number, "2027-10-19"), "date");
        assertOutOfTerm(cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-10-19")),
                "cancellationDate");

        assertThat(snapshot(number)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-08.6: on 2027-10-20 cancelling with 2027-10-20 is accepted with refund max(0, 6850.00 x 12 / 365 - 500.00) = 0.00, fee 500.00 and no cooling-off, one refund record and the transition to CANCELLED")
    void cancellingTodayIsAccepted() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-20");
        Figures expected = new Figures("2027-10-20", 1, "6850.00", 365, 12, false, "500.00", "0.00",
                "max(0, 6850.00 * 12 / 365 - 500.00) = 0.00");

        JsonNode preview = cancellations.previewOk(number, "2027-10-20");
        JsonNode cancelled = cancellations.cancelOk(number, "2027-10-20");

        expected.check(preview);
        expected.check(cancelled.get("refund"));
        expected.check(onlyRefundRow(number));
        assertThat(cancelled.get("policy").get("status").asText()).isEqualTo("CANCELLED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.stream().map(t -> t.get("TO_STATUS")).toList()).containsExactly("ACTIVE", "CANCELLED");
    }

    @Test
    @DisplayName("AC-08.6: cooling-off is still judged on the cancellation date: cancelling on 2026-11-05 (today) refunds the full 6850.00 with fee 0.00, while 2026-11-02 on that day is refused")
    void coolingOffIsJudgedOnTheCancellationDate() throws Exception {
        String number = issued("TERM_LIFE");
        at("2026-11-05");

        assertOutOfTerm(cancellations.preview("customer1", number, "2026-11-02"), "date");
        JsonNode cancelled = cancellations.cancelOk(number, null);

        JsonNode refund = cancelled.get("refund");
        assertThat(refund.get("cancellationDate").asText()).isEqualTo("2026-11-05");
        assertThat(refund.get("coolingOff").asBoolean()).isTrue();
        assertThat(refund.get("fee").asText()).isEqualTo("0.00");
        assertThat(refund.get("amount").asText()).isEqualTo("6850.00");
    }
}
