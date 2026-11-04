package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.PolicyRows;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC-10.4 with real operations (plan P-6): the policy's status always equals the last transition's target, and the
 * transition count equals the number of status-changing operations plus one for the issuance. An offer changes no status,
 * so it adds no transition, and a refused operation (a rejected payment) adds nothing. The operations are the real
 * endpoints and the real cycle, never the Sprint 3 test helper.
 */
@Tag("AC-10")
class PolicyLifecycleSequenceIT extends RenewalTestSupport {

    /** The policy's status equals its last transition's target, in the table and in the API, and the count is as given. */
    private void assertLifecycleInvariant(String number, int expectedTransitions, String expectedStatus)
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
        JsonNode viaApi = detail.get("transitions");
        assertThat(viaApi).hasSize(expectedTransitions);
        assertThat(viaApi.get(viaApi.size() - 1).get("toStatus").asText()).isEqualTo(expectedStatus);
        assertThat(detail.get("policy").get("status").asText()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("AC-10.4: issue, offer, pay, endorse, offer, lapse: the status equals the last transition's target after every step, and the transition count is the status-changing operations + 1, where offers add none")
    void aFullLifeOfRealOperations() throws Exception {
        String number = issued("TERM_LIFE");
        assertLifecycleInvariant(number, 1, "ACTIVE");

        renewals.jobOk("2027-10-01");
        assertLifecycleInvariant(number, 1, "ACTIVE");

        at("2027-10-05");
        renewals.payOk(number);
        assertLifecycleInvariant(number, 2, "RENEWED");

        endorsements.endorseOk(number, newAddressBody("2027-11-15"));
        assertLifecycleInvariant(number, 3, "ENDORSED");

        JsonNode secondOffer = renewals.jobOk("2028-10-01");
        assertThat(PolicyRows.terms(jdbc, number)).as("the second offer is term 3").hasSize(3);
        assertThat(texts(secondOffer.get("offersCreated")))
                .contains(number);
        assertLifecycleInvariant(number, 3, "ENDORSED");

        renewals.jobOk("2028-12-01");
        assertLifecycleInvariant(number, 4, "LAPSED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.stream().map(t -> t.get("TO_STATUS")).toList())
                .containsExactly("ACTIVE", "RENEWED", "ENDORSED", "LAPSED");
        assertThat(transitions.get(3)).containsEntry("REASON", "GRACE_PERIOD_EXPIRED")
                .containsEntry("ACTOR_ID", "system:renewal-scheduler");
    }

    @Test
    @DisplayName("AC-10.4: issue, offer, lapse, then a rejected payment: the rejected operation adds no transition, and the status is still the last transition's target")
    void aRejectedPaymentAddsNothing() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        assertLifecycleInvariant(number, 1, "ACTIVE");
        at("2027-12-01");

        MvcResult lateButNotLapsed = renewals.pay("customer1", number, new LinkedHashMap<>());
        assertThat(problem(lateButNotLapsed, 409).get("code").asText()).isEqualTo("RENEWAL_PAYMENT_WINDOW_CLOSED");
        assertLifecycleInvariant(number, 1, "ACTIVE");

        renewals.jobOk("2027-12-01");
        assertLifecycleInvariant(number, 2, "LAPSED");

        MvcResult afterLapse = renewals.pay("customer1", number, new LinkedHashMap<>());
        assertThat(problem(afterLapse, 409).get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
        assertLifecycleInvariant(number, 2, "LAPSED");
        assertThat(PolicyRows.versions(jdbc, number)).as("a rejected payment appends no version").hasSize(1);
    }

    @Test
    @DisplayName("AC-10.4: an endorsed policy that is offered and then paid goes ENDORSED to RENEWED, and the transition count follows the status-changing operations")
    void anEndorsedPolicyCanRenew() throws Exception {
        String number = issued("MOTOR");
        endorsements.endorseOk(number, newAddressBody(null));
        assertLifecycleInvariant(number, 2, "ENDORSED");
        renewals.jobOk("2027-10-01");
        assertLifecycleInvariant(number, 2, "ENDORSED");
        at("2027-10-20");

        renewals.payOk(number);

        assertLifecycleInvariant(number, 3, "RENEWED");
        assertThat(PolicyRows.transitions(jdbc, number).get(2)).containsEntry("FROM_STATUS", "ENDORSED")
                .containsEntry("TO_STATUS", "RENEWED").containsEntry("REASON", "RENEWAL_PAID");
        assertThat(PolicyRows.versions(jdbc, number)).as("issue, endorsement and payment each appended one version")
                .hasSize(3);
    }
}
