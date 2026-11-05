package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.householdInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static com.horizon.policyforge.support.ApplicationFixtures.motorInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The underwriting rules through the application API (AC-04.1 to AC-04.4): the status, the decision and the exact reason
 * codes of each v1 profile, and the stored {@code RULES} decision row (U-9) at rule version 1 with actor {@code system}.
 */
@Tag("AC-04")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class ApplicationUnderwritingIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ApplicationApi api;

    @BeforeEach
    void setUp() {
        clock.setDate(FixedClockConfig.TODAY);
        api = new ApplicationApi(mvc, json);
    }

    private LocalDate dobForAge(int age) {
        return LocalDate.now(clock).minusYears(age);
    }

    /** Applies as customer1 and checks the response and the stored RULES decision row. */
    private void assertUnderwritten(String productCode, Map<String, Object> inputs, int age, String status,
            String decision, List<String> reasonCodes) throws Exception {
        String quoteId = api.quoteId(productCode, inputs);

        JsonNode created = api.applyOk("customer1", quoteId, kyc(dobForAge(age)));

        assertThat(created.get("status").asText()).isEqualTo(status);
        assertThat(created.get("decision").asText()).isEqualTo(decision);
        assertThat(texts(created.get("reasonCodes"))).containsExactlyElementsOf(reasonCodes);
        assertThat(created.get("ruleVersion").asInt()).isEqualTo(1);
        JsonNode reasons = created.get("reasons");
        assertThat(reasons).hasSameSizeAs(reasonCodes);
        for (int i = 0; i < reasonCodes.size(); i++) {
            assertThat(reasons.get(i).get("code").asText()).isEqualTo(reasonCodes.get(i));
            assertThat(reasons.get(i).get("description").asText()).isNotBlank();
        }

        String applicationId = created.get("applicationId").asText();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select source, outcome, reason_codes, rule_version, actor_id, comment_text"
                        + " from underwriting_decision where application_id = ? order by id", applicationId);
        assertThat(rows).as("one RULES decision row").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("SOURCE")).isEqualTo("RULES");
        assertThat(row.get("OUTCOME")).isEqualTo(decision);
        assertThat(row.get("REASON_CODES")).isEqualTo(String.join(",", reasonCodes));
        assertThat(((Number) row.get("RULE_VERSION")).intValue()).isEqualTo(1);
        assertThat(row.get("ACTOR_ID")).isEqualTo("system");
        assertThat(row.get("COMMENT_TEXT")).isNull();
        String storedStatus = jdbc.queryForObject("select status from application where application_id = ?",
                String.class, applicationId);
        assertThat(storedStatus).isEqualTo(status);
    }

    @Test
    @DisplayName("AC-04.1: TERM_LIFE age 35, non-smoker, BMI 24, no declarations and sum insured 5,000,000.00 is APPROVED with AUTO_BIND and [UW-OK]")
    void cleanTermLifeIsAutoBound() throws Exception {
        assertUnderwritten("TERM_LIFE", cleanTermLifeInputs(), 35, "APPROVED", "AUTO_BIND", List.of("UW-OK"));
    }

    @Test
    @DisplayName("AC-04.1: the UW-OK reason carries the description \"No underwriting rule fired\" (U-8)")
    void autoBindReasonHasItsDescription() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());

        JsonNode created = api.applyOk("customer1", quoteId, kyc(dobForAge(35)));

        assertThat(created.get("reasons").get(0).get("description").asText())
                .isEqualTo("No underwriting rule fired");
    }

    @Test
    @DisplayName("AC-04.2: a TERM_LIFE smoker with sum insured 12,000,000.00 is REFERRED with MANUAL_REVIEW and [UW-TL-002]")
    void smokerWithLargeSumInsuredIsReferred() throws Exception {
        assertUnderwritten("TERM_LIFE", termLifeInputs(40, true, "12000000.00", "24", List.of()), 40, "REFERRED",
                "MANUAL_REVIEW", List.of("UW-TL-002"));
    }

    @Test
    @DisplayName("AC-04.3: TERM_LIFE age 62 and BMI 37 is DECLINED with DECLINE and [UW-TL-001, UW-TL-004]: decline beats refer and every fired code is listed")
    void declineBeatsRefer() throws Exception {
        assertUnderwritten("TERM_LIFE", termLifeInputs(62, false, "5000000.00", "37", List.of()), 62, "DECLINED",
                "DECLINE", List.of("UW-TL-001", "UW-TL-004"));
    }

    @Test
    @DisplayName("AC-04.4: a MOTOR quote with 3 claims is DECLINED with [UW-MO-001]")
    void motorWithThreeClaimsIsDeclined() throws Exception {
        assertUnderwritten("MOTOR", motorInputs(30, 3), 30, "DECLINED", "DECLINE", List.of("UW-MO-001"));
    }

    @Test
    @DisplayName("AC-04.4: a HOUSEHOLD quote with TIMBER construction is REFERRED with MANUAL_REVIEW and [UW-HH-003]")
    void timberHouseholdIsReferred() throws Exception {
        assertUnderwritten("HOUSEHOLD", householdInputs("TIMBER"), 50, "REFERRED", "MANUAL_REVIEW",
                List.of("UW-HH-003"));
    }

    @Test
    @DisplayName("AC-04.4: the audit entry of the automatic decision records the outcome status, with actor system")
    void autoDecisionAuditEntryNamesTheOutcomeStatus() throws Exception {
        String quoteId = api.quoteId("MOTOR", motorInputs(30, 3));

        String applicationId = api.applyOk("customer1", quoteId, kyc(dobForAge(30))).get("applicationId").asText();

        Map<String, Object> decided = jdbc.queryForMap(
                "select actor_id, previous_status, new_status from audit_entry"
                        + " where entity_type = 'APPLICATION' and entity_id = ? and action = 'UNDERWRITING_AUTO_DECISION'",
                applicationId);
        assertThat(decided.get("ACTOR_ID")).isEqualTo("system");
        assertThat(decided.get("PREVIOUS_STATUS")).isEqualTo("UNDERWRITING");
        assertThat(decided.get("NEW_STATUS")).isEqualTo("DECLINED");
    }
}
