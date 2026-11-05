package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.CancellationApi;
import com.horizon.policyforge.support.CancellationRows;
import com.horizon.policyforge.support.PolicyApi;
import com.horizon.policyforge.support.PolicyRows;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The refund figures of a cancellation (AC-08.1 to AC-08.3), through the preview (contract C3) and the cancel (C4). The
 * policy is issued on the fixed clock's date, 2026-11-01, so a TERM_LIFE policy runs 2026-11-01 to 2027-10-31 (365 days).
 * Every expected figure is the independent oracle's (premium_check.py refund): 4473.29, 2929.23 on 8180.85, 14609.45,
 * 6068.49, 10749.32, 8040.41 and 6312.57 on the 366-day renewal term, plus the spec's 6850.00 and 0.00.
 */
@Tag("AC-08")
class CancellationRefundIT extends CancellationTestSupport {

    private static final String TL_FORMULA = "max(0, 6850.00 * 265 / 365 - 500.00) = 4473.29";

    // ---- AC-08.1: the worked example ----

    @Test
    @DisplayName("AC-08.1: a TERM_LIFE 6850.00 policy issued 2026-11-01 and cancelled on 2027-02-09 gets 4473.29 from the preview and from the cancel (265 of 365 days, fee 500.00, the C5 formula), the stored record equals the preview field by field, and the cancel is 201 with the C4 body")
    void theWorkedExampleAgreesInPreviewResponseAndRecord() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        Figures figures = new Figures("2027-02-09", 1, "6850.00", 365, 265, false, "500.00", "4473.29", TL_FORMULA);

        JsonNode preview = cancellations.previewOk(number, "2027-02-09");

        assertThat(fieldNames(preview)).containsExactlyElementsOf(CancellationApi.PREVIEW_FIELDS);
        figures.check(preview);
        assertThat(preview.get("termStart").asText()).isEqualTo("2026-11-01");
        assertThat(preview.get("termEnd").asText()).isEqualTo("2027-10-31");
        assertThat(snapshot(number)).as("the preview writes nothing").isEqualTo(before);

        MvcResult result = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-02-09"));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode body = cancellations.bodyOf(result);
        assertThat(fieldNames(body)).containsExactlyElementsOf(CancellationApi.CANCEL_FIELDS);
        JsonNode refund = body.get("refund");
        assertThat(fieldNames(refund)).containsExactlyElementsOf(CancellationApi.REFUND_FIELDS);
        figures.check(refund);
        assertThat(refund.get("actorId").asText()).isEqualTo("customer1");
        assertThat(Instant.parse(refund.get("createdAt").asText())).isEqualTo(clock.instant());
        for (String field : List.of("termNo", "cancellationDate", "premium", "termDays", "unexpiredDays", "coolingOff",
                "fee", "amount", "formula")) {
            assertThat(refund.get(field)).as("the response equals the preview: " + field).isEqualTo(preview.get(field));
        }
        JsonNode policy = body.get("policy");
        assertThat(fieldNames(policy)).containsExactlyElementsOf(PolicyApi.HEADER_FIELDS);
        assertThat(policy.get("policyNumber").asText()).isEqualTo(number);
        assertThat(policy.get("status").asText()).isEqualTo("CANCELLED");

        Map<String, Object> row = onlyRefundRow(number);
        figures.check(row);
        assertThat(row.get("ACTOR_ID")).isEqualTo("customer1");
        assertThat(row.get("CREATED_AT")).isNotNull();
        CancellationRows.Snapshot after = snapshot(number);
        assertThat(after.totalRefunds()).isEqualTo(before.totalRefunds() + 1);
        assertThat(after.policyRefunds()).isEqualTo(1);
        assertThat(after.transitions()).isEqualTo(before.transitions() + 1);
        assertThat(after.terms()).as("a cancellation appends no term").isEqualTo(before.terms());
        assertThat(after.versions()).as("and no version").isEqualTo(before.versions());
        assertThat(after.endorsements()).isEqualTo(before.endorsements());
        assertThat(after.auditEntries()).as("it is recorded by its transition, not by an audit entry (P-10)")
                .isEqualTo(before.auditEntries());
        assertThat(after.status()).isEqualTo("CANCELLED");
        assertThat(after.currentVersionNo()).as("the current version does not move").isEqualTo(before.currentVersionNo());
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.get(transitions.size() - 1)).containsEntry("FROM_STATUS", "ACTIVE")
                .containsEntry("TO_STATUS", "CANCELLED").containsEntry("REASON", "NO_LONGER_NEEDED")
                .containsEntry("ACTOR_ID", "customer1");
    }

    @Test
    @DisplayName("AC-08.1: the cancellation date is optional and defaults to today, so on 2027-02-09 a cancel without a date and a preview without a date both give 4473.29, and the chosen reason is the transition's reason")
    void theDateDefaultsToToday() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-02-09");
        Figures figures = new Figures("2027-02-09", 1, "6850.00", 365, 265, false, "500.00", "4473.29", TL_FORMULA);

        figures.check(cancellations.previewOk(number, null));
        MvcResult result = cancellations.cancel("customer1", number, cancelBody("AFFORDABILITY", null));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        figures.check(cancellations.bodyOf(result).get("refund"));
        figures.check(onlyRefundRow(number));
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.get(transitions.size() - 1)).containsEntry("REASON", "AFFORDABILITY");
    }

    @ParameterizedTest(name = "AC-08.1: each of the four reasons is accepted and recorded as the transition reason: {0}")
    @ValueSource(strings = {"NO_LONGER_NEEDED", "REPLACED_BY_OTHER_COVER", "AFFORDABILITY", "OTHER"})
    void everyReasonIsAccepted(String reason) throws Exception {
        String number = issued("TERM_LIFE");

        MvcResult result = cancellations.cancel("customer1", number, cancelBody(reason, null));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.get(transitions.size() - 1)).containsEntry("REASON", reason);
    }

    @ParameterizedTest(name = "AC-08.1: {0} {1} cancelled on 2027-02-09 uses its own fee {2} and gives {3}")
    @CsvSource({
        "MOTOR, 15150.00, 250.00, 10749.32",
        "HOUSEHOLD, 11350.00, 200.00, 8040.41",
    })
    void eachProductUsesItsOwnFee(String product, String premium, String fee, String refund) throws Exception {
        String number = issued(product);
        Figures figures = new Figures("2027-02-09", 1, premium, 365, 265, false, fee, refund,
                "max(0, " + premium + " * 265 / 365 - " + fee + ") = " + refund);

        JsonNode preview = cancellations.previewOk(number, "2027-02-09");
        JsonNode cancelled = cancellations.cancelOk(number, "2027-02-09");

        figures.check(preview);
        figures.check(cancelled.get("refund"));
        figures.check(onlyRefundRow(number));
    }

    // ---- AC-08.1: the premium basis ----

    @Test
    @DisplayName("AC-08.1: after a +1330.85 sum-insured endorsement effective 2027-05-01, cancelling on 2027-06-01 refunds 2929.23 on a basis of 8180.85 (the term premium 6850.00 plus the adjustment), in the preview, the response and the record")
    void theBasisIncludesTheEndorsementAdjustment() throws Exception {
        String number = issued("TERM_LIFE");
        JsonNode endorsed = endorsements.endorseOk(number, sumInsuredBody("7000000.00", "2027-05-01"));
        assertThat(endorsed.get("endorsement").get("adjustment").asText()).isEqualTo("1330.85");
        Figures figures = new Figures("2027-06-01", 1, "8180.85", 365, 153, false, "500.00", "2929.23",
                "max(0, 8180.85 * 153 / 365 - 500.00) = 2929.23");

        JsonNode preview = cancellations.previewOk(number, "2027-06-01");
        JsonNode cancelled = cancellations.cancelOk(number, "2027-06-01");

        figures.check(preview);
        figures.check(cancelled.get("refund"));
        figures.check(onlyRefundRow(number));
        assertThat(cancelled.get("policy").get("status").asText()).isEqualTo("CANCELLED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.get(transitions.size() - 1)).containsEntry("FROM_STATUS", "ENDORSED")
                .containsEntry("TO_STATUS", "CANCELLED");
    }

    // ---- AC-08.1: the date window ----

    @ParameterizedTest(name = "AC-08.1: the date {0} is outside the term 2026-11-01..2027-10-31, so the cancel names cancellationDate and the preview names date, each a 400 CANCELLATION_DATE_OUT_OF_TERM that writes nothing")
    @ValueSource(strings = {"2026-10-31", "2027-11-01", "2000-01-01", "2099-12-31"})
    void aDateOutsideTheTermIsRefused(String date) throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);

        MvcResult cancel = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", date));
        MvcResult preview = cancellations.preview("customer1", number, date);

        assertOutOfTerm(cancel, "cancellationDate");
        assertOutOfTerm(preview, "date");
        assertThat(cancel.getResponse().getContentAsString()).as("the date is not echoed").doesNotContain(date);
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @ParameterizedTest(name = "AC-08.1: the first day and the last day of the term are inside it: {0}")
    @ValueSource(strings = {"2026-11-01", "2027-10-31"})
    void theTermBoundariesAreInside(String date) throws Exception {
        String number = issued("TERM_LIFE");

        JsonNode preview = cancellations.previewOk(number, date);

        assertThat(preview.get("cancellationDate").asText()).isEqualTo(date);
        assertThat(preview.get("termDays").asInt()).isEqualTo(365);
        assertThat(cancellations.cancelOk(number, date).get("refund").get("cancellationDate").asText())
                .isEqualTo(date);
    }

    @Test
    @DisplayName("AC-08.1: in the grace period after the term ended (an offer pending, not yet lapsed) today's date is outside the current paid term, so a cancel and a preview without a date are 400 CANCELLATION_DATE_OUT_OF_TERM and nothing is written")
    void inGraceTodayIsOutOfTheTerm() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-10");
        CancellationRows.Snapshot before = snapshot(number);

        MvcResult cancel = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", null));
        MvcResult preview = cancellations.preview("customer1", number, null);

        assertOutOfTerm(cancel, "cancellationDate");
        assertOutOfTerm(preview, "date");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
        assertThat(before.status()).as("still in force: the grace period has not run out").isEqualTo("ACTIVE");
    }

    // ---- AC-08.1: the request shape ----

    @Test
    @DisplayName("AC-08.1: a malformed or non-text cancellationDate is 400 naming cancellationDate and never echoing it, and a malformed preview date, an empty one and a repeated one are 400 naming date; nothing is written")
    void aMalformedDateIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        Map<String, Object> numeric = new LinkedHashMap<>(cancelBody("NO_LONGER_NEEDED", null));
        numeric.put("cancellationDate", 20270209);

        for (String bad : List.of("2027-13-45", "tomorrow", "2027-02-30", "2027-2-9", "09/02/2027", "")) {
            MvcResult cancel = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", bad));
            assertThat(errorFields(problem(cancel, 400))).as("cancel with date '%s'", bad)
                    .containsExactly("cancellationDate");
            if (!bad.isEmpty()) {
                assertThat(cancel.getResponse().getContentAsString()).doesNotContain(bad);
            }
        }
        assertThat(errorFields(problem(cancellations.cancel("customer1", number, numeric), 400)))
                .containsExactly("cancellationDate");
        for (String rawQuery : List.of("date=2027-13-45", "date=tomorrow", "date=", "date=2027-02-09&date=2027-03-01")) {
            MvcResult preview = cancellations.previewRaw("customer1", number, rawQuery);
            assertThat(errorFields(problem(preview, 400))).as("preview with ?%s", rawQuery).containsExactly("date");
            assertThat(preview.getResponse().getContentAsString()).doesNotContain("tomorrow");
        }

        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-08.1: a missing, null, non-text, lower-case or unknown reason is 400 naming reason and never echoing it, an empty body is 400, and nothing is written")
    void aBadReasonIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        Map<String, Object> nullReason = new LinkedHashMap<>();
        nullReason.put("reason", null);
        Map<String, Object> numberReason = new LinkedHashMap<>();
        numberReason.put("reason", 5);
        Map<String, Object> listReason = new LinkedHashMap<>();
        listReason.put("reason", List.of("OTHER"));

        List<Object> bodies = List.of(Map.of(), cancelBody(null, "2027-02-09"), nullReason, numberReason, listReason,
                cancelBody("no_longer_needed", null), cancelBody("SYNTHETIC_REASON_BETA", null),
                cancelBody("OTHER ", null), cancelBody("", null));
        for (Object body : bodies) {
            MvcResult result = cancellations.cancel("customer1", number, body);
            assertThat(errorFields(problem(result, 400))).as("body %s", body).containsExactly("reason");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("SYNTHETIC_REASON_BETA")
                    .doesNotContain("no_longer_needed");
        }
        MvcResult empty = cancellations.cancelEmptyBody("customer1", number);
        problem(empty, 400);

        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-08.1: an unknown key is 400 naming it sanitised (a 5000-character key is cut to 64, markup characters become underscores), a refund amount or a term number cannot be supplied, all faults are reported together, and nothing is written")
    void unknownKeysAreRefusedAndSanitised() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        Map<String, Object> markup = new LinkedHashMap<>(cancelBody("NO_LONGER_NEEDED", null));
        markup.put("<b>unknown key</b>", 1);
        Map<String, Object> longKey = new LinkedHashMap<>(cancelBody("NO_LONGER_NEEDED", null));
        longKey.put("k".repeat(5000), 1);
        Map<String, Object> amount = new LinkedHashMap<>(cancelBody("NO_LONGER_NEEDED", null));
        amount.put("amount", "9999.00");
        amount.put("termNo", 2);
        Map<String, Object> everything = new LinkedHashMap<>(cancelBody("NOT_A_REASON", "not-a-date"));
        everything.put("fee", "0.00");

        MvcResult markupResult = cancellations.cancel("customer1", number, markup);
        MvcResult longResult = cancellations.cancel("customer1", number, longKey);
        MvcResult amountResult = cancellations.cancel("customer1", number, amount);
        MvcResult allResult = cancellations.cancel("customer1", number, everything);

        assertThat(errorFields(problem(markupResult, 400))).containsExactly("_b_unknown_key__b_");
        assertThat(markupResult.getResponse().getContentAsString()).doesNotContain("<b>");
        assertThat(errorFields(problem(longResult, 400))).containsExactly("k".repeat(64));
        assertThat(longResult.getResponse().getContentAsString()).doesNotContain("k".repeat(65));
        assertThat(errorFields(problem(amountResult, 400))).containsExactlyInAnyOrder("amount", "termNo");
        assertThat(amountResult.getResponse().getContentAsString()).doesNotContain("9999.00");
        assertThat(errorFields(problem(allResult, 400))).containsExactlyInAnyOrder("fee", "cancellationDate", "reason");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-08.1: the checks run in the P-5 order: the shape (400) before the owner (404), the owner before the state (409), and the state before the date window (400), for the cancel and for the preview")
    void theChecksRunInTheDocumentedOrder() throws Exception {
        String number = issued("TERM_LIFE");

        // The shape comes before the owner: another customer's bad request is a 400, a good one is a 404.
        assertThat(errorFields(problem(cancellations.cancel("customer2", number, Map.of()), 400)))
                .containsExactly("reason");
        problem(cancellations.cancel("customer2", number, cancelBody("NO_LONGER_NEEDED", "2027-02-09")), 404);
        assertThat(errorFields(problem(cancellations.preview("customer2", number, "tomorrow"), 400)))
                .containsExactly("date");
        problem(cancellations.preview("customer2", number, "2027-02-09"), 404);
        problem(cancellations.preview("customer2", number, "2030-01-01"), 404);

        // A policy in force and a date outside the term: the date window is the answer.
        assertOutOfTerm(cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2030-01-01")),
                "cancellationDate");

        // A cancelled policy and a date outside the term: the state comes first, but the shape still comes before it.
        cancellations.cancelOk(number, "2027-02-09");
        assertInvalidState(cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2030-01-01")),
                number);
        assertInvalidState(cancellations.preview("customer1", number, "2030-01-01"), number);
        assertThat(errorFields(problem(cancellations.cancel("customer1", number, Map.of()), 400)))
                .containsExactly("reason");
        assertThat(errorFields(problem(cancellations.preview("customer1", number, "tomorrow"), 400)))
                .containsExactly("date");
    }

    // ---- AC-08.1: the current paid term after an early renewal payment (G-5) ----

    @Test
    @DisplayName("AC-08.1: after an early renewal payment the current paid term is the renewal term, so a date in term 1 and today's date are 400, and 2027-11-03 refunds 6312.57 against the renewal premium 6850.00 over its 366 days (364 unexpired, fee 500.00, no cooling-off)")
    void afterAnEarlyRenewalPaymentTheRenewalTermIsTheTerm() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-10-05");
        renewals.payOk(number);
        CancellationRows.Snapshot before = snapshot(number);
        Figures figures = new Figures("2027-11-03", 2, "6850.00", 366, 364, false, "500.00", "6312.57",
                "max(0, 6850.00 * 364 / 366 - 500.00) = 6312.57");

        assertOutOfTerm(cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-10-20")),
                "cancellationDate");
        assertOutOfTerm(cancellations.preview("customer1", number, "2027-10-20"), "date");
        assertOutOfTerm(cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", null)),
                "cancellationDate");
        assertOutOfTerm(cancellations.preview("customer1", number, null), "date");
        assertThat(snapshot(number)).as("nothing written by the refused requests").isEqualTo(before);

        JsonNode preview = cancellations.previewOk(number, "2027-11-03");
        JsonNode cancelled = cancellations.cancelOk(number, "2027-11-03");

        figures.check(preview);
        assertThat(preview.get("termStart").asText()).isEqualTo("2027-11-01");
        assertThat(preview.get("termEnd").asText()).isEqualTo("2028-10-31");
        figures.check(cancelled.get("refund"));
        figures.check(onlyRefundRow(number));
        assertThat(cancelled.get("policy").get("status").asText()).isEqualTo("CANCELLED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.stream().map(t -> t.get("TO_STATUS")).toList())
                .containsExactly("ACTIVE", "RENEWED", "CANCELLED");
    }

    // ---- AC-08.2: cooling-off ----

    @Test
    @DisplayName("AC-08.2: TERM_LIFE cancelled on 2026-11-11, 10 days after the effective date, refunds the full premium 6850.00 with fee 0.00 and coolingOff true, in the preview, the response and the record")
    void insideTheCoolingOffTheFullPremiumIsRefunded() throws Exception {
        String number = issued("TERM_LIFE");
        Figures figures = new Figures("2026-11-11", 1, "6850.00", 365, 355, true, "0.00", "6850.00",
                "cooling-off: full premium 6850.00 = 6850.00");

        JsonNode preview = cancellations.previewOk(number, "2026-11-11");
        JsonNode cancelled = cancellations.cancelOk(number, "2026-11-11");

        figures.check(preview);
        figures.check(cancelled.get("refund"));
        figures.check(onlyRefundRow(number));
    }

    @Test
    @DisplayName("AC-08.2: a policy issued today and cancelled today, with no date given, is inside cooling-off and refunds exactly 6850.00 with fee 0.00")
    void cancellingOnTheIssueDayIsInsideTheCoolingOff() throws Exception {
        String number = issued("TERM_LIFE");

        JsonNode cancelled = cancellations.cancelOk(number, null);

        new Figures("2026-11-01", 1, "6850.00", 365, 365, true, "0.00", "6850.00",
                "cooling-off: full premium 6850.00 = 6850.00").check(cancelled.get("refund"));
    }

    @ParameterizedTest(name = "AC-08.2: {0} (cooling-off {1} days) cancelled {2} days after the effective date is inside the cooling-off: {3}")
    @CsvSource({
        "TERM_LIFE, 15, 14, true",
        "TERM_LIFE, 15, 15, false",
        "MOTOR, 7, 6, true",
        "MOTOR, 7, 7, false",
        "HOUSEHOLD, 14, 13, true",
        "HOUSEHOLD, 14, 14, false",
    })
    void theCoolingOffBoundaryIsExclusive(String product, int coolingOffDays, int day, boolean inside)
            throws Exception {
        String number = issued(product);
        String date = ISSUE_DAY.plusDays(day).toString();

        JsonNode preview = cancellations.previewOk(number, date);

        assertThat(day < coolingOffDays).as("the table row is consistent").isEqualTo(inside);
        assertThat(preview.get("coolingOff").asBoolean()).isEqualTo(inside);
        assertThat(preview.get("fee").asText()).isEqualTo(inside ? "0.00"
                : switch (product) {
                    case "TERM_LIFE" -> "500.00";
                    case "MOTOR" -> "250.00";
                    default -> "200.00";
                });
        assertThat(preview.get("formula").asText().startsWith("cooling-off: ")).isEqualTo(inside);
    }

    @Test
    @DisplayName("AC-08.2: MOTOR cancelled on day 7 (cooling-off is 7 days, so it does not apply) gets the pro-rata refund 14609.45 on 15150.00 with fee 250.00, and TERM_LIFE on day 15 gets 6068.49")
    void onTheLastDayOfTheCoolingOffThePeriodHasEnded() throws Exception {
        String motor = issued("MOTOR");
        String termLife = issued("TERM_LIFE");

        JsonNode motorRefund = cancellations.cancelOk(motor, "2026-11-08").get("refund");
        JsonNode termLifeRefund = cancellations.cancelOk(termLife, "2026-11-16").get("refund");

        new Figures("2026-11-08", 1, "15150.00", 365, 358, false, "250.00", "14609.45",
                "max(0, 15150.00 * 358 / 365 - 250.00) = 14609.45").check(motorRefund);
        new Figures("2026-11-16", 1, "6850.00", 365, 350, false, "500.00", "6068.49",
                "max(0, 6850.00 * 350 / 365 - 500.00) = 6068.49").check(termLifeRefund);
    }

    // ---- AC-08.3: the floor ----

    @Test
    @DisplayName("AC-08.3: cancelling on the last day of the term, 2027-10-31, with a fee larger than the pro-rata amount stores a refund of 0.00, never negative, and the status becomes CANCELLED")
    void theRefundIsNeverNegative() throws Exception {
        String number = issued("TERM_LIFE");
        Figures figures = new Figures("2027-10-31", 1, "6850.00", 365, 1, false, "500.00", "0.00",
                "max(0, 6850.00 * 1 / 365 - 500.00) = 0.00");

        JsonNode preview = cancellations.previewOk(number, "2027-10-31");
        JsonNode cancelled = cancellations.cancelOk(number, "2027-10-31");

        figures.check(preview);
        figures.check(cancelled.get("refund"));
        Map<String, Object> row = onlyRefundRow(number);
        figures.check(row);
        assertThat(money(row, "AMOUNT")).isEqualTo("0.00");
        assertThat(cancelled.get("policy").get("status").asText()).isEqualTo("CANCELLED");
        assertThat(PolicyRows.policy(jdbc, number)).containsEntry("STATUS", "CANCELLED");
    }
}
