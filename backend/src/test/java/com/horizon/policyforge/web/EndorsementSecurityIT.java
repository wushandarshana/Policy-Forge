package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.EndorsementRows;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The authentication and request-format boundary of the endorsement endpoints (NFR-04.1, NFR-04.3). The anonymous 401s
 * and the role 403s on well-formed requests are also part of the parameterised {@code RoleBoundaryIT}. Here the policy
 * is real, so a 403 is proved to come from the role and not from a missing policy, and nothing is written by any
 * refused request.
 */
@Tag("NFR-04")
class EndorsementSecurityIT extends EndorsementTestSupport {

    @Test
    @DisplayName("NFR-04.1: endorse and preview refuse underwriter1 and admin1 with 403 on a real policy, and answer another customer and an unknown policy with 404, and write nothing")
    void onlyTheOwningCustomerCanEndorseOrPreview() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);

        for (String user : List.of("underwriter1", "admin1")) {
            assertThat(endorsements.endorse(user, number, newAddressBody(null)).getResponse().getStatus())
                    .as(user + " endorse").isEqualTo(403);
            assertThat(endorsements.preview(user, number, newAddressBody(null)).getResponse().getStatus())
                    .as(user + " preview").isEqualTo(403);
        }
        for (MvcResult notFound : List.of(endorsements.endorse("customer2", number, newAddressBody(null)),
                endorsements.preview("customer2", number, newAddressBody(null)),
                endorsements.endorse("customer1", "PF-TL-2026-999999", newAddressBody(null)),
                endorsements.preview("customer1", "PF-TL-2026-999999", newAddressBody(null)))) {
            JsonNode problem = problem(notFound, 404);
            assertThat(problem.get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        }

        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
        assertThat(endorsements.endorse("customer1", number, newAddressBody(null)).getResponse().getStatus())
                .as("the owner is let through").isEqualTo(201);
    }

    @Test
    @DisplayName("NFR-04.1: an anonymous endorse or preview of a real policy is 401 with a Basic challenge, and writes nothing")
    void anonymousRequestsAreRefused() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);

        for (String path : List.of("/api/policies/" + number + "/endorsements",
                "/api/policies/" + number + "/endorsements/preview")) {
            MvcResult result = mvc.perform(post(path).contentType("application/json").content(json.writeValueAsString(newAddressBody(null))))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(401);
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
            assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Basic");
        }
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.3: a text/plain or form-encoded endorse or preview is 415 with the fixed detail, and changes nothing, even though the same text is a valid JSON body")
    void onlyJsonIsAccepted() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);
        String validJsonText = json.writeValueAsString(newAddressBody(null));

        for (String contentType : List.of("text/plain", "application/x-www-form-urlencoded")) {
            MvcResult endorse = endorsements.endorseRaw("customer1", number, contentType, validJsonText);
            MvcResult preview = endorsements.previewRaw("customer1", number, contentType, validJsonText);

            for (MvcResult refused : List.of(endorse, preview)) {
                JsonNode problem = problem(refused, 415);
                assertThat(problem.get("detail").asText()).isEqualTo("The request content type must be application/json.");
            }
        }

        assertThat(snapshot(number)).as("nothing changed").isEqualTo(before);
        assertThat(endorsements.endorse("customer1", number, newAddressBody(null)).getResponse().getStatus())
                .as("the same body as JSON is accepted").isEqualTo(201);
    }
}
