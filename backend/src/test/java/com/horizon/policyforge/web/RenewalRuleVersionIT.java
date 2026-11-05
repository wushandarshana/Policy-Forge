package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.EndorsementApi;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.PolicyApi;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalApi;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.math.BigDecimal;
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
 * AC-07.3: the renewal premium is refreshed with the rule version active on the run date, while the current term keeps
 * the version it was rated with. The v2 rule set is a test fixture (plan G-1) that this test publishes in its own
 * application context, which is discarded after the test, so no other test sees it and no rule file is added. It is v1
 * with the age band 31 to 40 at factor 1.30 and {@code effectiveFrom} 2027-09-01, so the AC-04.1 profile (age 35,
 * 5,000,000.00) renews at 7400.00 (5000 x 1.30 x 1.00 x 1.10 + 250.00) while term 1 and version 1 keep 6850.00.
 */
@Tag("AC-07")
@SpringBootTest(properties = "policyforge.test.context=renewal-rule-version-isolated")
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RenewalRuleVersionIT {

    private static final LocalDate ISSUE_DAY = LocalDate.parse("2026-11-01");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private static void assertMoney(Object actual, String expected) {
        assertThat(((BigDecimal) actual).toPlainString()).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-07.3: with TERM_LIFE v2 (effective 2027-09-01, higher rates) active, the 2027-10-01 run offers the renewal at 7400.00 and rule version 2, term 1 and version 1 keep 6850.00 at rule version 1, and after the payment version 2 has 7400.00 at rule version 2")
    void theRenewalUsesTheRuleVersionActiveOnTheRunDate() throws Exception {
        clock.setDate(ISSUE_DAY);
        EndorsementApi endorsements = new EndorsementApi(mvc, json);
        RenewalApi renewals = new RenewalApi(mvc, json);
        PolicyApi policies = new PolicyApi(mvc, json);
        try {
            String number = endorsements.issuePolicy("customer1", "TERM_LIFE", ISSUE_DAY);
            assertThat(((Number) PolicyRows.versions(jdbc, number).get(0).get("RULE_VERSION")).intValue()).isEqualTo(1);

            MvcResult published = mvc.perform(post("/api/products/TERM_LIFE/versions")
                    .with(httpBasic("admin1", ApplicationApi.PASSWORD))
                    .contentType("application/json")
                    .content(RuleSetFixtures.termLifeV2BandFactor130("2027-09-01"))).andReturn();
            assertThat(published.getResponse().getStatus()).as(published.getResponse().getContentAsString())
                    .isEqualTo(201);

            clock.setDate(LocalDate.parse("2027-10-01"));
            JsonNode run = renewals.jobOk("2027-10-01");

            assertThat(texts(run.get("offersCreated"))).contains(number);
            List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
            assertThat(terms).hasSize(2);
            assertMoney(terms.get(1).get("PREMIUM"), "7400.00");
            assertMoney(terms.get(0).get("PREMIUM"), "6850.00");
            JsonNode detail = policies.bodyOf(policies.detail("customer1", number));
            assertThat(detail.get("terms").get(1).path("ruleVersion").asInt()).as("the renewal term records v2")
                    .isEqualTo(2);
            assertThat(detail.get("terms").get(0).path("ruleVersion").asInt()).as("term 1 keeps v1").isEqualTo(1);
            assertThat(detail.path("pendingRenewal").path("premium").asText()).isEqualTo("7400.00");
            assertThat(detail.path("pendingRenewal").path("ruleVersion").asInt()).isEqualTo(2);
            assertThat(detail.get("policy").get("annualPremium").asText()).as("the policy is still on version 1")
                    .isEqualTo("6850.00");
            List<Map<String, Object>> versionsBefore = PolicyRows.versions(jdbc, number);
            assertThat(versionsBefore).hasSize(1);
            assertMoney(versionsBefore.get(0).get("ANNUAL_PREMIUM"), "6850.00");
            assertThat(((Number) versionsBefore.get(0).get("RULE_VERSION")).intValue()).isEqualTo(1);

            MvcResult paid = renewals.pay("customer1", number, Map.of());
            assertThat(paid.getResponse().getStatus()).as(paid.getResponse().getContentAsString()).isEqualTo(200);

            List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
            assertThat(versions).hasSize(2);
            assertMoney(versions.get(1).get("ANNUAL_PREMIUM"), "7400.00");
            assertThat(((Number) versions.get(1).get("RULE_VERSION")).intValue()).isEqualTo(2);
            assertMoney(versions.get(0).get("ANNUAL_PREMIUM"), "6850.00");
            assertThat(((Number) versions.get(0).get("RULE_VERSION")).intValue()).as("version 1 is untouched")
                    .isEqualTo(1);
            JsonNode renewed = renewals.bodyOf(paid);
            assertThat(renewed.get("policy").get("annualPremium").asText()).isEqualTo("7400.00");
            assertThat(renewed.get("policy").get("ruleVersion").asInt()).isEqualTo(2);
            assertThat(renewed.get("term").get("ruleVersion").asInt()).isEqualTo(2);
        } finally {
            clock.setDate(FixedClockConfig.TODAY);
        }
    }
}
