package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.EndorsementApi;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.time.LocalDate;
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
 * AC-06.2: a sum-insured endorsement re-rates with the rule version the policy was priced with, never the latest. The
 * v2 rule set is a test fixture that this test publishes in its own application context, which is discarded after the
 * test, so every other integration test still sees v1 as the active version. v2 is v1 with a policy fee of 1000.00
 * instead of 250.00, so a new quote of the AC-04.1 profile is 7600.00 while the policy's own version still gives 9490.00.
 */
@Tag("AC-06")
@SpringBootTest(properties = "policyforge.test.context=endorsement-rule-version-isolated")
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EndorsementRuleVersionIT {

    private static final LocalDate ISSUE_DAY = LocalDate.parse("2026-11-01");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("AC-06.2: while a newer TERM_LIFE rule version is active, a sum-insured endorsement of a v1 policy still gives new premium 9490.00 and adjustment 1330.85, and version 2 keeps rule version 1")
    void endorsementUsesThePoliciesOwnRuleVersion() throws Exception {
        clock.setDate(ISSUE_DAY);
        ApplicationApi applications = new ApplicationApi(mvc, json);
        EndorsementApi endorsements = new EndorsementApi(mvc, json);
        try {
            String number = endorsements.issuePolicy("customer1", "TERM_LIFE", ISSUE_DAY);
            assertThat(((Number) PolicyRows.versions(jdbc, number).get(0).get("RULE_VERSION")).intValue()).isEqualTo(1);

            MvcResult published = mvc.perform(post("/api/products/TERM_LIFE/versions")
                    .with(httpBasic("admin1", ApplicationApi.PASSWORD))
                    .contentType("application/json")
                    .content(RuleSetFixtures.termLifeV2PolicyFee1000("2026-11-01"))).andReturn();
            assertThat(published.getResponse().getStatus()).as(published.getResponse().getContentAsString())
                    .isEqualTo(201);
            JsonNode newQuote = applications.createQuote("TERM_LIFE", cleanTermLifeInputs());
            assertThat(newQuote.get("ruleVersion").asInt()).as("v2 is now the active version").isEqualTo(2);
            assertThat(newQuote.get("annualPremium").asText()).as("and it prices differently").isEqualTo("7600.00");

            JsonNode created = endorsements.endorseOk(number, sumInsuredBody("7000000.00", "2027-05-01"));

            assertThat(created.get("endorsement").get("newAnnualPremium").asText()).isEqualTo("9490.00");
            assertThat(created.get("endorsement").get("adjustment").asText()).isEqualTo("1330.85");
            assertThat(created.get("policy").get("ruleVersion").asInt()).isEqualTo(1);
            assertThat(PolicyRows.versions(jdbc, number).stream()
                    .map(version -> ((Number) version.get("RULE_VERSION")).intValue()).toList())
                    .as("every version of the policy keeps rule version 1").containsExactly(1, 1);
            JsonNode preview = endorsements.previewOk(number, sumInsuredBody("5000000.00", "2027-05-01"));
            assertThat(preview.get("newAnnualPremium").asText()).isEqualTo("6850.00");
        } finally {
            clock.setDate(FixedClockConfig.TODAY);
        }
    }
}
