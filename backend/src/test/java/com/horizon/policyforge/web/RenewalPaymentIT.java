package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.PolicyApi.HEADER_FIELDS;
import static com.horizon.policyforge.support.PolicyApi.TERM_FIELDS;
import static com.horizon.policyforge.support.RenewalApi.PAYMENT_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Paying the renewal offer (AC-07.5, plan G-3 and P-5): the owner pays the pending term, and in one transaction the term
 * is marked paid, policy version n + 1 is appended and the policy moves to RENEWED with reason RENEWAL_PAID. The amount
 * is the term's premium, never a client value. Every refused payment is proved to write nothing.
 */
@Tag("AC-07")
class RenewalPaymentIT extends RenewalTestSupport {

    private static void assertMoney(Object actual, String expected) {
        assertThat(((BigDecimal) actual).toPlainString()).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-07.5: paying on 2027-11-10 gives 200 with the header and the paid term, the term is paid, the status is RENEWED, version 2 carries the renewal term per G-3 and the transition is RENEWAL_PAID by customer1")
    void payingWithinGraceRenewsThePolicy() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-10");

        MvcResult result = renewals.pay("customer1", number, new LinkedHashMap<>());

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        JsonNode paid = renewals.bodyOf(result);
        assertThat(fieldNames(paid)).as("the C2 key set, and nothing else").isEqualTo(PAYMENT_FIELDS);
        assertThat(fieldNames(paid.get("policy"))).isEqualTo(HEADER_FIELDS);
        assertThat(fieldNames(paid.get("term"))).isEqualTo(TERM_FIELDS);
        JsonNode term = paid.get("term");
        assertThat(term.get("termNo").asInt()).isEqualTo(2);
        assertThat(term.get("startDate").asText()).isEqualTo("2027-11-01");
        assertThat(term.get("endDate").asText()).isEqualTo("2028-10-31");
        assertThat(term.get("premium").asText()).isEqualTo("6850.00");
        assertThat(term.get("paid").asBoolean()).isTrue();
        assertThat(term.get("ruleVersion").asInt()).isEqualTo(1);
        JsonNode policy = paid.get("policy");
        assertThat(policy.get("policyNumber").asText()).isEqualTo(number);
        assertThat(policy.get("status").asText()).isEqualTo("RENEWED");
        assertThat(policy.get("currentVersionNo").asInt()).isEqualTo(2);
        assertThat(policy.get("effectiveDate").asText()).as("the header follows the renewal term").isEqualTo("2027-11-01");
        assertThat(policy.get("expiryDate").asText()).isEqualTo("2028-10-31");
        assertThat(policy.get("nextPremiumDueDate").asText()).isEqualTo("2028-11-01");
        assertThat(policy.get("annualPremium").asText()).isEqualTo("6850.00");
        assertThat(policy.get("sumInsured").asText()).isEqualTo("5000000.00");
        assertThat(policy.get("endorsementCount").asInt()).isZero();

        List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
        assertThat(terms).hasSize(2);
        assertThat(terms.get(0).get("PAID")).isEqualTo(true);
        assertThat(terms.get(1).get("PAID")).as("term 2 is paid").isEqualTo(true);
        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        assertThat(versions).as("version 2 is appended, version 1 is untouched").hasSize(2);
        Map<String, Object> v1 = versions.get(0);
        Map<String, Object> v2 = versions.get(1);
        assertThat(((Number) v2.get("VERSION_NO")).intValue()).isEqualTo(2);
        assertMoney(v2.get("ANNUAL_PREMIUM"), "6850.00");
        assertMoney(v2.get("SUM_INSURED"), "5000000.00");
        assertThat(((Number) v2.get("RULE_VERSION")).intValue()).isEqualTo(1);
        assertThat(String.valueOf(v2.get("EFFECTIVE_DATE"))).isEqualTo("2027-11-01");
        assertThat(String.valueOf(v2.get("EXPIRY_DATE"))).isEqualTo("2028-10-31");
        assertThat(v2.get("ADDRESS_LINE1")).isEqualTo(v1.get("ADDRESS_LINE1"));
        assertThat(v2.get("ADDRESS_CITY")).isEqualTo(v1.get("ADDRESS_CITY"));
        assertThat(v2.get("ADDRESS_POSTCODE")).isEqualTo(v1.get("ADDRESS_POSTCODE"));
        assertThat(PolicyRows.riskInputsJson(jdbc, number, 2)).as("the risk inputs carry over")
                .isEqualTo(PolicyRows.riskInputsJson(jdbc, number, 1));
        assertThat(PolicyRows.nomineesJson(jdbc, number, 2)).isEqualTo(PolicyRows.nomineesJson(jdbc, number, 1));
        assertThat(String.valueOf(v1.get("EXPIRY_DATE"))).as("version 1 keeps its own dates").isEqualTo("2027-10-31");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions).hasSize(2);
        assertThat(transitions.get(1)).containsEntry("FROM_STATUS", "ACTIVE").containsEntry("TO_STATUS", "RENEWED")
                .containsEntry("REASON", "RENEWAL_PAID").containsEntry("ACTOR_ID", "customer1");
        Map<String, Object> row = PolicyRows.policy(jdbc, number);
        assertThat(row).containsEntry("STATUS", "RENEWED");
        assertThat(((Number) row.get("CURRENT_VERSION_NO")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-07.5: an empty JSON body is accepted like {}, and the last day of the window (2027-11-30) still pays")
    void anEmptyBodyPaysOnTheLastDay() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-30");

        MvcResult result = renewals.payEmptyBody("customer1", number);

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("RENEWED");
    }

    @Test
    @DisplayName("AC-07.5: paying a renewed policy again gives 409 NO_PENDING_RENEWAL with a fixed detail and writes nothing")
    void payingAgainHasNothingPending() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-10");
        renewals.payOk(number);
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        MvcResult again = renewals.pay("customer1", number, new LinkedHashMap<>());

        JsonNode problem = problem(again, 409);
        assertThat(problem.get("code").asText()).isEqualTo("NO_PENDING_RENEWAL");
        assertThat(problem.get("detail").asText()).doesNotContain(number);
        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-07.5: paying before any offer exists gives 409 NO_PENDING_RENEWAL and writes nothing")
    void payingWithoutAnOfferIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        MvcResult result = renewals.pay("customer1", number, new LinkedHashMap<>());

        assertThat(problem(result, 409).get("code").asText()).isEqualTo("NO_PENDING_RENEWAL");
        assertThat(RenewalRows.snapshot(jdbc, number)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-07.5: paying on 2027-12-01 before any run gives 409 RENEWAL_PAYMENT_WINDOW_CLOSED, and after the 2027-12-01 lapse gives 409 invalid-policy-state, and neither writes anything")
    void payingAfterTheWindowIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-12-01");
        RenewalRows.Snapshot beforeClosed = RenewalRows.snapshot(jdbc, number);

        MvcResult closed = renewals.pay("customer1", number, new LinkedHashMap<>());

        JsonNode closedProblem = problem(closed, 409);
        assertThat(closedProblem.get("code").asText()).isEqualTo("RENEWAL_PAYMENT_WINDOW_CLOSED");
        assertThat(closedProblem.get("detail").asText()).doesNotContain(number);
        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(beforeClosed);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ACTIVE");

        renewals.jobOk("2027-12-01");
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("LAPSED");
        RenewalRows.Snapshot beforeLapsed = RenewalRows.snapshot(jdbc, number);

        MvcResult lapsed = renewals.pay("customer1", number, new LinkedHashMap<>());

        JsonNode lapsedProblem = problem(lapsed, 409);
        assertThat(lapsedProblem.get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
        assertThat(lapsedProblem.get("type").asText()).isEqualTo("/problems/invalid-policy-state");
        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(beforeLapsed);
    }

    @Test
    @DisplayName("AC-07.5: the checks run in the P-5 order: an unknown key is 400 before the policy is looked up, an unknown or another customer's policy is 404 before any state check, and a refused state is 409")
    void checksRunInTheDocumentedOrder() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        renewals.jobOk("2027-12-01");
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("LAPSED");
        at("2027-12-01");
        Map<String, Object> unknownKey = new LinkedHashMap<>();
        unknownKey.put("amount", "1.00");

        MvcResult shapeOnUnknownPolicy = renewals.pay("customer1", "PF-TL-2026-999999", unknownKey);
        MvcResult shapeOnLapsedPolicy = renewals.pay("customer1", number, unknownKey);
        MvcResult otherCustomer = renewals.pay("customer2", number, new LinkedHashMap<>());
        MvcResult unknownPolicy = renewals.pay("customer1", "PF-TL-2026-999999", new LinkedHashMap<>());
        MvcResult lapsedOwner = renewals.pay("customer1", number, new LinkedHashMap<>());

        assertThat(problem(shapeOnUnknownPolicy, 400).get("errors").get(0).get("field").asText()).isEqualTo("amount");
        assertThat(problem(shapeOnLapsedPolicy, 400).get("errors").get(0).get("field").asText()).isEqualTo("amount");
        assertThat(problem(otherCustomer, 404).get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        assertThat(problem(unknownPolicy, 404).get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        assertThat(problem(lapsedOwner, 409).get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
    }

    @Test
    @DisplayName("AC-07.5: six payments sent at the same moment give exactly one 200 and the rest 409, and leave one paid term, one version 2 and one RENEWAL_PAID transition")
    void concurrentPaymentsRenewOnce() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-10");
        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return renewals.pay("customer1", number, new LinkedHashMap<>());
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(60, TimeUnit.SECONDS);
                int status = result.getResponse().getStatus();
                if (status == 200) {
                    ok++;
                } else {
                    JsonNode problem = problem(result, 409);
                    assertThat(problem.get("code").asText()).isIn("POLICY_UPDATE_CONFLICT", "NO_PENDING_RENEWAL");
                }
            }
            assertThat(ok).as("exactly one payment wins").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("RENEWED");
        assertThat(PolicyRows.versions(jdbc, number)).hasSize(2);
        assertThat(PolicyRows.transitions(jdbc, number)).hasSize(2);
        assertThat(PolicyRows.terms(jdbc, number).get(1).get("PAID")).isEqualTo(true);
    }

    @Test
    @DisplayName("AC-07.5: two payments of the same offer sent at the same moment give exactly one 200 and one 409, and leave one paid term 2, exactly one RENEWED version (version 2) and one RENEWAL_PAID transition")
    void twoConcurrentPaymentsRenewOnce() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-10");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return renewals.pay("customer1", number, new LinkedHashMap<>());
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(60, TimeUnit.SECONDS);
                statuses.add(result.getResponse().getStatus());
                if (result.getResponse().getStatus() != 200) {
                    assertThat(problem(result, 409).get("code").asText())
                            .isIn("POLICY_UPDATE_CONFLICT", "NO_PENDING_RENEWAL");
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).as("one 200 and one 409").containsExactlyInAnyOrder(200, 409);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("RENEWED");
        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        assertThat(versions).as("exactly one RENEWED version").hasSize(2);
        assertThat(((Number) versions.get(1).get("VERSION_NO")).intValue()).isEqualTo(2);
        assertThat(PolicyRows.transitions(jdbc, number).stream().filter(t -> "RENEWED".equals(t.get("TO_STATUS")))
                .count()).isEqualTo(1);
        assertThat(PolicyRows.terms(jdbc, number).get(1).get("PAID")).isEqualTo(true);
    }
}
