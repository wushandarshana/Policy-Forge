package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.EndorsementApi.addressBody;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static com.horizon.policyforge.support.EndorsementApi.nomineeBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.support.EndorsementRows;
import com.horizon.policyforge.support.PolicyRows;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Endorsements and the policy state machine (AC-10.2, AC-10.4). Nothing can reach RENEWED, LAPSED or CANCELLED through
 * the API yet (Sprint 4), so the set-up moves a policy there the way the services will (P-6): the aggregate's
 * {@code applyTransition}, backed by {@code PolicyStateMachine}, plus an appended transition row.
 */
@Tag("AC-10")
class EndorsementStateIT extends EndorsementTestSupport {

    private static final String INVALID_STATE_TYPE = "/problems/invalid-policy-state";

    // ---- AC-10.2 ----

    @ParameterizedTest(name = "AC-10.2: a {0} policy refuses endorse and preview of every type with 409 invalid-policy-state and writes nothing")
    @MethodSource("terminalStatuses")
    void terminalPoliciesCannotBeEndorsed(PolicyStatus status) throws Exception {
        String number = issued("TERM_LIFE");
        states.move(number, status, "TEST_SETUP");
        EndorsementRows.Snapshot before = snapshot(number);
        List<Map<String, Object>> bodies = List.of(newAddressBody(null),
                nomineeBody("Nominee One", "SPOUSE", 40, null), sumInsuredBody("7000000.00", null));
        Set<String> details = new HashSet<>();

        for (Map<String, Object> body : bodies) {
            for (MvcResult result : List.of(endorsements.endorse("customer1", number, body),
                    endorsements.preview("customer1", number, body))) {
                JsonNode problem = problem(result, 409);
                assertThat(problem.get("type").asText()).isEqualTo(INVALID_STATE_TYPE);
                assertThat(problem.get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
                assertThat(result.getResponse().getContentAsString()).as("fixed text, no policy number or status")
                        .doesNotContain(number).doesNotContain("CANCELLED").doesNotContain("LAPSED")
                        .doesNotContain("Nominee One");
                details.add(problem.get("detail").asText());
            }
        }

        assertThat(details).as("one fixed detail").hasSize(1);
        assertThat(snapshot(number)).as("no endorsement, version or transition row is written").isEqualTo(before);
        assertThat(EndorsementRows.endorsements(jdbc, number)).isEmpty();
        JsonNode header = detail(number).get("policy");
        assertThat(header.get("status").asText()).isEqualTo(status.name());
        assertThat(header.get("endorsementCount").asInt()).isZero();
    }

    static Stream<Arguments> terminalStatuses() {
        return Stream.of(Arguments.of(PolicyStatus.CANCELLED), Arguments.of(PolicyStatus.LAPSED));
    }

    @Test
    @DisplayName("AC-10.2: the state check comes before the business rules (a bad postcode on a CANCELLED policy is 409, not 400) and after the shape check (a shape error is still 400)")
    void stateIsCheckedAfterShapeAndBeforeTheRules() throws Exception {
        String number = issued("TERM_LIFE");
        states.move(number, PolicyStatus.CANCELLED, "TEST_SETUP");

        MvcResult ruleViolation = endorsements.endorse("customer1", number,
                addressBody("9 Hill Lane", "Nagpur", "4400", "2031-01-01"));
        MvcResult shapeError = endorsements.endorse("customer1", number, Map.of("type", "SYNTHETIC_TYPE_BETA"));

        assertThat(problem(ruleViolation, 409).get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
        assertThat(errorFields(problem(shapeError, 400))).containsExactly("type");
    }

    @ParameterizedTest(name = "AC-10.2: an in-force {0} policy can be endorsed and then reads ENDORSED")
    @MethodSource("inForceStatuses")
    void inForcePoliciesCanBeEndorsed(PolicyStatus status) throws Exception {
        String number = issued("TERM_LIFE");
        if (status != PolicyStatus.ACTIVE) {
            states.move(number, status, "TEST_SETUP");
        }

        JsonNode created = endorsements.endorseOk(number, newAddressBody(null));

        assertThat(created.get("policy").get("status").asText()).isEqualTo("ENDORSED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions.get(transitions.size() - 1)).containsEntry("FROM_STATUS", status.name())
                .containsEntry("TO_STATUS", "ENDORSED");
    }

    static Stream<Arguments> inForceStatuses() {
        return Stream.of(Arguments.of(PolicyStatus.ACTIVE), Arguments.of(PolicyStatus.ENDORSED),
                Arguments.of(PolicyStatus.RENEWED));
    }

    // ---- AC-10.4 ----

    /** One operation of a sequence: an endorsement of a type, or a move that the services will make in Sprint 4. */
    private enum Op { ADDRESS, NOMINEE, SUM, RENEW, LAPSE, CANCEL }

    static Stream<Arguments> sequences() {
        return Stream.of(
                Arguments.of("issue only", List.of()),
                Arguments.of("one address change", List.of(Op.ADDRESS)),
                Arguments.of("address, nominee, sum insured", List.of(Op.ADDRESS, Op.NOMINEE, Op.SUM)),
                Arguments.of("four mixed endorsements", List.of(Op.SUM, Op.ADDRESS, Op.NOMINEE, Op.ADDRESS)),
                Arguments.of("endorse, renew, endorse", List.of(Op.ADDRESS, Op.RENEW, Op.ADDRESS)),
                Arguments.of("renew twice, then a sum-insured change", List.of(Op.RENEW, Op.RENEW, Op.SUM)),
                Arguments.of("endorse, renew, lapse, endorse refused", List.of(Op.ADDRESS, Op.RENEW, Op.LAPSE, Op.ADDRESS)),
                Arguments.of("nominee, renew, cancel, two refused",
                        List.of(Op.NOMINEE, Op.RENEW, Op.CANCEL, Op.SUM, Op.NOMINEE)));
    }

    @ParameterizedTest(name = "AC-10.4: after {0} the policy status equals the last transition's to, and the transition count is the applied operations + 1")
    @MethodSource("sequences")
    void statusFollowsTheLastTransition(String label, List<Op> operations) throws Exception {
        String number = issued("TERM_LIFE");
        PolicyStatus expected = PolicyStatus.ACTIVE;
        int applied = 0;
        int sumInsuredChanges = 0;
        assertConsistent(number, expected, 1);

        for (Op op : operations) {
            boolean inForce = expected.isInForce();
            switch (op) {
                case ADDRESS, NOMINEE, SUM -> {
                    Map<String, Object> body = switch (op) {
                        case ADDRESS -> newAddressBody(null);
                        case NOMINEE -> nomineeBody("Nominee One", "SPOUSE", 10, null);
                        default -> sumInsuredBody((5_000_000 + 100_000 * ++sumInsuredChanges) + ".00", null);
                    };
                    MvcResult result = endorsements.endorse("customer1", number, body);
                    if (inForce) {
                        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                                .isEqualTo(201);
                        expected = PolicyStatus.ENDORSED;
                        applied++;
                    } else {
                        assertThat(problem(result, 409).get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
                    }
                }
                case RENEW -> {
                    states.move(number, PolicyStatus.RENEWED, "TEST_SETUP");
                    expected = PolicyStatus.RENEWED;
                    applied++;
                }
                case LAPSE -> {
                    states.move(number, PolicyStatus.LAPSED, "TEST_SETUP");
                    expected = PolicyStatus.LAPSED;
                    applied++;
                }
                case CANCEL -> {
                    states.move(number, PolicyStatus.CANCELLED, "TEST_SETUP");
                    expected = PolicyStatus.CANCELLED;
                    applied++;
                }
                default -> throw new IllegalStateException(op.name());
            }
            assertConsistent(number, expected, applied + 1);
        }
    }

    /** The stored status, the last transition's target and the API's status agree, and the count is as expected. */
    private void assertConsistent(String number, PolicyStatus expected, int transitionCount) throws Exception {
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions).as("transition count").hasSize(transitionCount);
        String last = (String) transitions.get(transitions.size() - 1).get("TO_STATUS");
        assertThat(PolicyRows.policy(jdbc, number).get("STATUS")).as("status equals the last transition's to")
                .isEqualTo(last).isEqualTo(expected.name());
        assertThat(detail(number).get("policy").get("status").asText()).isEqualTo(expected.name());
    }
}
