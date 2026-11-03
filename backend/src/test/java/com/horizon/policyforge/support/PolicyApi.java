package com.horizon.policyforge.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** MockMvc calls to the policy endpoints (C1 to C3), shared by the issuance and policy view integration tests. */
public final class PolicyApi {

    /** The exact key set of a C1 policy header, in the order the API returns it. */
    public static final List<String> HEADER_FIELDS = List.of("policyNumber", "applicationId", "productCode", "status",
            "currentVersionNo", "ruleVersion", "effectiveDate", "expiryDate", "sumInsured", "annualPremium",
            "nextPremiumDueDate", "endorsementCount");

    /** The exact key set of one C2 item. */
    public static final List<String> SUMMARY_FIELDS = List.of("policyNumber", "productCode", "status", "sumInsured",
            "annualPremium", "nextPremiumDueDate", "endorsementCount");

    /** The exact key set of the C3 body. */
    public static final List<String> DETAIL_FIELDS = List.of("policy", "versions", "endorsements", "transitions",
            "terms", "refunds");

    private final MockMvc mvc;
    private final ObjectMapper json;

    public PolicyApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** The C1 body: {@code {}} for a null date, otherwise {@code {effectiveDate}}. */
    public static Map<String, Object> issueBody(String effectiveDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (effectiveDate != null) {
            body.put("effectiveDate", effectiveDate);
        }
        return body;
    }

    /** POST /api/applications/{id}/issue as {@code user} with the given JSON body, whatever the answer. */
    public MvcResult issue(String user, String applicationId, Object body) throws Exception {
        return mvc.perform(post("/api/applications/{id}/issue", applicationId)
                .with(httpBasic(user, ApplicationApi.PASSWORD))
                .contentType("application/json")
                .content(json.writeValueAsString(body))).andReturn();
    }

    /** POST /api/applications/{id}/issue as {@code user} with a raw body and content type. */
    public MvcResult issueRaw(String user, String applicationId, String contentType, String body) throws Exception {
        return mvc.perform(post("/api/applications/{id}/issue", applicationId)
                .with(httpBasic(user, ApplicationApi.PASSWORD))
                .contentType(contentType)
                .content(body)).andReturn();
    }

    /** Issues with the given effective date (null for none) and requires 201; returns the body. */
    public JsonNode issueOk(String user, String applicationId, String effectiveDate) throws Exception {
        MvcResult result = issue(user, applicationId, issueBody(effectiveDate));
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return bodyOf(result);
    }

    /** GET /api/policies/mine as {@code user}. */
    public MvcResult mine(String user) throws Exception {
        return mvc.perform(get("/api/policies/mine").with(httpBasic(user, ApplicationApi.PASSWORD))).andReturn();
    }

    /** GET /api/policies/{number} as {@code user}. */
    public MvcResult detail(String user, String policyNumber) throws Exception {
        return mvc.perform(get("/api/policies/{number}", policyNumber)
                .with(httpBasic(user, ApplicationApi.PASSWORD))).andReturn();
    }

    public JsonNode bodyOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The per-product sequence of a policy number ({@code PF-TL-2026-000042} gives 42). */
    public static int sequenceOf(String policyNumber) {
        return Integer.parseInt(policyNumber.substring(policyNumber.lastIndexOf('-') + 1));
    }
}
