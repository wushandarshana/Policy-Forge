package com.horizon.policyforge.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
