package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.decisionBody;
import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationApi.indexOf;
import static com.horizon.policyforge.support.ApplicationApi.overrideBody;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * The admin override endpoint {@code POST /api/underwriting/{applicationId}/override} (AC-09.3, AC-09.4, AC-09.5 and
 * AC-09.6; contracts C3 and C5; U-9, U-10, U-12). Every refused request is followed by a check that the status, the
 * decision rows and the audit entries are exactly as before.
 */
@Tag("AC-09")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class UnderwritingOverrideIT {

    private static final LocalDate TODAY = FixedClockConfig.TODAY;
    private static final String UNKNOWN_ID = "A-00000000-0000-4000-8000-000000000000";
    private static final String COMMENT = "Commercial exception signed off";

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

    private String declined() throws Exception {
        return api.declinedApplication("customer1", TODAY);
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
    @DisplayName("AC-09.3: admin1 overrides a DECLINED application with UW-OVR-001 and the comment \"Commercial exception signed off\", and the status becomes APPROVED")
    void adminOverridesADeclinedApplication() throws Exception {
        String id = declined();

        MvcResult result = api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode body = api.bodyOf(result);
        assertThat(fieldNames(body)).containsExactly("applicationId", "previousStatus", "status");
        assertThat(body.get("applicationId").asText()).isEqualTo(id);
        assertThat(body.get("previousStatus").asText()).isEqualTo("DECLINED");
        assertThat(body.get("status").asText()).isEqualTo("APPROVED");
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("AC-09.3: an OVERRIDE decision row is appended and the original RULES DECLINE row stays present and unchanged")
    void overrideAppendsARowAndKeepsTheOriginalDecision() throws Exception {
        String id = declined();
        Map<String, Object> originalRow = ApplicationRows.decisions(jdbc, id).get(0);

        api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));

        List<Map<String, Object>> rows = ApplicationRows.decisions(jdbc, id);
        assertThat(rows).extracting(row -> row.get("SOURCE")).containsExactly("RULES", "OVERRIDE");
        assertThat(rows.get(0)).isEqualTo(originalRow);
        assertThat(rows.get(0).get("OUTCOME")).isEqualTo("DECLINE");
        assertThat(rows.get(0).get("REASON_CODES")).isEqualTo("UW-TL-001,UW-TL-004");
        assertThat(rows.get(0).get("ACTOR_ID")).isEqualTo("system");
        Map<String, Object> override = rows.get(1);
        assertThat(override.get("OUTCOME")).isEqualTo("APPROVE");
        assertThat(override.get("REASON_CODES")).isEqualTo("UW-OVR-001");
        assertThat(override.get("ACTOR_ID")).isEqualTo("admin1");
        assertThat(override.get("COMMENT_TEXT")).isEqualTo(COMMENT);
        assertThat(((Number) override.get("RULE_VERSION")).intValue()).isEqualTo(1);
        assertThat(override.get("REASONS_JSON").toString()).contains("UW-OVR-001")
                .contains("Commercial exception approved");
        assertThat(ApplicationRows.instant(override.get("DECIDED_AT"))).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("AC-09.3: an UNDERWRITING_OVERRIDE audit entry records actorId admin1, the reason code, the comment, previousStatus DECLINED, newStatus APPROVED and a timestamp")
    void overrideIsAuditedWithTheActorId() throws Exception {
        String id = declined();

        api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));

        List<Map<String, Object>> audit = ApplicationRows.audit(jdbc, id);
        assertThat(audit).extracting(row -> row.get("ACTION"))
                .containsExactly("APPLICATION_SUBMITTED", "UNDERWRITING_AUTO_DECISION", "UNDERWRITING_OVERRIDE");
        Map<String, Object> entry = audit.get(2);
        assertThat(entry.get("ACTOR_ID")).isEqualTo("admin1");
        assertThat(entry.get("ENTITY_TYPE")).isEqualTo("APPLICATION");
        assertThat(entry.get("ENTITY_ID")).isEqualTo(id);
        assertThat(entry.get("REASON_CODE")).isEqualTo("UW-OVR-001");
        assertThat(entry.get("COMMENT_TEXT")).isEqualTo(COMMENT);
        assertThat(entry.get("PREVIOUS_STATUS")).isEqualTo("DECLINED");
        assertThat(entry.get("NEW_STATUS")).isEqualTo("APPROVED");
        assertThat(ApplicationRows.instant(entry.get("OCCURRED_AT"))).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("AC-09.3: UW-OVR-002 is accepted too, and the comment is trimmed before it is stored")
    void secondOverrideCodeIsAccepted() throws Exception {
        String id = declined();

        MvcResult result = api.override("admin1", id, overrideBody("UW-OVR-002", "  " + COMMENT + " \n"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(ApplicationRows.decisions(jdbc, id).get(1).get("REASON_CODES")).isEqualTo("UW-OVR-002");
        assertThat(ApplicationRows.decisions(jdbc, id).get(1).get("COMMENT_TEXT")).isEqualTo(COMMENT);
        assertThat(ApplicationRows.audit(jdbc, id).get(2).get("COMMENT_TEXT")).isEqualTo(COMMENT);
    }

    @Test
    @DisplayName("AC-09.3: an application an underwriter declined by hand can be overridden too, and all three decision rows are kept")
    void manuallyDeclinedApplicationCanBeOverridden() throws Exception {
        String id = api.referredApplication("customer1", TODAY);
        api.decide("underwriter1", id, decisionBody("DECLINE", "UW-MAN-010", "Risk is outside our appetite"));

        MvcResult result = api.override("admin1", id, overrideBody("UW-OVR-002", COMMENT));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(ApplicationRows.decisions(jdbc, id)).extracting(row -> row.get("SOURCE"))
                .containsExactly("RULES", "MANUAL", "OVERRIDE");
    }

    @Test
    @DisplayName("AC-09.3: an overridden application leaves the admin's declined list")
    void overriddenApplicationLeavesTheDeclinedList() throws Exception {
        String id = declined();
        assertThat(indexOf(api.bodyOf(api.queue("admin1", "includeDeclined=true")), id)).isNotNegative();

        api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));

        assertThat(indexOf(api.bodyOf(api.queue("admin1", "includeDeclined=true")), id)).isNegative();
    }

    @Test
    @DisplayName("AC-09.4: underwriter1 calling override gets 403 and nothing changes")
    void underwriterCannotOverride() throws Exception {
        String id = declined();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = api.override("underwriter1", id, overrideBody("UW-OVR-001", COMMENT));

        assertThat(problemOf(result, 403).get("detail").asText()).doesNotContain(id);
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.4: a CUSTOMER calling override gets 403 and nothing changes")
    void customerCannotOverride() throws Exception {
        String id = declined();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = api.override("customer1", id, overrideBody("UW-OVR-001", COMMENT));

        problemOf(result, 403);
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.4: admin1 with a missing, null, blank, 9-character or 2001-character comment gets 400 naming comment, and nothing changes")
    void invalidCommentIsRejected() throws Exception {
        String id = declined();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);
        List<String> comments = new ArrayList<>();
        comments.add(null);
        comments.add("");
        comments.add("         ");
        comments.add("123456789");
        comments.add("x".repeat(2001));

        for (String comment : comments) {
            JsonNode problem = problemOf(api.override("admin1", id, overrideBody("UW-OVR-001", comment)), 400);
            assertThat(errorFields(problem)).containsExactly("comment");
        }
        Map<String, Object> missing = overrideBody("UW-OVR-001", null);
        missing.remove("comment");
        assertThat(errorFields(problemOf(api.override("admin1", id, missing), 400))).containsExactly("comment");
        assertUnchanged(before, id);
    }

    @ParameterizedTest(name = "AC-09.6: the code {0} on the override endpoint is a 400 naming reasonCode and nothing changes")
    @ValueSource(strings = {"UW-MAN-001", "UW-MAN-002", "UW-MAN-010", "UW-OVR-003", "uw-ovr-001"})
    void onlyOverrideCodesAreAccepted(String reasonCode) throws Exception {
        String id = declined();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = api.override("admin1", id, overrideBody(reasonCode, COMMENT));

        assertThat(errorFields(problemOf(result, 400))).containsExactly("reasonCode");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(reasonCode);
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.6: a missing reason code on the override endpoint is a 400 naming reasonCode")
    void missingReasonCodeIsRejected() throws Exception {
        String id = declined();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);
        Map<String, Object> body = overrideBody(null, COMMENT);
        body.remove("reasonCode");

        assertThat(errorFields(problemOf(api.override("admin1", id, body), 400))).containsExactly("reasonCode");
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.4: an override body with an unknown key, such as a forged actorId or decision, is a 400 naming each key and nothing changes (KD-008)")
    void unknownKeysAreRejected() throws Exception {
        String id = declined();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);
        Map<String, Object> body = overrideBody("UW-OVR-001", COMMENT);
        body.put("decision", "APPROVE");
        body.put("actorId", "admin1");

        JsonNode problem = problemOf(api.override("admin1", id, body), 400);

        assertThat(errorFields(problem)).containsExactly("actorId", "decision");
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.5: an override on an APPROVED application is a 409 INVALID_APPLICATION_STATE with a fixed detail, the status is unchanged and no decision or audit row is appended")
    void overrideOnAnApprovedApplicationIsAConflict() throws Exception {
        String id = api.approvedApplication("customer1", TODAY);
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));

        JsonNode problem = problemOf(result, 409);
        assertThat(problem.get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertThat(problem.get("detail").asText()).doesNotContain(id).doesNotContain("APPROVED");
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("APPROVED");
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.5: an override on a REFERRED application is a 409 too, because only a DECLINED application can be overridden")
    void overrideOnAReferredApplicationIsAConflict() throws Exception {
        String id = api.referredApplication("customer1", TODAY);
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));

        assertThat(problemOf(result, 409).get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertUnchanged(before, id);
    }

    @Test
    @DisplayName("AC-09.5: overriding the same application twice, the second request is a 409 and only one OVERRIDE row exists")
    void secondOverrideIsAConflict() throws Exception {
        String id = declined();
        assertThat(api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT)).getResponse().getStatus())
                .isEqualTo(200);
        Snapshot afterFirst = ApplicationRows.snapshot(jdbc, id);

        MvcResult second = api.override("admin1", id, overrideBody("UW-OVR-002", COMMENT));

        assertThat(problemOf(second, 409).get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertUnchanged(afterFirst, id);
    }

    @Test
    @DisplayName("AC-09.5: an unknown or malformed application ID on override is a 404 with a fixed detail that does not echo the ID")
    void unknownApplicationIdIsNotFound() throws Exception {
        for (String id : List.of(UNKNOWN_ID, "not-an-application", "x".repeat(300))) {
            MvcResult result = api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));

            JsonNode problem = problemOf(result, 404);
            assertThat(problem.get("detail").asText()).isEqualTo("The requested resource was not found.");
            assertThat(result.getResponse().getContentAsString()).doesNotContain(id);
        }
    }

    @Test
    @DisplayName("AC-09.4: an invalid override body is a 400 even for an unknown application ID, because body validation runs before the lookup (C3)")
    void validationComesBeforeTheLookup() throws Exception {
        MvcResult result = api.override("admin1", UNKNOWN_ID, overrideBody("UW-OVR-001", "short"));

        assertThat(errorFields(problemOf(result, 400))).containsExactly("comment");
    }

    @Test
    @DisplayName("AC-09.5: an invalid override body is a 400 even for an APPROVED application, because body validation runs before the state check (C3)")
    void validationComesBeforeTheStateCheck() throws Exception {
        String approved = api.approvedApplication("customer1", TODAY);
        Snapshot before = ApplicationRows.snapshot(jdbc, approved);

        MvcResult invalid = api.override("admin1", approved, overrideBody("UW-OVR-001", "short"));

        assertThat(errorFields(problemOf(invalid, 400))).containsExactly("comment");
        assertUnchanged(before, approved);
    }

    @Test
    @DisplayName("AC-09.5: an override that loses the optimistic-lock race is a 409 INVALID_APPLICATION_STATE and appends nothing (U-9)")
    void staleVersionIsAConflict() throws Exception {
        String id = declined();
        TransactionTemplate tx = new TransactionTemplate(transactions);

        MvcResult result = tx.execute(status -> {
            applications.findByApplicationId(id).orElseThrow();
            jdbc.update("update application set version = version + 1 where application_id = ?", id);
            try {
                return api.override("admin1", id, overrideBody("UW-OVR-001", COMMENT));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                status.setRollbackOnly();
            }
        });

        assertThat(problemOf(result, 409).get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("DECLINED");
        assertThat(ApplicationRows.decisions(jdbc, id)).hasSize(1);
        assertThat(ApplicationRows.audit(jdbc, id)).hasSize(2);
    }

    @Test
    @DisplayName("NFR-04.3: a text/plain POST to the override endpoint with a valid JSON body is a 415 and nothing changes")
    void textPlainOverrideIsUnsupportedMediaType() throws Exception {
        String id = declined();
        Snapshot before = ApplicationRows.snapshot(jdbc, id);

        MvcResult result = mvc.perform(post("/api/underwriting/{id}/override", id)
                .with(httpBasic("admin1", ApplicationApi.PASSWORD))
                .contentType("text/plain")
                .content(json.writeValueAsString(overrideBody("UW-OVR-001", COMMENT)))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertUnchanged(before, id);
    }
}
