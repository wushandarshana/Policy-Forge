package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationFixtures;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * No 401 carries a {@code WWW-Authenticate} header (NFR-04.1, plan P-14, contract C7). A Basic challenge makes a browser
 * open its credential dialog and cache what is typed, and the cached credentials are then attached to cross-site
 * requests (KD-007). The SPA signs in with its own form and sends preemptive Basic credentials, so the server never needs
 * to challenge. The 401 comes from three places, and each is covered: the security entry point (anonymous), the Basic
 * filter (a wrong password, including on a public path) and the exception handler (an authentication exception raised
 * inside a controller).
 */
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
@Import(ProblemProbeController.class)
class SecurityChallengeIT {

    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    /** A request that needs a signed-in user, named for the test report. The builder is rebuilt for each use. */
    record Call(String name, Supplier<MockHttpServletRequestBuilder> builder) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Call> protectedCalls() {
        return Stream.of(
                new Call("GET /api/policies/mine", () -> get("/api/policies/mine")),
                new Call("GET /api/applications/mine", () -> get("/api/applications/mine")),
                new Call("GET /api/underwriting/queue", () -> get("/api/underwriting/queue")),
                new Call("GET /api/admin/dashboard", () -> get("/api/admin/dashboard")),
                new Call("POST /api/applications/{id}/issue",
                        () -> post("/api/applications/A-1/issue").contentType("application/json").content("{}")),
                new Call("POST /api/policies/{number}/endorsements",
                        () -> post("/api/policies/PF-TL-2026-000001/endorsements").contentType("application/json")
                                .content("{}")),
                new Call("POST /api/underwriting/{id}/decision",
                        () -> post("/api/underwriting/A-1/decision").contentType("application/json").content("{}")),
                new Call("POST /api/products/{code}/versions",
                        () -> post("/api/products/MOTOR/versions").contentType("application/json").content("{}")));
    }

    private static void assertUnauthorisedWithoutChallenge(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(result.getResponse().getHeaderNames())
                .as("no 401 may ask the browser to prompt for and cache credentials")
                .noneMatch(WWW_AUTHENTICATE::equalsIgnoreCase);
        assertThat(result.getResponse().getHeader(WWW_AUTHENTICATE)).isNull();
    }

    @ParameterizedTest(name = "NFR-04.1: anonymous {0} is 401 problem+json with no WWW-Authenticate header")
    @MethodSource("protectedCalls")
    void anonymousRequestsGetNoChallenge(Call call) throws Exception {
        assertUnauthorisedWithoutChallenge(mvc.perform(call.builder().get()).andReturn());
    }

    @ParameterizedTest(name = "NFR-04.1: {0} with bad credentials is 401 problem+json with no WWW-Authenticate header")
    @MethodSource("protectedCalls")
    void badCredentialsGetNoChallenge(Call call) throws Exception {
        assertUnauthorisedWithoutChallenge(
                mvc.perform(call.builder().get().with(httpBasic("customer1", "not-the-password"))).andReturn());
        assertUnauthorisedWithoutChallenge(
                mvc.perform(call.builder().get().with(httpBasic("nobody", PASSWORD))).andReturn());
    }

    @Test
    @DisplayName("NFR-04.1: invalid credentials on the public POST /api/quotes are 401 problem+json with no WWW-Authenticate header, and no quote is stored")
    void badCredentialsOnQuoteCreationGetNoChallenge() throws Exception {
        int before = jdbc.queryForObject("select count(*) from quote", Integer.class);

        MvcResult result = mvc.perform(post("/api/quotes").with(httpBasic("customer1", "not-the-password"))
                .contentType("application/json").content(json.writeValueAsString(quoteBody()))).andReturn();

        assertUnauthorisedWithoutChallenge(result);
        assertThat(jdbc.queryForObject("select count(*) from quote", Integer.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.1: an authentication exception raised inside a controller is 401 problem+json with no WWW-Authenticate header")
    void exceptionHandlerGetsNoChallenge() throws Exception {
        MvcResult result = mvc.perform(get("/api/_problem/unauthenticated").with(httpBasic("customer1", PASSWORD)))
                .andReturn();

        assertUnauthorisedWithoutChallenge(result);
    }

    @Test
    @DisplayName("NFR-04.1: valid preemptive Basic credentials still succeed on a protected GET and on the public POST /api/quotes")
    void preemptiveBasicStillWorks() throws Exception {
        MvcResult read = mvc.perform(get("/api/policies/mine").with(httpBasic("customer1", PASSWORD))).andReturn();
        MvcResult created = mvc.perform(post("/api/quotes").with(httpBasic("customer1", PASSWORD))
                .contentType("application/json").content(json.writeValueAsString(quoteBody()))).andReturn();

        assertThat(read.getResponse().getStatus()).isEqualTo(200);
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(read.getResponse().getHeader(WWW_AUTHENTICATE)).isNull();
    }

    private static Map<String, Object> quoteBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", "TERM_LIFE");
        body.put("riskInputs", ApplicationFixtures.cleanTermLifeInputs());
        return body;
    }
}
