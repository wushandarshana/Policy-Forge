package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.CancellationApi;
import com.horizon.policyforge.support.DashboardApi;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The admin dashboard endpoint (sprint 4 contract C6, P-11): its exact keys, its three products, scale-2 money strings,
 * the {@code asOf} parameter, and who may call it (AC-11.5, NFR-04.1). The seed is in the context (P-15), so nothing here
 * assumes a count: the active counts are compared with the database, and the other figures with their own shape.
 */
@Tag("AC-11")
class PortfolioDashboardIT extends DashboardTestSupport {

    private static final String MONEY = "-?\\d+\\.\\d{2}";

    private int inForce(String productCode) {
        return jdbc.queryForObject("select count(*) from policy where product_code = ? and status in ('ACTIVE',"
                + " 'ENDORSED', 'RENEWED')", Integer.class, productCode);
    }

    @Test
    @DisplayName("AC-11.1: the dashboard answers exactly the spec's keys, lists all three products, shows money as scale-2 strings with a total equal to the sum of the products, and defaults asOf to today")
    void exactKeysAllProductsAndScaleTwoMoney() throws Exception {
        MvcResult result = dashboards.get("admin1", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        JsonNode dashboard = dashboards.bodyOf(result);
        assertThat(fieldNames(dashboard)).containsExactlyElementsOf(DashboardApi.FIELDS);
        assertThat(dashboard.get("asOf").asText()).isEqualTo("2026-11-01");
        assertThat(fieldNames(dashboard.get("activePoliciesByProduct"))).containsExactlyElementsOf(DashboardApi.PRODUCTS);
        dashboard.get("activePoliciesByProduct").forEach(count -> assertThat(count.isInt()).isTrue());
        assertThat(fieldNames(dashboard.get("premiumCollected"))).containsExactlyElementsOf(DashboardApi.PREMIUM_KEYS);
        dashboard.get("premiumCollected").forEach(
                money -> assertThat(money.isTextual() && money.asText().matches(MONEY)).as(money.toString()).isTrue());
        BigDecimal sum = BigDecimal.ZERO;
        for (String product : DashboardApi.PRODUCTS) {
            sum = sum.add(DashboardApi.premium(dashboard, product));
        }
        assertThat(DashboardApi.premium(dashboard, "total")).isEqualTo(sum);
        assertThat(dashboard.get("renewalPipeline").isArray()).isTrue();
        assertThat(fieldNames(dashboard.get("lapseForecast"))).containsExactlyElementsOf(DashboardApi.FORECAST_FIELDS);
        assertThat(dashboard.get("lapseForecast").get("count").asInt())
                .isEqualTo(dashboard.get("lapseForecast").get("policies").size());
        for (JsonNode item : dashboard.get("renewalPipeline")) {
            assertThat(fieldNames(item)).containsExactlyElementsOf(DashboardApi.PIPELINE_FIELDS);
            assertThat(item.get("renewalPremium").isNull() || item.get("renewalPremium").asText().matches(MONEY))
                    .isTrue();
            assertThat(item.get("paid").isBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("AC-11.1: the active counts equal the in-force policies per product in the database (ACTIVE, ENDORSED and RENEWED, never LAPSED or CANCELLED), after new policies are issued and one is cancelled")
    void activeCountsEqualTheInForcePoliciesPerProduct() throws Exception {
        issued("TERM_LIFE");
        issued("MOTOR");
        issued("HOUSEHOLD");
        String doomed = issued("TERM_LIFE");
        JsonNode before = dashboards.ok(null);

        new CancellationApi(mvc, json).cancelOk(doomed, "2027-02-09");

        JsonNode after = dashboards.ok(null);
        for (String product : DashboardApi.PRODUCTS) {
            assertThat(after.get("activePoliciesByProduct").get(product).asInt()).as(product)
                    .isEqualTo(inForce(product));
        }
        assertThat(after.get("activePoliciesByProduct").get("TERM_LIFE").asInt())
                .as("a cancelled policy is no longer active")
                .isEqualTo(before.get("activePoliciesByProduct").get("TERM_LIFE").asInt() - 1);
        assertThat(after.get("activePoliciesByProduct").get("MOTOR").asInt())
                .isEqualTo(before.get("activePoliciesByProduct").get("MOTOR").asInt());
    }

    @Test
    @DisplayName("AC-11.1: asOf is echoed, the active counts do not depend on it, and the pipeline and forecast are sorted: the pipeline by term end then number, the forecast by number")
    void asOfIsEchoedAndListsAreSorted() throws Exception {
        issued("TERM_LIFE");
        issued("MOTOR");
        issued("TERM_LIFE");

        JsonNode early = dashboards.ok("2027-09-01");
        JsonNode late = dashboards.ok("2027-12-15");

        assertThat(early.get("asOf").asText()).isEqualTo("2027-09-01");
        assertThat(late.get("asOf").asText()).isEqualTo("2027-12-15");
        assertThat(early.get("activePoliciesByProduct")).isEqualTo(late.get("activePoliciesByProduct"));
        List<String> keys = new ArrayList<>();
        for (JsonNode item : early.get("renewalPipeline")) {
            keys.add(item.get("termEnd").asText() + " " + item.get("policyNumber").asText());
        }
        assertThat(keys).as("the policies issued on 2026-11-01 end on 2027-10-31").isNotEmpty()
                .isSortedAccordingTo(String::compareTo);
        List<String> forecast = DashboardApi.forecastNumbers(late);
        assertThat(forecast).as("the same policies are past their term end on 2027-12-15").isNotEmpty()
                .isSortedAccordingTo(String::compareTo);
        assertThat(late.get("lapseForecast").get("count").asInt()).isEqualTo(forecast.size());
    }

    @Test
    @DisplayName("AC-11.1: a malformed asOf (an impossible date, other formats, empty, a repeated parameter) is a 400 problem+json naming asOf with a fixed detail that never echoes it")
    void malformedAsOfIsA400NamingAsOf() throws Exception {
        List<String> queries = new ArrayList<>();
        for (String value : List.of("2027-13-01", "2027-02-30", "abc", "27-10-15", "2027-1-5", "20271015",
                "2027-10-15T00:00:00", "SYNTHETIC_ASOF_ALPHA")) {
            queries.add("asOf=" + value);
        }
        queries.add("asOf=");
        queries.add("asOf=2027-10-15&asOf=2027-10-16");

        for (String query : queries) {
            MvcResult result = dashboards.getRaw("admin1", query);

            assertThat(result.getResponse().getStatus()).as(query).isEqualTo(400);
            assertThat(result.getResponse().getContentType()).as(query).startsWith("application/problem+json");
            JsonNode problem = dashboards.bodyOf(result);
            assertThat(errorFields(problem)).as(query).containsExactly("asOf");
            assertThat(result.getResponse().getContentAsString()).as(query).doesNotContain("SYNTHETIC_ASOF_ALPHA")
                    .doesNotContain("2027-13-01");
        }
    }

    @Test
    @DisplayName("AC-11.5: customer1, customer2 and underwriter1 get 403 problem+json and nothing of the dashboard, and admin1 gets 200")
    void onlyAnAdminReadsTheDashboard() throws Exception {
        for (String user : List.of("customer1", "customer2", "underwriter1")) {
            MvcResult refused = dashboards.get(user, null);

            assertThat(refused.getResponse().getStatus()).as(user).isEqualTo(403);
            assertThat(refused.getResponse().getContentType()).as(user).startsWith("application/problem+json");
            JsonNode problem = dashboards.bodyOf(refused);
            assertThat(problem.get("status").asInt()).isEqualTo(403);
            assertThat(problem.has("activePoliciesByProduct")).as(user).isFalse();
            assertThat(problem.has("premiumCollected")).as(user).isFalse();
        }
        MvcResult admin = dashboards.get("admin1", "2027-10-15");

        assertThat(admin.getResponse().getStatus()).isEqualTo(200);
        assertThat(dashboards.bodyOf(admin).get("asOf").asText()).isEqualTo("2027-10-15");
    }

    @Test
    @Tag("NFR-04")
    @DisplayName("NFR-04.1: an anonymous dashboard request and one with a wrong password are 401 problem+json with no Basic challenge, and a malformed asOf is still 401 before it is read")
    void anonymousIsRefusedWithoutAChallenge() throws Exception {
        MvcResult anonymous = dashboards.get(null, null);
        MvcResult wrongPassword = mvc.perform(request(HttpMethod.GET, DashboardApi.PATH)
                .with(httpBasic("admin1", "not-the-password"))).andReturn();
        MvcResult malformedAnonymous = dashboards.get(null, "not-a-date");

        for (MvcResult result : List.of(anonymous, wrongPassword, malformedAnonymous)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
            assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("activePoliciesByProduct");
        }
    }

    @Test
    @Tag("NFR-04")
    @DisplayName("NFR-04.1: the dashboard is GET only, so a POST, PUT, PATCH or DELETE by admin1 is refused with a 405 problem and the dashboard figures do not change")
    void theDashboardIsReadOnly() throws Exception {
        JsonNode before = dashboards.ok(null);

        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            MvcResult refused = mvc.perform(request(method, DashboardApi.PATH)
                    .with(httpBasic("admin1", ApplicationApi.PASSWORD)).contentType("application/json")
                    .content("{}")).andReturn();

            assertThat(refused.getResponse().getStatus()).as(method.name()).isEqualTo(405);
            assertThat(refused.getResponse().getContentType()).as(method.name()).startsWith("application/problem+json");
        }
        assertThat(dashboards.ok(null)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-11.1: the dashboard carries policy numbers, products, dates and money only: no customer ID, no name and no KYC value appears in the body")
    void theBodyHoldsNoPersonalData() throws Exception {
        issued("TERM_LIFE");

        String body = dashboards.get("admin1", "2027-10-15").getResponse().getContentAsString();

        assertThat(body).doesNotContain("customer1").doesNotContain("customer2").doesNotContain("Asha Test")
                .doesNotContain("XXXX-XXXX").doesNotContain("ABCDE").doesNotContain("Street");
    }
}
