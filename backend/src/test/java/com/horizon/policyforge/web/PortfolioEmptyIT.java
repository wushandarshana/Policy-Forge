package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.DashboardApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * A portfolio with nothing in force (AC-11.1, P-11). This class has its own application context, because it lapses
 * every policy in it: one renewal cycle far in the future offers and then lapses each in-force policy, seed included
 * (a lapse is terminal and keeps the premium that was collected). The dashboard must still list all three products,
 * with a count of 0 for each.
 */
@Tag("AC-11")
@SpringBootTest(properties = "policyforge.test.context=dashboard-empty")
class PortfolioEmptyIT extends RenewalTestSupport {

    private DashboardApi dashboards;

    @BeforeEach
    void setUpDashboard() {
        dashboards = new DashboardApi(mvc, json);
    }

    @Test
    @DisplayName("AC-11.1: with every policy lapsed the dashboard still lists all three products with a count of 0, keeps the premium collected, and has an empty pipeline and a forecast of count 0")
    void aPortfolioWithNothingInForceStillListsAllThreeProducts() throws Exception {
        issued("TERM_LIFE");
        issued("MOTOR");
        JsonNode before = dashboards.ok("2035-01-01");
        assertThat(before.get("activePoliciesByProduct").get("TERM_LIFE").asInt()).isGreaterThan(0);

        JsonNode run = renewals.jobOk("2035-01-01");

        assertThat(run.get("lapsed")).as("every in-force policy lapsed").isNotEmpty();
        JsonNode after = dashboards.ok("2035-01-01");
        assertThat(DashboardApi.PRODUCTS).allSatisfy(product ->
                assertThat(after.get("activePoliciesByProduct").get(product).asInt()).as(product).isZero());
        assertThat(after.get("activePoliciesByProduct")).hasSize(3);
        assertThat(after.get("premiumCollected")).as("a lapse keeps what was collected")
                .isEqualTo(before.get("premiumCollected"));
        assertThat(after.get("renewalPipeline")).isEmpty();
        assertThat(after.get("lapseForecast").get("count").asInt()).isZero();
        assertThat(after.get("lapseForecast").get("policies")).isEmpty();
    }
}
