package com.horizon.policyforge.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MockMvc calls to the renewal endpoints (sprint 4 contracts C1 and C2): the admin cycle job and the owner's payment.
 * A null user sends no credentials. Synthetic data only.
 */
public final class RenewalApi {

    public static final String JOB_PATH = "/api/admin/jobs/renewal-cycle";

    /** The exact key set of the C1 job result, in the order the API returns it. */
    public static final List<String> JOB_FIELDS = List.of("runDate", "offersCreated", "lapsed");

    /** The exact key set of the C2 payment result. */
    public static final List<String> PAYMENT_FIELDS = List.of("policy", "term");

    private final MockMvc mvc;
    private final ObjectMapper json;

    public RenewalApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public static String payPath(String policyNumber) {
        return "/api/policies/" + policyNumber + "/renewal/pay";
    }

    private static String jobUrl(String date) {
        return JOB_PATH + (date == null ? "" : "?date=" + date);
    }

    /** POST the cycle job as {@code user} for the date (null for none) with a JSON body, whatever the answer. */
    public MvcResult job(String user, String date, Object body) throws Exception {
        return mvc.perform(withUser(post(jobUrl(date)), user)
                .contentType("application/json").content(json.writeValueAsString(body))).andReturn();
    }

    /** POST the cycle job with a raw body and content type. */
    public MvcResult jobRaw(String user, String date, String contentType, String body) throws Exception {
        return mvc.perform(withUser(post(jobUrl(date)), user).contentType(contentType).content(body)).andReturn();
    }

    /** POST the cycle job with an empty JSON body, as the spec allows. */
    public MvcResult jobEmptyBody(String user, String date) throws Exception {
        return mvc.perform(withUser(post(jobUrl(date)), user).contentType("application/json")).andReturn();
    }

    /** Runs the job as admin1 for the date and requires 200; returns the body. */
    public JsonNode jobOk(String date) throws Exception {
        MvcResult result = job("admin1", date, Map.of());
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return bodyOf(result);
    }

    /** POST the payment as {@code user} with a JSON body, whatever the answer. */
    public MvcResult pay(String user, String policyNumber, Object body) throws Exception {
        return mvc.perform(withUser(post(payPath(policyNumber)), user)
                .contentType("application/json").content(json.writeValueAsString(body))).andReturn();
    }

    /** POST the payment as {@code user} with an empty JSON body. */
    public MvcResult payEmptyBody(String user, String policyNumber) throws Exception {
        return mvc.perform(withUser(post(payPath(policyNumber)), user).contentType("application/json")).andReturn();
    }

    /** POST the payment with a raw body and content type. */
    public MvcResult payRaw(String user, String policyNumber, String contentType, String body) throws Exception {
        return mvc.perform(withUser(post(payPath(policyNumber)), user).contentType(contentType).content(body))
                .andReturn();
    }

    /** Pays as customer1 with {@code {}} and requires 200; returns the body. */
    public JsonNode payOk(String policyNumber) throws Exception {
        MvcResult result = pay("customer1", policyNumber, new LinkedHashMap<>());
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return bodyOf(result);
    }

    public JsonNode bodyOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder builder, String user) {
        return user == null ? builder : builder.with(httpBasic(user, ApplicationApi.PASSWORD));
    }
}
