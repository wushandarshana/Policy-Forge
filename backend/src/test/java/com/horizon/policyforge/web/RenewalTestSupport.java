package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.EndorsementApi;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.PolicyApi;
import com.horizon.policyforge.support.RenewalApi;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared set-up of the renewal integration tests. The subclasses share one application context of their own (the marker
 * property keeps the older movable-clock tests out of it), because the cycle job lapses every in-force policy it finds
 * and the other tests must not meet those. The clock stands at 2026-11-01 (the AC-04.1 issue date), so a policy issued
 * here runs 2026-11-01 to 2027-10-31, and every test puts the clock back. Rows are found by policy number, and
 * "nothing is written" is proved with counts taken before and after.
 */
@SpringBootTest(properties = "policyforge.test.context=renewal")
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
abstract class RenewalTestSupport {

    protected static final LocalDate ISSUE_DAY = LocalDate.parse("2026-11-01");
    protected static final String NOT_FOUND_DETAIL = "The requested resource was not found.";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    protected ApplicationApi applications;
    protected PolicyApi policies;
    protected EndorsementApi endorsements;
    protected RenewalApi renewals;

    @BeforeEach
    void setUpRenewalSupport() {
        clock.setDate(ISSUE_DAY);
        applications = new ApplicationApi(mvc, json);
        policies = new PolicyApi(mvc, json);
        endorsements = new EndorsementApi(mvc, json);
        renewals = new RenewalApi(mvc, json);
    }

    @AfterEach
    void putTheClockBack() {
        clock.setDate(FixedClockConfig.TODAY);
    }

    /** A new ACTIVE policy of customer1 for the product, effective 2026-11-01, issued on the clock's current date. */
    protected String issued(String productCode) throws Exception {
        return endorsements.issuePolicy("customer1", productCode, ISSUE_DAY);
    }

    /** The C3 detail of the policy as customer1, requiring 200. */
    protected JsonNode detail(String policyNumber) throws Exception {
        MvcResult result = policies.detail("customer1", policyNumber);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return policies.bodyOf(result);
    }

    /** Requires a problem+json answer with the given status and returns its body. */
    protected JsonNode problem(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        return policies.bodyOf(result);
    }

    protected void at(String date) {
        clock.setDate(LocalDate.parse(date));
    }
}
