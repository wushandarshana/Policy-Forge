package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static com.horizon.policyforge.support.EndorsementApi.nomineeBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.CancellationApi;
import com.horizon.policyforge.support.CancellationRows;
import com.horizon.policyforge.support.PolicyRows;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cancellation and the policy lifecycle (AC-08.4, AC-10.2, AC-10.3, AC-10.4), with real operations only (plan P-6): the
 * real renewal cycle lapses a policy, the real pay endpoint renews it and the real cancel endpoint cancels it. No test
 * here uses the Sprint 3 helper that moves a policy by hand.
 */
@Tag("AC-08")
class CancellationStateIT extends CancellationTestSupport {

    // ---- AC-08.4: exactly one record, and a second cancel is a 409 ----

    @Test
    @DisplayName("AC-08.4: after a cancel the status is CANCELLED and exactly one refund record exists, a second cancel is 409 invalid-policy-state with the fixed detail and writes nothing, and a preview of the cancelled policy is 409")
    void aSecondCancelIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        cancellations.cancelOk(number, "2027-02-09");
        CancellationRows.Snapshot cancelled = snapshot(number);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.policyRefunds()).isEqualTo(1);

        MvcResult second = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-02-09"));
        MvcResult differentDate = cancellations.cancel("customer1", number, cancelBody("OTHER", "2027-03-01"));
        MvcResult preview = cancellations.preview("customer1", number, "2027-02-09");

        assertInvalidState(second, number);
        assertInvalidState(differentDate, number);
        assertInvalidState(preview, number);
        assertThat(snapshot(number)).as("still one record, one transition, the same status").isEqualTo(cancelled);
        assertThat(onlyRefundRow(number)).as("the stored refund is the first one").containsEntry("UNEXPIRED_DAYS", 265);
        assertThat(detail(number).get("policy").get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("AC-08.4: the detail lists the refunds in append order with the C5 shape, empty before the cancel and one entry equal to the cancel response after it")
    void theDetailListsTheRefund() throws Exception {
        String number = issued("TERM_LIFE");
        assertThat(detail(number).get("refunds")).as("no refund before a cancel").isEmpty();

        JsonNode cancelled = cancellations.cancelOk(number, "2027-02-09");

        JsonNode refunds = detail(number).get("refunds");
        assertThat(refunds).hasSize(1);
        assertThat(fieldNames(refunds.get(0))).containsExactlyElementsOf(CancellationApi.REFUND_FIELDS);
        assertThat(refunds.get(0)).as("the detail shows the refund the cancel returned").isEqualTo(cancelled.get("refund"));
        assertThat(refunds.get(0).get("amount").asText()).isEqualTo("4473.29");
        assertThat(refunds.get(0).get("actorId").asText()).isEqualTo("customer1");
        JsonNode transitions = detail(number).get("transitions");
        assertThat(transitions.get(transitions.size() - 1).get("toStatus").asText()).isEqualTo("CANCELLED");
        assertThat(transitions.get(transitions.size() - 1).get("reason").asText()).isEqualTo("NO_LONGER_NEEDED");
        assertThat(detail(number).get("pendingRenewal").isNull()).as("a cancelled policy has no offer to pay").isTrue();
    }

    @Test
    @DisplayName("AC-08.4: a customer sees only their own refunds, and another policy's detail never lists them")
    void refundsBelongToTheirPolicy() throws Exception {
        String cancelledNumber = issued("TERM_LIFE");
        String otherNumber = issued("MOTOR");
        cancellations.cancelOk(cancelledNumber, "2027-02-09");

        assertThat(detail(otherNumber).get("refunds")).isEmpty();
        assertThat(detail(cancelledNumber).get("refunds")).hasSize(1);
        MvcResult stranger = policies.detail("customer2", cancelledNumber);
        assertThat(stranger.getResponse().getStatus()).isEqualTo(404);
        assertThat(stranger.getResponse().getContentAsString()).doesNotContain("4473.29");
    }

    // ---- AC-10.3: the API half (Sprint 3 P-6 carry-over) ----

    @ParameterizedTest(name = "AC-10.3: a {0} policy LAPSED by the real renewal cycle on {1} gets 409 invalid-policy-state on cancel and on preview, and no refund record is written")
    @CsvSource({
        "TERM_LIFE, 2027-12-01",
        "MOTOR, 2027-11-16",
        "HOUSEHOLD, 2027-11-16",
    })
    void aLapsedPolicyCannotBeCancelled(String product, String lapseDate) throws Exception {
        String number = issued(product);
        renewals.jobOk("2027-10-01");
        JsonNode run = renewals.jobOk(lapseDate);
        assertThat(com.horizon.policyforge.support.ApplicationApi.texts(run.get("lapsed"))).contains(number);
        at(lapseDate);
        assertThat(PolicyRows.policy(jdbc, number)).containsEntry("STATUS", "LAPSED");
        CancellationRows.Snapshot before = snapshot(number);

        MvcResult cancel = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-10-15"));
        MvcResult cancelToday = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", null));
        MvcResult preview = cancellations.preview("customer1", number, "2027-10-15");

        assertInvalidState(cancel, number);
        assertInvalidState(cancelToday, number);
        assertInvalidState(preview, number);
        assertThat(CancellationRows.refunds(jdbc, number)).as("no refund record").isEmpty();
        assertThat(snapshot(number)).as("nothing written, the policy is still LAPSED").isEqualTo(before);
        assertThat(detail(number).get("policy").get("status").asText()).isEqualTo("LAPSED");
    }

    // ---- AC-10.2: a really cancelled policy refuses endorsements ----

    @Test
    @DisplayName("AC-10.2: a policy cancelled through the real endpoint refuses an address, a nominee and a sum-insured endorsement and the preview of each with 409 invalid-policy-state, and nothing is written")
    void aCancelledPolicyCannotBeEndorsed() throws Exception {
        String number = issued("TERM_LIFE");
        cancellations.cancelOk(number, "2027-02-09");
        CancellationRows.Snapshot before = snapshot(number);
        List<Map<String, Object>> bodies = List.of(newAddressBody(null),
                nomineeBody("Nominee One", "SPOUSE", 40, null), sumInsuredBody("7000000.00", null));

        for (Map<String, Object> body : bodies) {
            for (MvcResult result : List.of(endorsements.endorse("customer1", number, body),
                    endorsements.preview("customer1", number, body))) {
                assertInvalidState(result, number);
                assertThat(result.getResponse().getContentAsString()).doesNotContain("Nominee One");
            }
        }

        assertThat(snapshot(number)).as("no endorsement, version, transition or refund is written").isEqualTo(before);
        assertThat(detail(number).get("policy").get("endorsementCount").asInt()).isZero();
    }

    // ---- AC-10.4: sequences that include a real cancel ----

    @Test
    @DisplayName("AC-10.4: issue, endorse, cancel: the status equals the last transition's target after every step and the count is the status-changing operations + 1, and a rejected endorse, pay, second cancel and preview afterwards change no count")
    void issueEndorseCancelThenRejectedOperations() throws Exception {
        String number = issued("TERM_LIFE");
        assertLifecycleInvariant(number, 1, "ACTIVE");

        endorsements.endorseOk(number, newAddressBody(null));
        assertLifecycleInvariant(number, 2, "ENDORSED");

        cancellations.cancelOk(number, "2027-02-09");
        assertLifecycleInvariant(number, 3, "CANCELLED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.stream().map(t -> t.get("TO_STATUS")).toList())
                .containsExactly("ACTIVE", "ENDORSED", "CANCELLED");
        CancellationRows.Snapshot cancelled = snapshot(number);

        MvcResult endorse = endorsements.endorse("customer1", number, newAddressBody(null));
        MvcResult pay = renewals.pay("customer1", number, new LinkedHashMap<>());
        MvcResult cancelAgain = cancellations.cancel("customer1", number, cancelBody("OTHER", null));
        MvcResult preview = cancellations.preview("customer1", number, "2027-02-09");

        assertInvalidState(endorse, number);
        assertInvalidState(pay, number);
        assertInvalidState(cancelAgain, number);
        assertInvalidState(preview, number);
        assertThat(snapshot(number)).as("the rejected operations changed no count").isEqualTo(cancelled);
        assertLifecycleInvariant(number, 3, "CANCELLED");
    }

    @Test
    @DisplayName("AC-10.4: issue, offer, pay, cancel in the renewal term: the transitions are ACTIVE, RENEWED, CANCELLED, an offer adds none, and the status equals the last transition's target after every step")
    void issueRenewCancel() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        assertLifecycleInvariant(number, 1, "ACTIVE");
        at("2027-10-05");
        renewals.payOk(number);
        assertLifecycleInvariant(number, 2, "RENEWED");

        cancellations.cancelOk(number, "2027-11-03");

        assertLifecycleInvariant(number, 3, "CANCELLED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.get(2)).containsEntry("FROM_STATUS", "RENEWED").containsEntry("TO_STATUS", "CANCELLED")
                .containsEntry("REASON", "NO_LONGER_NEEDED").containsEntry("ACTOR_ID", "customer1");
        assertThat(PolicyRows.versions(jdbc, number)).as("issue and the payment each appended one version, the cancel none")
                .hasSize(2);
        CancellationRows.Snapshot cancelled = snapshot(number);
        assertInvalidState(renewals.pay("customer1", number, new LinkedHashMap<>()), number);
        assertThat(snapshot(number)).isEqualTo(cancelled);
    }

    @Test
    @DisplayName("AC-10.4: issue, offer, then cancel inside term 1: the transitions are ACTIVE, CANCELLED, and the unpaid offer stays in the term history but cannot be paid")
    void anOfferedPolicyCanBeCancelledAndItsOfferCannotBePaid() throws Exception {
        String number = issued("MOTOR");
        renewals.jobOk("2027-10-01");
        at("2027-10-05");
        assertThat(detail(number).get("pendingRenewal").isNull()).isFalse();

        cancellations.cancelOk(number, "2027-10-20");

        assertLifecycleInvariant(number, 2, "CANCELLED");
        assertThat(PolicyRows.terms(jdbc, number)).as("the unpaid offer stays in the term history").hasSize(2);
        assertThat(detail(number).get("pendingRenewal").isNull()).as("but is no longer payable").isTrue();
        CancellationRows.Snapshot cancelled = snapshot(number);
        assertInvalidState(renewals.pay("customer1", number, new LinkedHashMap<>()), number);
        assertThat(snapshot(number)).isEqualTo(cancelled);
        new Figures("2027-10-20", 1, "15150.00", 365, 12, false, "250.00", "248.08",
                "max(0, 15150.00 * 12 / 365 - 250.00) = 248.08").check(onlyRefundRow(number));
    }
}
