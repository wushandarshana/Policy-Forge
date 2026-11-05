package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.CancellationApi;
import com.horizon.policyforge.support.CancellationRows;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The authentication, ownership and request-format boundary of the two cancellation endpoints (NFR-04.1, NFR-04.3, plan
 * C3, C4 and P-5): the refund preview and the cancel. Every refused request is proved to write nothing, and nothing a
 * caller typed is echoed.
 */
@Tag("NFR-04")
class CancellationSecurityIT extends CancellationTestSupport {

    private static final String FIXED_415_DETAIL = "The request content type must be application/json.";

    @Test
    @DisplayName("NFR-04.1: previewing answers anonymous with 401 and no Basic challenge, underwriter1 and admin1 with 403, another customer, an unknown number and a malformed number with 404 and the fixed detail, and the owner with 200")
    void onlyTheOwningCustomerCanPreview() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);

        MvcResult anonymous = cancellations.preview(null, number, "2027-02-09");
        MvcResult underwriter = cancellations.preview("underwriter1", number, "2027-02-09");
        MvcResult admin = cancellations.preview("admin1", number, "2027-02-09");

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(anonymous.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(anonymous.getResponse().getHeader("WWW-Authenticate")).isNull();
        assertThat(underwriter.getResponse().getStatus()).isEqualTo(403);
        assertThat(admin.getResponse().getStatus()).isEqualTo(403);
        assertThat(underwriter.getResponse().getContentType()).startsWith("application/problem+json");
        for (MvcResult notFound : List.of(cancellations.preview("customer2", number, "2027-02-09"),
                cancellations.preview("customer1", "PF-TL-2026-999999", "2027-02-09"),
                cancellations.preview("customer1", "PF-XX-0000-000000", "2027-02-09"))) {
            JsonNode problem = problem(notFound, 404);
            assertThat(problem.get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        }
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);

        MvcResult owner = cancellations.preview("customer1", number, "2027-02-09");

        assertThat(owner.getResponse().getStatus()).as("the owner is let through").isEqualTo(200);
    }

    @Test
    @DisplayName("NFR-04.1: cancelling answers anonymous with 401 and no Basic challenge, underwriter1 and admin1 with 403, another customer, an unknown number and a malformed number with 404 and the fixed detail, and writes nothing; the owner is let through with 201")
    void onlyTheOwningCustomerCanCancel() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        Map<String, Object> body = cancelBody("NO_LONGER_NEEDED", "2027-02-09");

        MvcResult anonymous = cancellations.cancel(null, number, body);
        MvcResult underwriter = cancellations.cancel("underwriter1", number, body);
        MvcResult admin = cancellations.cancel("admin1", number, body);

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(anonymous.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(anonymous.getResponse().getHeader("WWW-Authenticate")).isNull();
        assertThat(underwriter.getResponse().getStatus()).isEqualTo(403);
        assertThat(admin.getResponse().getStatus()).isEqualTo(403);
        for (MvcResult notFound : List.of(cancellations.cancel("customer2", number, body),
                cancellations.cancel("customer1", "PF-TL-2026-999999", body),
                cancellations.cancel("customer1", "PF-XX-0000-000000", body))) {
            JsonNode problem = problem(notFound, 404);
            assertThat(problem.get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        }
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
        assertThat(CancellationRows.refunds(jdbc, number)).isEmpty();

        MvcResult owner = cancellations.cancel("customer1", number, body);

        assertThat(owner.getResponse().getStatus()).as(owner.getResponse().getContentAsString()).isEqualTo(201);
    }

    @Test
    @DisplayName("NFR-04.1: a wrong password on the preview and the cancel is 401 with no Basic challenge and writes nothing, so a browser is never asked for credentials to cache")
    void aWrongPasswordIsNotChallenged() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);

        MvcResult preview = mvc.perform(get(CancellationApi.previewPath(number) + "?date=2027-02-09")
                .with(httpBasic("customer1", "wrong-password"))).andReturn();
        MvcResult cancel = mvc.perform(post(CancellationApi.cancelPath(number))
                .contentType("application/json").content("{\"reason\":\"OTHER\"}")
                .with(httpBasic("customer1", "wrong-password"))).andReturn();

        for (MvcResult refused : List.of(preview, cancel)) {
            assertThat(refused.getResponse().getStatus()).isEqualTo(401);
            assertThat(refused.getResponse().getHeader("WWW-Authenticate")).isNull();
            assertThat(refused.getResponse().getContentType()).startsWith("application/problem+json");
        }
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.3: a text/plain or form-encoded cancel is 415 with the fixed detail and writes nothing, even though the same text is a valid JSON body, and the same body as JSON is accepted")
    void onlyJsonIsAccepted() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        String valid = "{\"reason\":\"NO_LONGER_NEEDED\",\"cancellationDate\":\"2027-02-09\"}";

        for (String contentType : List.of("text/plain", "application/x-www-form-urlencoded", "application/xml")) {
            MvcResult refused = cancellations.cancelRaw("customer1", number, contentType, valid);

            JsonNode problem = problem(refused, 415);
            assertThat(problem.get("detail").asText()).isEqualTo(FIXED_415_DETAIL);
        }

        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
        MvcResult accepted = cancellations.cancelRaw("customer1", number, "application/json", valid);
        assertThat(accepted.getResponse().getStatus()).as(accepted.getResponse().getContentAsString())
                .isEqualTo(201);
    }

    @Test
    @DisplayName("NFR-04.1: the preview is read-only and the cancel is POST only: a POST to the preview, and a GET, PUT, PATCH or DELETE to the cancel, are refused and write nothing")
    void thereIsNoOtherWayToCancel() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        String cancelUrl = CancellationApi.cancelPath(number);
        String previewUrl = CancellationApi.previewPath(number);

        List<MvcResult> refused = new ArrayList<>();
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            refused.add(mvc.perform(request(method, cancelUrl).contentType("application/json")
                    .content("{\"reason\":\"OTHER\"}").with(httpBasic("customer1", "password"))).andReturn());
        }
        refused.add(mvc.perform(post(previewUrl).contentType("application/json").content("{\"reason\":\"OTHER\"}")
                .with(httpBasic("customer1", "password"))).andReturn());

        for (MvcResult result : refused) {
            assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(405);
        }
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }
}
