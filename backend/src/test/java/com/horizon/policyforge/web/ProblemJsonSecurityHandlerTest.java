package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * The 401 and 403 written inside the security filter chain, where the exception handler is not reached (NFR-04.1): a
 * fixed {@code instance} URN that never repeats the request path, and no {@code WWW-Authenticate} header on the 401.
 */
@Tag("NFR-04")
class ProblemJsonSecurityHandlerTest {

    private static final String PATH_WITH_MARKER = "/api/policies/zzmarker0x7f3a/endorsements";

    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    private final ProblemJsonSecurityHandler handler = new ProblemJsonSecurityHandler(json);

    @Test
    @DisplayName("NFR-04.1: the authentication entry point writes a 401 with the fixed unauthorized instance and no WWW-Authenticate header")
    void entryPointWritesAFixedInstanceAndNoChallenge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH_WITH_MARKER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new BadCredentialsException("probe"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode problem = json.readTree(response.getContentAsString());
        assertThat(problem.get("instance").asText()).isEqualTo("urn:policyforge:problem:unauthorized");
        assertThat(response.getContentAsString()).doesNotContain("zzmarker0x7f3a");
    }

    @Test
    @DisplayName("NFR-04.1: the access denied handler writes a 403 with the fixed forbidden instance")
    void accessDeniedHandlerWritesAFixedInstance() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH_WITH_MARKER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("probe"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode problem = json.readTree(response.getContentAsString());
        assertThat(problem.get("instance").asText()).isEqualTo("urn:policyforge:problem:forbidden");
        assertThat(response.getContentAsString()).doesNotContain("zzmarker0x7f3a");
    }
}
