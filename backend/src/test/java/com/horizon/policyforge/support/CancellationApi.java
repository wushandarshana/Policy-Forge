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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MockMvc calls to the cancellation endpoints (sprint 4 contracts C3 and C4): the refund preview and the cancellation
 * itself. A null user sends no credentials. Synthetic data only.
 */
public final class CancellationApi {

    /** The exact key set of the C3 preview, in the order the API returns it. */
    public static final List<String> PREVIEW_FIELDS = List.of("cancellationDate", "termNo", "termStart", "termEnd",
            "premium", "termDays", "unexpiredDays", "coolingOff", "fee", "amount", "formula");

    /** The exact key set of one C5 refund, in the order the API returns it. */
    public static final List<String> REFUND_FIELDS = List.of("termNo", "cancellationDate", "premium", "termDays",
            "unexpiredDays", "coolingOff", "fee", "amount", "formula", "actorId", "createdAt");

    /** The exact key set of the C4 success body. */
    public static final List<String> CANCEL_FIELDS = List.of("refund", "policy");

    /** The four cancellation reasons of plan G-4. */
    public static final List<String> REASONS = List.of("NO_LONGER_NEEDED", "REPLACED_BY_OTHER_COVER", "AFFORDABILITY",
            "OTHER");

    private final MockMvc mvc;
    private final ObjectMapper json;

    public CancellationApi(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public static String previewPath(String policyNumber) {
        return "/api/policies/" + policyNumber + "/cancel/preview";
    }

    public static String cancelPath(String policyNumber) {
        return "/api/policies/" + policyNumber + "/cancel";
    }

    /** The C4 body: {@code {reason}} for a null date, otherwise {@code {reason, cancellationDate}}. */
    public static Map<String, Object> cancelBody(String reason, String cancellationDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) {
            body.put("reason", reason);
        }
        if (cancellationDate != null) {
            body.put("cancellationDate", cancellationDate);
        }
        return body;
    }

    /** GET the preview as {@code user} for the date (null for none), whatever the answer. */
    public MvcResult preview(String user, String policyNumber, String date) throws Exception {
        return previewRaw(user, policyNumber, date == null ? "" : "date=" + date);
    }

    /** GET the preview with a raw query string (without the question mark, or empty). */
    public MvcResult previewRaw(String user, String policyNumber, String rawQuery) throws Exception {
        String url = previewPath(policyNumber) + (rawQuery.isEmpty() ? "" : "?" + rawQuery);
        return mvc.perform(withUser(get(url), user)).andReturn();
    }

    /** POST the cancellation as {@code user} with a JSON body, whatever the answer. */
    public MvcResult cancel(String user, String policyNumber, Object body) throws Exception {
        return mvc.perform(withUser(post(cancelPath(policyNumber)), user)
                .contentType("application/json").content(json.writeValueAsString(body))).andReturn();
    }

    /** POST the cancellation with a raw body and content type. */
    public MvcResult cancelRaw(String user, String policyNumber, String contentType, String body) throws Exception {
        return mvc.perform(withUser(post(cancelPath(policyNumber)), user).contentType(contentType).content(body))
                .andReturn();
    }

    /** POST the cancellation with no body at all (JSON content type). */
    public MvcResult cancelEmptyBody(String user, String policyNumber) throws Exception {
        return mvc.perform(withUser(post(cancelPath(policyNumber)), user).contentType("application/json"))
                .andReturn();
    }

    /** Previews as customer1 and requires 200; returns the body. */
    public JsonNode previewOk(String policyNumber, String date) throws Exception {
        MvcResult result = preview("customer1", policyNumber, date);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return bodyOf(result);
    }

    /** Cancels as customer1 with the reason NO_LONGER_NEEDED and requires 201; returns the body. */
    public JsonNode cancelOk(String policyNumber, String cancellationDate) throws Exception {
        MvcResult result = cancel("customer1", policyNumber, cancelBody("NO_LONGER_NEEDED", cancellationDate));
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return bodyOf(result);
    }

    public JsonNode bodyOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder builder, String user) {
        return user == null ? builder : builder.with(httpBasic(user, ApplicationApi.PASSWORD));
    }
}
