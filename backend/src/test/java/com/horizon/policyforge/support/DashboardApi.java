package com.horizon.policyforge.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * MockMvc calls to the admin dashboard (sprint 4 contract C6, AC-11) and the exact key sets of its body. A null user
 * sends no credentials. Synthetic data only.
 */
public final class DashboardApi {

    public static final String PATH = "/api/admin/dashboard";

    /** The exact key set of the C6 body, in the order the API returns it. */
    public static final List<String> FIELDS = List.of("asOf", "activePoliciesByProduct", "premiumCollected",
            "renewalPipeline", "lapseForecast");

    /** The products, always all three, in the launch order. */
    public static final List<String> PRODUCTS = List.of("TERM_LIFE", "MOTOR", "HOUSEHOLD");

    /** The premium-collected keys: the three products, then the total. */
    public static final List<String> PREMIUM_KEYS = List.of("TERM_LIFE", "MOTOR", "HOUSEHOLD", "total");

    public static final List<String> PIPELINE_FIELDS = List.of("policyNumber", "termEnd", "renewalPremium", "paid");

    public static final List<String> FORECAST_FIELDS = List.of("count", "policies");

    private final MockMvc mvc;
    private final ObjectMapper json;

    public DashboardApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** GET the dashboard as {@code user} with the raw query string (without "?", or empty), whatever the answer. */
    public MvcResult getRaw(String user, String rawQuery) throws Exception {
        String url = PATH + (rawQuery == null || rawQuery.isEmpty() ? "" : "?" + rawQuery);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url);
        return mvc.perform(user == null ? builder : builder.with(httpBasic(user, ApplicationApi.PASSWORD)))
                .andReturn();
    }

    /** GET the dashboard as {@code user}; {@code asOf} is left out when null. */
    public MvcResult get(String user, String asOf) throws Exception {
        return getRaw(user, asOf == null ? "" : "asOf=" + asOf);
    }

    /** GET as admin1 and require 200; returns the body. {@code asOf} is left out when null. */
    public JsonNode ok(String asOf) throws Exception {
        MvcResult result = get("admin1", asOf);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return bodyOf(result);
    }

    public JsonNode bodyOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The money of {@code premiumCollected.<key>} as an exact decimal. */
    public static BigDecimal premium(JsonNode dashboard, String key) {
        return new BigDecimal(dashboard.get("premiumCollected").get(key).asText());
    }

    /** The pipeline item of the policy, or null. */
    public static JsonNode pipelineItem(JsonNode dashboard, String policyNumber) {
        for (JsonNode item : dashboard.get("renewalPipeline")) {
            if (policyNumber.equals(item.get("policyNumber").asText())) {
                return item;
            }
        }
        return null;
    }

    /** The policy numbers of the lapse forecast. */
    public static List<String> forecastNumbers(JsonNode dashboard) {
        List<String> numbers = new ArrayList<>();
        dashboard.get("lapseForecast").get("policies").forEach(number -> numbers.add(number.asText()));
        return numbers;
    }
}
