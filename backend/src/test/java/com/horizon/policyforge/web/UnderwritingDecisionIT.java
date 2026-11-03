package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.decisionBody;
import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationApi.indexOf;
import static com.horizon.policyforge.support.ApplicationApi.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.repository.ApplicationRepository;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationRows;
import com.horizon.policyforge.support.ApplicationRows.Snapshot;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The manual decision endpoint {@code POST /api/underwriting/{applicationId}/decision} (AC-09.2, AC-09.4, AC-09.5 and
 * AC-09.6; contracts C3 and C5; U-9, U-10, U-12). Every refused request is followed by a check that the status, the
 * decision rows and the audit entries of the application are exactly as before. The startup seed is present (U-14), so
 * each test works on its own application, found by its ID.
 */
@Tag("AC-09")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class UnderwritingDecisionIT {

    private static final LocalDate TODAY = FixedClockConfig.TODAY;
    private static final String UNKNOWN_ID = "A-00000000-0000-4000-8000-000000000000";
    private static final String GOOD_COMMENT = "Acceptable after medical review";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ApplicationRepository applications;

    private ApplicationApi api;

    @BeforeEach
    void setUp() {
        clock.setDate(TODAY);
        api = new ApplicationApi(mvc, json);
    }

    @AfterEach
    void resetClock() {
        clock.setDate(TODAY);
    }

    private String referred() throws Exception {
        return api.referredApplication("customer1", TODAY);
    }

    private JsonNode problemOf(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        return api.bodyOf(result);
    }

    private void assertUnchanged(Snapshot before, String applicationId) {
        assertThat(ApplicationRows.snapshot(jdbc, applicationId)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-09.2: underwriter1 approves a REFERRED application with UW-MAN-001 and a comment, and the status becomes APPROVED")
    void underwriterApprovesAReferredApplication() throws Exception {
        String id = referred();

        MvcResult result = api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode body = api.bodyOf(result);
        assertThat(fieldNames(body)).containsExactly("applicationId", "previousStatus", "status");
        assertThat(body.get("applicationId").asText()).isEqualTo(id);
        assertThat(body.get("previousStatus").asText()).isEqualTo("REFERRED");
        assertThat(body.get("status").asText()).isEqualTo("APPROVED");
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("AC-09.2: a MANUAL decision row is appended with the actor, reason, comment and rule version, and the original RULES row is kept unchanged")
    void approvalAppendsAManualDecisionRow() throws Exception {
        String id = referred();

        api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT));

        List<Map<String, Object>> rows = ApplicationRows.decisions(jdbc, id);
        assertThat(rows).extracting(row -> row.get("SOURCE")).containsExactly("RULES", "MANUAL");
        Map<String, Object> rules = rows.get(0);
        assertThat(rules.get("OUTCOME")).isEqualTo("MANUAL_REVIEW");
        assertThat(rules.get("REASON_CODES")).isEqualTo("UW-TL-002");
        assertThat(rules.get("ACTOR_ID")).isEqualTo("system");
        assertThat(rules.get("COMMENT_TEXT")).isNull();
        Map<String, Object> manual = rows.get(1);
        assertThat(manual.get("OUTCOME")).isEqualTo("APPROVE");
        assertThat(manual.get("REASON_CODES")).isEqualTo("UW-MAN-001");
        assertThat(manual.get("ACTOR_ID")).isEqualTo("underwriter1");
        assertThat(manual.get("COMMENT_TEXT")).isEqualTo(GOOD_COMMENT);
        assertThat(((Number) manual.get("RULE_VERSION")).intValue()).isEqualTo(1);
        assertThat(manual.get("REASONS_JSON").toString()).contains("UW-MAN-001").contains("Acceptable after review");
        assertThat(ApplicationRows.instant(manual.get("DECIDED_AT"))).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("AC-09.2: an UNDERWRITING_DECISION audit entry records actorId underwriter1, the application ID, the reason code, the comment, previousStatus REFERRED, newStatus APPROVED and a timestamp")
    void approvalIsAuditedWithTheActorId() throws Exception {
        String id = referred();

        api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT));

        List<Map<String, Object>> audit = ApplicationRows.audit(jdbc, id);
        assertThat(audit).extracting(row -> row.get("ACTION"))
                .containsExactly("APPLICATION_SUBMITTED", "UNDERWRITING_AUTO_DECISION", "UNDERWRITING_DECISION");
        Map<String, Object> entry = audit.get(2);
        assertThat(entry.get("ACTOR_ID")).isEqualTo("underwriter1");
        assertThat(entry.get("ENTITY_TYPE")).isEqualTo("APPLICATION");
        assertThat(entry.get("ENTITY_ID")).isEqualTo(id);
        assertThat(entry.get("REASON_CODE")).isEqualTo("UW-MAN-001");
        assertThat(entry.get("COMMENT_TEXT")).isEqualTo(GOOD_COMMENT);
        assertThat(entry.get("PREVIOUS_STATUS")).isEqualTo("REFERRED");
        assertThat(entry.get("NEW_STATUS")).isEqualTo("APPROVED");
        assertThat(ApplicationRows.instant(entry.get("OCCURRED_AT"))).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("AC-09.2: the actor is the authenticated principal, so admin1 deciding records admin1")
    void actorIdComesFromThePrincipal() throws Exception {
        String id = referred();

        MvcResult result = api.decide("admin1", id, decisionBody("APPROVE", "UW-MAN-002", GOOD_COMMENT));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(ApplicationRows.audit(jdbc, id).get(2).get("ACTOR_ID")).isEqualTo("admin1");
        assertThat(ApplicationRows.decisions(jdbc, id).get(1).get("ACTOR_ID")).isEqualTo("admin1");
        assertThat(ApplicationRows.decisions(jdbc, id).get(1).get("REASON_CODES")).isEqualTo("UW-MAN-002");
    }

    @Test
    @DisplayName("AC-09.2: underwriter1 declines a REFERRED application with UW-MAN-010, the status becomes DECLINED and the rows record DECLINE")
    void underwriterDeclinesAReferredApplication() throws Exception {
        String id = referred();

        MvcResult result = api.decide("underwriter1", id, decisionBody("DECLINE", "UW-MAN-010", GOOD_COMMENT));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.bodyOf(result).get("status").asText()).isEqualTo("DECLINED");
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("DECLINED");
        assertThat(ApplicationRows.decisions(jdbc, id).get(1).get("OUTCOME")).isEqualTo("DECLINE");
        Map<String, Object> entry = ApplicationRows.audit(jdbc, id).get(2);
        assertThat(entry.get("PREVIOUS_STATUS")).isEqualTo("REFERRED");
        assertThat(entry.get("NEW_STATUS")).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("AC-09.2: the comment is trimmed before it is stored in the decision row and the audit entry")
    void commentIsTrimmed() throws Exception {
        String id = referred();

        api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", "   " + GOOD_COMMENT + "  \n"));

        assertThat(ApplicationRows.decisions(jdbc, id)).as("a MANUAL row was appended").hasSize(2);
        assertThat(ApplicationRows.audit(jdbc, id)).as("an audit entry was appended").hasSize(3);
        assertThat(ApplicationRows.decisions(jdbc, id).get(1).get("COMMENT_TEXT")).isEqualTo(GOOD_COMMENT);
        assertThat(ApplicationRows.audit(jdbc, id).get(2).get("COMMENT_TEXT")).isEqualTo(GOOD_COMMENT);
    }

    @Test
    @DisplayName("AC-09.2: a decided application leaves the queue, and its customer sees the new status beside the original MANUAL_REVIEW decision")
    void decidedApplicationLeavesTheQueue() throws Exception {
        String id = referred();
        assertThat(indexOf(api.bodyOf(api.queue("underwriter1", "")), id)).isNotNegative();

        api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT));

        assertThat(indexOf(api.bodyOf(api.queue("underwriter1", "")), id)).isNegative();
        JsonNode mine = api.bodyOf(api.mine("customer1"));
        JsonNode own = null;
        for (JsonNode application : mine) {
            if (id.equals(application.get("applicationId").asText())) {
                own = application;
            }
        }
        assertThat(own).isNotNull();
        assertThat(own.get("status").asText()).isEqualTo("APPROVED");
        assertThat(own.get("decision").asText()).isEqualTo("MANUAL_REVIEW");
        assertThat(texts(own.get("reasonCodes"))).containsExactly("UW-TL-002");
    }

    @Test
    @DisplayName("AC-09.4: a comment of exactly 10 characters is accepted, and so is one of 2000")
    void commentBoundariesAreAccepted() throws Exception {
        String shortest = referred();
        String longest = referred();

        assertThat(api.decide("underwriter1", shortest, decisionBody("APPROVE", "UW-MAN-001", "0123456789"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(api.decide("underwriter1", longest, decisionBody("APPROVE", "UW-MAN-001", "x".repeat(2000)))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(ApplicationRows.decisions(jdbc, longest).get(1).get("COMMENT_TEXT").toString()).hasSize(2000);
    }

    @Test
    @DisplayName("AC-09.4: a missing, null, blank, 9-character or 2001-character comment on the decision endpoint is a 400 naming comment, and nothing changes")
    void invalidCommentIsRejected() throws Exception {
        String id = referred();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);
        List<Object> comments = new ArrayList<>();
        comments.add(null);
        comments.add("");
        comments.add("          ");
        comments.add("123456789");
        comments.add("  123456789  ");
        comments.add("x".repeat(2001));

        for (Object comment : comments) {
            Map<String, Object> body = decisionBody("APPROVE", "UW-MAN-001", null);
            body.put("comment", comment);
            JsonNode problem = problemOf(api.decide("underwriter1", id, body), 400);
            assertThat(errorFields(problem)).as("comment of length %s", comment == null ? "null" : ((String) comment).length())
                    .containsExactly("comment");
        }
        Map<String, Object> missing = decisionBody("APPROVE", "UW-MAN-001", null);
        missing.remove("comment");
        assertThat(errorFields(problemOf(api.decide("underwriter1", id, missing), 400))).containsExactly("comment");
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.4: the comment problem never echoes the submitted text")
    void commentProblemDoesNotEchoTheComment() throws Exception {
        String id = referred();

        MvcResult result = api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", "too short"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("too short");
    }

    @Test
    @DisplayName("AC-09.4: a decision body with an unknown key, such as a forged actorId, is a 400 naming that key and nothing changes (KD-008)")
    void unknownKeyIsRejected() throws Exception {
        String id = referred();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);
        Map<String, Object> body = decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT);
        body.put("actorId", "admin1");

        JsonNode problem = problemOf(api.decide("underwriter1", id, body), 400);

        assertThat(errorFields(problem)).containsExactly("actorId");
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.4: a missing or unrecognised decision is a 400 naming decision, and nothing changes")
    void invalidDecisionValueIsRejected() throws Exception {
        String id = referred();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult unrecognised = api.decide("underwriter1", id, decisionBody("MAYBE", "UW-MAN-001", GOOD_COMMENT));
        Map<String, Object> missing = decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT);
        missing.remove("decision");
        MvcResult absent = api.decide("underwriter1", id, missing);

        assertThat(errorFields(problemOf(unrecognised, 400))).containsExactly("decision");
        assertThat(unrecognised.getResponse().getContentAsString()).doesNotContain("MAYBE");
        assertThat(errorFields(problemOf(absent, 400))).containsExactly("decision");
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.4: every problem of a bad body is reported together, sorted by field")
    void everyBodyProblemIsReportedTogether() throws Exception {
        String id = referred();

        JsonNode problem = problemOf(api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-010", "short")),
                400);

        assertThat(errorFields(problem)).containsExactly("comment", "reasonCode");
    }

    @ParameterizedTest(name = "AC-09.6: {0} with {1} on the decision endpoint is a 400 naming reasonCode and nothing changes")
    @CsvSource({
        "APPROVE,UW-MAN-010",
        "DECLINE,UW-MAN-001",
        "DECLINE,UW-MAN-002",
        "APPROVE,UW-OVR-001",
        "DECLINE,UW-OVR-002",
        "APPROVE,UW-MAN-999",
        "APPROVE,uw-man-001",
    })
    void unpairedReasonCodeIsRejected(String decision, String reasonCode) throws Exception {
        String id = referred();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = api.decide("underwriter1", id, decisionBody(decision, reasonCode, GOOD_COMMENT));

        JsonNode problem = problemOf(result, 400);
        assertThat(errorFields(problem)).containsExactly("reasonCode");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(reasonCode);
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.6: a missing reason code is a 400 naming reasonCode, and the paired codes are accepted: APPROVE with UW-MAN-001 or UW-MAN-002, DECLINE with UW-MAN-010")
    void missingReasonCodeIsRejectedAndPairedCodesAreAccepted() throws Exception {
        String id = referred();
        Map<String, Object> missing = decisionBody("APPROVE", null, GOOD_COMMENT);
        missing.remove("reasonCode");
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        assertThat(errorFields(problemOf(api.decide("underwriter1", id, missing), 400))).containsExactly("reasonCode");
        assertUnchanged(before, id);
        assertThat(api.decide("underwriter1", referred(), decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(api.decide("underwriter1", referred(), decisionBody("APPROVE", "UW-MAN-002", GOOD_COMMENT))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(api.decide("underwriter1", referred(), decisionBody("DECLINE", "UW-MAN-010", GOOD_COMMENT))
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("AC-09.5: a decision on an APPROVED or a DECLINED application is a 409 INVALID_APPLICATION_STATE with a fixed detail, and no row is appended")
    void decisionOnANonReferredApplicationIsAConflict() throws Exception {
        String approved = api.approvedApplication("customer1", TODAY);
        String declined = api.declinedApplication("customer1", TODAY);
        Snapshot approvedBefore = ApplicationRows.snapshot(jdbc, approved);
        Snapshot declinedBefore = ApplicationRows.snapshot(jdbc, declined);

        JsonNode first = problemOf(
                api.decide("underwriter1", approved, decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT)), 409);
        JsonNode second = problemOf(
                api.decide("underwriter1", declined, decisionBody("DECLINE", "UW-MAN-010", GOOD_COMMENT)), 409);

        assertThat(first.get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertThat(second.get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertThat(first.get("detail").asText()).isEqualTo(second.get("detail").asText())
                .doesNotContain(approved).doesNotContain("APPROVED");
        assertUnchanged(approvedBefore, approved);
        assertUnchanged(declinedBefore, declined);
    }

    @Test
    @DisplayName("AC-09.5: deciding the same application twice, the second request is a 409 and only one MANUAL row and one UNDERWRITING_DECISION entry exist")
    void secondDecisionOnTheSameApplicationIsAConflict() throws Exception {
        String id = referred();
        assertThat(api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT))
                .getResponse().getStatus()).isEqualTo(200);
        Snapshot afterFirst = ApplicationRows.snapshot(jdbc, id);

        MvcResult second = api.decide("underwriter1", id, decisionBody("DECLINE", "UW-MAN-010", GOOD_COMMENT));

        assertThat(problemOf(second, 409).get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertUnchanged(afterFirst, id);
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("AC-09.5: an unknown application ID is a 404 with a fixed detail that does not echo the ID, and a malformed ID is the same 404")
    void unknownOrMalformedApplicationIdIsNotFound() throws Exception {
        for (String id : List.of(UNKNOWN_ID, "not-an-application", "A-1", "x".repeat(300))) {
            MvcResult result = api.decide("underwriter1", id,
                    decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT));

            JsonNode problem = problemOf(result, 404);
            assertThat(problem.get("detail").asText()).isEqualTo("The requested resource was not found.");
            assertThat(result.getResponse().getContentAsString()).doesNotContain(id);
        }
    }

    @Test
    @DisplayName("AC-09.4: an invalid body is a 400 even for an unknown application ID, because body validation runs before the lookup (C3)")
    void validationComesBeforeTheLookup() throws Exception {
        MvcResult result = api.decide("underwriter1", UNKNOWN_ID, decisionBody("APPROVE", "UW-MAN-010", GOOD_COMMENT));

        assertThat(errorFields(problemOf(result, 400))).containsExactly("reasonCode");
    }

    @Test
    @DisplayName("AC-09.5: an invalid body is a 400 even for an APPROVED application, because body validation runs before the state check (C3)")
    void validationComesBeforeTheStateCheck() throws Exception {
        String approved = api.approvedApplication("customer1", TODAY);
        Snapshot before = ApplicationRows.snapshot(jdbc, approved);

        MvcResult result = api.decide("underwriter1", approved, decisionBody("APPROVE", "UW-MAN-010", GOOD_COMMENT));

        assertThat(errorFields(problemOf(result, 400))).containsExactly("reasonCode");
        assertUnchanged(before, approved);
    }

    @Test
    @DisplayName("AC-09.5: a decision that loses the optimistic-lock race is a 409 INVALID_APPLICATION_STATE and appends nothing (U-9)")
    void staleVersionIsAConflict() throws Exception {
        String id = referred();
        TransactionTemplate tx = new TransactionTemplate(transactions);

        // One transaction plays both sides: it loads the application at version N, then another writer commits
        // (the version moves on), and only then does the decision run. Everything is rolled back at the end.
        MvcResult result = tx.execute(status -> {
            applications.findByApplicationId(id).orElseThrow();
            jdbc.update("update application set version = version + 1 where application_id = ?", id);
            try {
                return api.decide("underwriter1", id, decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                status.setRollbackOnly();
            }
        });

        JsonNode problem = problemOf(result, 409);
        assertThat(problem.get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("REFERRED");
        assertThat(ApplicationRows.decisions(jdbc, id)).hasSize(1);
        assertThat(ApplicationRows.audit(jdbc, id)).hasSize(2);
    }

    @Test
    @DisplayName("AC-09.5: of two simultaneous decisions on one application exactly one succeeds, the other is a 409, and one MANUAL row and one audit entry are written (U-9)")
    void simultaneousDecisionsOnlyOneWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                String id = referred();
                CyclicBarrier start = new CyclicBarrier(2);
                List<Future<Integer>> answers = new ArrayList<>();
                for (Map<String, Object> body : List.of(decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT),
                        decisionBody("DECLINE", "UW-MAN-010", GOOD_COMMENT))) {
                    answers.add(pool.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return api.decide("underwriter1", id, body).getResponse().getStatus();
                    }));
                }
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> answer : answers) {
                    statuses.add(answer.get(30, TimeUnit.SECONDS));
                }

                assertThat(statuses).as("round %s", round).containsExactlyInAnyOrder(200, 409);
                assertThat(ApplicationRows.decisions(jdbc, id)).extracting(row -> row.get("SOURCE"))
                        .containsExactly("RULES", "MANUAL");
                assertThat(ApplicationRows.audit(jdbc, id)).extracting(row -> row.get("ACTION"))
                        .containsExactly("APPLICATION_SUBMITTED", "UNDERWRITING_AUTO_DECISION",
                                "UNDERWRITING_DECISION");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("NFR-04.3: a text/plain POST to the decision endpoint with a valid JSON body is a 415 and nothing changes")
    void textPlainDecisionIsUnsupportedMediaType() throws Exception {
        String id = referred();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = mvc.perform(post("/api/underwriting/{id}/decision", id)
                .with(httpBasic("underwriter1", ApplicationApi.PASSWORD))
                .contentType("text/plain")
                .content(json.writeValueAsString(decisionBody("APPROVE", "UW-MAN-001", GOOD_COMMENT)))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("NFR-04.3: a form-encoded POST to the decision endpoint is a 415 and nothing changes")
    void formEncodedDecisionIsUnsupportedMediaType() throws Exception {
        String id = referred();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = mvc.perform(post("/api/underwriting/{id}/decision", id)
                .with(httpBasic("underwriter1", ApplicationApi.PASSWORD))
                .contentType("application/x-www-form-urlencoded")
                .param("decision", "APPROVE").param("reasonCode", "UW-MAN-001").param("comment", GOOD_COMMENT))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertUnchanged(before, id);
    }

    /** A body that is not an object is refused as unreadable, with the fixed text and no echo (never a 500). */
    @Test
    @DisplayName("AC-09.4: a decision body that is not a JSON object is a 400 with a fixed detail and nothing changes")
    void unreadableBodyIsRejected() throws Exception {
        String id = referred();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);
        Map<String, Object> wrongType = new LinkedHashMap<>();
        wrongType.put("decision", "APPROVE");
        wrongType.put("reasonCode", "UW-MAN-001");
        wrongType.put("comment", Map.of("text", "nested"));

        MvcResult nested = api.decide("underwriter1", id, wrongType);
        MvcResult array = mvc.perform(post("/api/underwriting/{id}/decision", id)
                .with(httpBasic("underwriter1", ApplicationApi.PASSWORD))
                .contentType("application/json").content("[1,2]")).andReturn();

        assertThat(problemOf(nested, 400).get("detail").asText()).doesNotContain("nested");
        assertThat(problemOf(array, 400).get("detail").asText()).isEqualTo("The request could not be read.");
        assertUnchanged(before, id);
    }
}
