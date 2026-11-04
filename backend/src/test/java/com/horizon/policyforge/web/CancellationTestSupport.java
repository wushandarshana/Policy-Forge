package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.CancellationApi;
import com.horizon.policyforge.support.CancellationRows;
import com.horizon.policyforge.support.PolicyRows;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared set-up of the cancellation integration tests. They run in the renewal tests' application context (one
 * movable clock at 2026-11-01, the policy's issue date, so a policy issued here runs 2026-11-01 to 2027-10-31), because
 * the real renewal cycle is part of what they prove (a LAPSED policy cannot be cancelled, an early renewal payment moves
 * the current paid term). Rows are found by policy number, and "nothing is written" is proved with counts taken before
 * and after.
 */
abstract class CancellationTestSupport extends RenewalTestSupport {

    protected static final String INVALID_STATE_TYPE = "/problems/invalid-policy-state";
    protected static final String OUT_OF_TERM = "CANCELLATION_DATE_OUT_OF_TERM";

    protected CancellationApi cancellations;

    @BeforeEach
    void setUpCancellationSupport() {
        cancellations = new CancellationApi(mvc, json);
    }

    protected CancellationRows.Snapshot snapshot(String policyNumber) {
        return CancellationRows.snapshot(jdbc, policyNumber);
    }

    /** The refund record of the policy as a row; requires exactly one. */
    protected Map<String, Object> onlyRefundRow(String policyNumber) {
        List<Map<String, Object>> rows = CancellationRows.refunds(jdbc, policyNumber);
        assertThat(rows).as("exactly one refund record").hasSize(1);
        return rows.get(0);
    }

    /** Requires a 409 invalid-policy-state problem with the fixed detail, naming neither status nor number. */
    protected void assertInvalidState(MvcResult result, String policyNumber) throws Exception {
        JsonNode problem = problem(result, 409);
        assertThat(problem.get("type").asText()).isEqualTo(INVALID_STATE_TYPE);
        assertThat(problem.get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
        assertThat(problem.get("detail").asText()).as("a fixed detail that never echoes input")
                .doesNotContain(policyNumber).doesNotContain("CANCELLED").doesNotContain("LAPSED");
    }

    /** Requires a 400 problem named CANCELLATION_DATE_OUT_OF_TERM that names exactly the given request field. */
    protected void assertOutOfTerm(MvcResult result, String field) throws Exception {
        JsonNode problem = problem(result, 400);
        assertThat(problem.get("code").asText()).isEqualTo(OUT_OF_TERM);
        assertThat(errorFields(problem)).containsExactly(field);
    }

    /** The money of a stored row as the plain decimal string the API shows (scale 2). */
    protected static String money(Map<String, Object> row, String column) {
        return ((BigDecimal) row.get(column)).toPlainString();
    }

    /**
     * What the figures of a preview, a cancel response and a stored refund must be. The preview also names the term
     * dates, the refund names the actor and the time.
     */
    protected record Figures(String date, int termNo, String premium, int termDays, int unexpiredDays,
            boolean coolingOff, String fee, String amount, String formula) {

        /** Checks the common fields of a preview or a C5 refund. */
        void check(JsonNode node) {
            assertThat(node.get("cancellationDate").asText()).isEqualTo(date);
            assertThat(node.get("termNo").asInt()).isEqualTo(termNo);
            assertThat(node.get("premium").asText()).isEqualTo(premium);
            assertThat(node.get("termDays").asInt()).isEqualTo(termDays);
            assertThat(node.get("unexpiredDays").asInt()).isEqualTo(unexpiredDays);
            assertThat(node.get("coolingOff").asBoolean()).isEqualTo(coolingOff);
            assertThat(node.get("fee").asText()).isEqualTo(fee);
            assertThat(node.get("amount").asText()).isEqualTo(amount);
            assertThat(node.get("formula").asText()).isEqualTo(formula);
        }

        /** Checks the stored row field by field. */
        void check(Map<String, Object> row) {
            assertThat(row.get("CANCELLATION_DATE").toString()).isEqualTo(date);
            assertThat(((Number) row.get("TERM_NO")).intValue()).isEqualTo(termNo);
            assertThat(money(row, "PREMIUM")).isEqualTo(premium);
            assertThat(((Number) row.get("TERM_DAYS")).intValue()).isEqualTo(termDays);
            assertThat(((Number) row.get("UNEXPIRED_DAYS")).intValue()).isEqualTo(unexpiredDays);
            assertThat(row.get("COOLING_OFF")).isEqualTo(coolingOff);
            assertThat(money(row, "FEE")).isEqualTo(fee);
            assertThat(money(row, "AMOUNT")).isEqualTo(amount);
            assertThat(row.get("FORMULA")).isEqualTo(formula);
        }
    }

    /** The policy's status equals its last transition's target, in the table and in the API, and the count is as given. */
    protected void assertLifecycleInvariant(String number, int expectedTransitions, String expectedStatus)
            throws Exception {
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions).as("transitions = status-changing operations + 1").hasSize(expectedTransitions);
        String last = (String) transitions.get(transitions.size() - 1).get("TO_STATUS");
        assertThat(PolicyRows.policy(jdbc, number)).as("the status is the last transition's target")
                .containsEntry("STATUS", last).containsEntry("STATUS", expectedStatus);
        for (int i = 1; i < transitions.size(); i++) {
            assertThat(transitions.get(i).get("FROM_STATUS")).as("each move starts where the one before ended")
                    .isEqualTo(transitions.get(i - 1).get("TO_STATUS"));
        }
        JsonNode detail = detail(number);
        assertThat(detail.get("transitions")).hasSize(expectedTransitions);
        assertThat(detail.get("policy").get("status").asText()).isEqualTo(expectedStatus);
    }
}
