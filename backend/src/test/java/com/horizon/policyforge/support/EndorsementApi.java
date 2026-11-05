package com.horizon.policyforge.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MockMvc calls to the endorsement endpoints (C4, C5) and the body builders for the three endorsement types, plus the
 * set-up that issues a policy for a product on the clock's date. Synthetic data only.
 */
public final class EndorsementApi {

    public static final String NEW_LINE1 = "22 New Road";
    public static final String NEW_CITY = "Mumbai";
    public static final String NEW_POSTCODE = "400001";
    public static final String NOMINEE_NAME = "Ravi Nominee";

    /** The exact key set of one C3 endorsement, in the order the API returns it. */
    public static final List<String> ENDORSEMENT_FIELDS = List.of("sequenceNo", "type", "changes", "beforeVersionNo",
            "afterVersionNo", "newAnnualPremium", "adjustment", "effectiveDate", "actorId", "createdAt");

    /** The exact key set of the C4 success body. */
    public static final List<String> CREATED_FIELDS = List.of("endorsement", "policy");

    /** The exact key set of the C5 preview body. */
    public static final List<String> PREVIEW_FIELDS = List.of("type", "effectiveDate", "currentAnnualPremium",
            "newAnnualPremium", "adjustment", "unexpiredDays", "termDays");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final ApplicationApi applications;
    private final PolicyApi policies;

    public EndorsementApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
        this.applications = new ApplicationApi(mvc, json);
        this.policies = new PolicyApi(mvc, json);
    }

    // ---- request bodies (C4) ----

    /** CHANGE_ADDRESS to the given address. A null effective date is left out of the body. */
    public static Map<String, Object> addressBody(String line1, String city, String postcode, String effectiveDate) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("line1", line1);
        address.put("city", city);
        address.put("postcode", postcode);
        Map<String, Object> body = typed("CHANGE_ADDRESS", effectiveDate);
        body.put("address", address);
        return body;
    }

    /** CHANGE_ADDRESS to the standard new address. */
    public static Map<String, Object> newAddressBody(String effectiveDate) {
        return addressBody(NEW_LINE1, NEW_CITY, NEW_POSTCODE, effectiveDate);
    }

    /** ADD_NOMINEE with the given relationship and share. */
    public static Map<String, Object> nomineeBody(String name, String relationship, Object sharePercent,
            String effectiveDate) {
        Map<String, Object> nominee = new LinkedHashMap<>();
        nominee.put("name", name);
        nominee.put("relationship", relationship);
        nominee.put("sharePercent", sharePercent);
        Map<String, Object> body = typed("ADD_NOMINEE", effectiveDate);
        body.put("nominee", nominee);
        return body;
    }

    /** CHANGE_SUM_INSURED for TERM_LIFE and MOTOR. */
    public static Map<String, Object> sumInsuredBody(Object newSumInsured, String effectiveDate) {
        Map<String, Object> body = typed("CHANGE_SUM_INSURED", effectiveDate);
        body.put("newSumInsured", newSumInsured);
        return body;
    }

    /** CHANGE_SUM_INSURED for HOUSEHOLD: a null component is left out. */
    public static Map<String, Object> householdBody(String building, String contents, String effectiveDate) {
        Map<String, Object> body = typed("CHANGE_SUM_INSURED", effectiveDate);
        if (building != null) {
            body.put("newBuildingSumInsured", building);
        }
        if (contents != null) {
            body.put("newContentsSumInsured", contents);
        }
        return body;
    }

    private static Map<String, Object> typed(String type, String effectiveDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        if (effectiveDate != null) {
            body.put("effectiveDate", effectiveDate);
        }
        return body;
    }

    // ---- calls ----

    /** POST /api/policies/{number}/endorsements as {@code user}, whatever the answer. */
    public MvcResult endorse(String user, String policyNumber, Object body) throws Exception {
        return send(HttpMethod.POST, "/api/policies/" + policyNumber + "/endorsements", user, body);
    }

    /** POST /api/policies/{number}/endorsements/preview as {@code user}, whatever the answer. */
    public MvcResult preview(String user, String policyNumber, Object body) throws Exception {
        return send(HttpMethod.POST, "/api/policies/" + policyNumber + "/endorsements/preview", user, body);
    }

    /** Any method and path as {@code user} with a JSON body (null for none). */
    public MvcResult send(HttpMethod method, String path, String user, Object body) throws Exception {
        var builder = request(method, path).with(httpBasic(user, ApplicationApi.PASSWORD))
                .contentType("application/json");
        if (body != null) {
            builder = builder.content(json.writeValueAsString(body));
        }
        return mvc.perform(builder).andReturn();
    }

    /** POST an endorsement with a raw body and content type. */
    public MvcResult endorseRaw(String user, String policyNumber, String contentType, String body) throws Exception {
        return mvc.perform(request(HttpMethod.POST, "/api/policies/" + policyNumber + "/endorsements")
                .with(httpBasic(user, ApplicationApi.PASSWORD)).contentType(contentType).content(body)).andReturn();
    }

    /** POST a preview with a raw body and content type. */
    public MvcResult previewRaw(String user, String policyNumber, String contentType, String body) throws Exception {
        return mvc.perform(request(HttpMethod.POST, "/api/policies/" + policyNumber + "/endorsements/preview")
                .with(httpBasic(user, ApplicationApi.PASSWORD)).contentType(contentType).content(body)).andReturn();
    }

    /** Endorses as customer1 and requires 201; returns the body. */
    public JsonNode endorseOk(String policyNumber, Object body) throws Exception {
        MvcResult result = endorse("customer1", policyNumber, body);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return bodyOf(result);
    }

    /** Previews as customer1 and requires 200; returns the body. */
    public JsonNode previewOk(String policyNumber, Object body) throws Exception {
        MvcResult result = preview("customer1", policyNumber, body);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return bodyOf(result);
    }

    public JsonNode bodyOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    // ---- set-up ----

    /**
     * Quotes, applies, gets approved and issues a policy of {@code productCode} for {@code user}, effective on
     * {@code today} (the clock's date), and returns the policy number. TERM_LIFE is the AC-04.1 profile (5,000,000.00,
     * premium 6850.00), MOTOR is 800000.00 (premium 15150.00) and HOUSEHOLD is building 6,000,000.00 plus contents
     * 1,000,000.00 (premium 11350.00).
     */
    public String issuePolicy(String user, String productCode, LocalDate today) throws Exception {
        String applicationId = switch (productCode) {
            case "TERM_LIFE" -> applications.approvedApplication(user, today);
            case "MOTOR" -> applications.applyAs(user, "MOTOR", ApplicationFixtures.motorInputs(30, 0), 30, today);
            case "HOUSEHOLD" -> applications.applyAs(user, "HOUSEHOLD", ApplicationFixtures.householdInputs("BRICK"),
                    50, today);
            default -> throw new IllegalArgumentException(productCode);
        };
        return policies.issueOk(user, applicationId, today.toString()).get("policyNumber").asText();
    }
}
