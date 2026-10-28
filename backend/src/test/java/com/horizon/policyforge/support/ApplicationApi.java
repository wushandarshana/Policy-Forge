package com.horizon.policyforge.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** MockMvc calls shared by the application and underwriting integration tests. Every user's password is "password". */
public final class ApplicationApi {

    public static final String PASSWORD = "password";

    private final MockMvc mvc;
    private final ObjectMapper json;

    public ApplicationApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** Creates a public quote and returns its body; fails the test unless the answer is 201. */
    public JsonNode createQuote(String productCode, Map<String, Object> riskInputs) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", productCode);
        body.put("riskInputs", riskInputs);
        MvcResult result = mvc.perform(post("/api/quotes").contentType("application/json")
                .content(json.writeValueAsString(body))).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return bodyOf(result);
    }

    /** The ID of a freshly created quote. */
    public String quoteId(String productCode, Map<String, Object> riskInputs) throws Exception {
        return createQuote(productCode, riskInputs).get("quoteId").asText();
    }

    /** POST /api/applications as {@code user} with the given body, whatever the answer. */
    public MvcResult apply(String user, Object body) throws Exception {
        return mvc.perform(post("/api/applications")
                .with(httpBasic(user, PASSWORD))
                .contentType("application/json")
                .content(json.writeValueAsString(body))).andReturn();
    }

    /** Applies and requires 201; returns the response body. */
    public JsonNode applyOk(String user, String quoteId, Map<String, Object> kyc) throws Exception {
        MvcResult result = apply(user, ApplicationFixtures.application(quoteId, kyc));
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return bodyOf(result);
    }

    /** GET /api/applications/mine as {@code user}. */
    public MvcResult mine(String user) throws Exception {
        return mvc.perform(get("/api/applications/mine").with(httpBasic(user, PASSWORD))).andReturn();
    }

    /**
     * Quotes and applies as {@code user} with the given risk inputs and a date of birth that gives {@code age} on
     * {@code today}, and returns the application ID. Requires the application to be accepted (201).
     */
    public String applyAs(String user, String productCode, Map<String, Object> riskInputs, int age, LocalDate today)
            throws Exception {
        String quoteId = quoteId(productCode, riskInputs);
        return applyOk(user, quoteId, ApplicationFixtures.kyc(today.minusYears(age))).get("applicationId").asText();
    }

    /** A new REFERRED application (TERM_LIFE smoker, sum insured 12,000,000.00: UW-TL-002). */
    public String referredApplication(String user, LocalDate today) throws Exception {
        return applyAs(user, "TERM_LIFE", ApplicationFixtures.termLifeInputs(40, true, "12000000.00", "24", List.of()),
                40, today);
    }

    /** A new DECLINED application (TERM_LIFE age 62, BMI 37: UW-TL-001 and UW-TL-004). */
    public String declinedApplication(String user, LocalDate today) throws Exception {
        return applyAs(user, "TERM_LIFE", ApplicationFixtures.termLifeInputs(62, false, "5000000.00", "37", List.of()),
                62, today);
    }

    /** A new APPROVED application (the AC-04.1 profile: UW-OK). */
    public String approvedApplication(String user, LocalDate today) throws Exception {
        return applyAs(user, "TERM_LIFE", ApplicationFixtures.cleanTermLifeInputs(), 35, today);
    }

    /** GET /api/underwriting/queue as {@code user}. {@code query} is the raw query string without "?", or empty. */
    public MvcResult queue(String user, String query) throws Exception {
        String path = "/api/underwriting/queue" + (query.isEmpty() ? "" : "?" + query);
        return mvc.perform(get(path).with(httpBasic(user, PASSWORD))).andReturn();
    }

    /** POST /api/underwriting/{id}/decision as {@code user} with a JSON body, whatever the answer. */
    public MvcResult decide(String user, String applicationId, Object body) throws Exception {
        return mvc.perform(post("/api/underwriting/{id}/decision", applicationId)
                .with(httpBasic(user, PASSWORD))
                .contentType("application/json")
                .content(json.writeValueAsString(body))).andReturn();
    }

    /** POST /api/underwriting/{id}/override as {@code user} with a JSON body, whatever the answer. */
    public MvcResult override(String user, String applicationId, Object body) throws Exception {
        return mvc.perform(post("/api/underwriting/{id}/override", applicationId)
                .with(httpBasic(user, PASSWORD))
                .contentType("application/json")
                .content(json.writeValueAsString(body))).andReturn();
    }

    /** The C5 decision body: {@code {decision, reasonCode, comment}}. */
    public static Map<String, Object> decisionBody(String decision, String reasonCode, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("reasonCode", reasonCode);
        body.put("comment", comment);
        return body;
    }

    /** The C5 override body: {@code {reasonCode, comment}}. */
    public static Map<String, Object> overrideBody(String reasonCode, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reasonCode", reasonCode);
        body.put("comment", comment);
        return body;
    }

    /** The queue item with this application ID, or null when it is not listed (or the body is not a list). */
    public static JsonNode itemFor(JsonNode queue, String applicationId) {
        if (!queue.isArray()) {
            return null;
        }
        for (JsonNode item : queue) {
            if (applicationId.equals(item.get("applicationId").asText())) {
                return item;
            }
        }
        return null;
    }

    /** The position of the application in the queue, or -1 when it is not listed (or the body is not a list). */
    public static int indexOf(JsonNode queue, String applicationId) {
        if (!queue.isArray()) {
            return -1;
        }
        for (int i = 0; i < queue.size(); i++) {
            if (applicationId.equals(queue.get(i).get("applicationId").asText())) {
                return i;
            }
        }
        return -1;
    }

    public JsonNode bodyOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The {@code errors[].field} values of a 400 problem body, in the order returned. */
    public static List<String> errorFields(JsonNode problem) {
        List<String> fields = new ArrayList<>();
        problem.get("errors").forEach(error -> fields.add(error.get("field").asText()));
        return fields;
    }

    /** The field names of a JSON object, in order. */
    public static List<String> fieldNames(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** The text values of a JSON array. */
    public static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
