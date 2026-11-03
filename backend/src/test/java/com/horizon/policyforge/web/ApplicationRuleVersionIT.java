package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC-04.5 through the application API: underwriting uses the rule version active at application time (U-5). The v2 rule
 * set is a test fixture (U-15, P-7) that this test publishes in its own application context, which is discarded after
 * the test, so every other integration test still sees v1 as the active version.
 */
@Tag("AC-04")
@SpringBootTest(properties = "policyforge.test.context=rule-version-isolated")
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationRuleVersionIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("AC-04.5: after a v2 rule set with the UW-TL-001 threshold at 65 is active, an age-62 application is not DECLINED and its stored decision references rule version 2")
    void applicationIsUnderwrittenWithTheVersionActiveAtApplicationTime() throws Exception {
        clock.setDate(FixedClockConfig.TODAY);
        ApplicationApi api = new ApplicationApi(mvc, json);
        LocalDate dob = LocalDate.now(clock).minusYears(62);
        Map<String, Object> age62 = termLifeInputs(62, false, "5000000.00", "24", List.of());
        String beforeV2 = api.quoteId("TERM_LIFE", age62);
        String afterV2 = api.quoteId("TERM_LIFE", age62);

        JsonNode declined = api.applyOk("customer1", beforeV2, kyc(dob));
        assertThat(declined.get("status").asText()).as("under v1, age 62 is declined").isEqualTo("DECLINED");
        assertThat(texts(declined.get("reasonCodes"))).containsExactly("UW-TL-001");
        assertThat(declined.get("ruleVersion").asInt()).isEqualTo(1);

        MvcResult published = mvc.perform(post("/api/products/TERM_LIFE/versions")
                .with(httpBasic("admin1", ApplicationApi.PASSWORD))
                .contentType("application/json")
                .content(RuleSetFixtures.termLifeV2AgeThreshold65("2026-10-28"))).andReturn();
        assertThat(published.getResponse().getStatus()).as(published.getResponse().getContentAsString())
                .isEqualTo(201);

        JsonNode created = api.applyOk("customer1", afterV2, kyc(dob));

        assertThat(created.get("decision").asText()).isNotEqualTo("DECLINE");
        assertThat(created.get("status").asText()).isNotEqualTo("DECLINED");
        assertThat(created.get("ruleVersion").asInt()).isEqualTo(2);
        String applicationId = created.get("applicationId").asText();
        Map<String, Object> row = jdbc.queryForMap(
                "select source, rule_version, outcome from underwriting_decision where application_id = ?",
                applicationId);
        assertThat(row.get("SOURCE")).isEqualTo("RULES");
        assertThat(((Number) row.get("RULE_VERSION")).intValue()).isEqualTo(2);
        assertThat(row.get("OUTCOME")).isNotEqualTo("DECLINE");
        assertThat(jdbc.queryForObject(
                "select rule_version from underwriting_decision where application_id = ?", Integer.class,
                declined.get("applicationId").asText())).as("the earlier decision is not rewritten").isEqualTo(1);
    }
}
