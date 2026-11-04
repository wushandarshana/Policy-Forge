package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Every problem+json {@code instance} is a fixed URN, never derived from the request (NFR-04.1, plan P-14, contract C7).
 * Spring fills a missing instance with the request path, and a path can carry anything a caller types: an application ID,
 * a policy number, a marker. Two proofs:
 * <ul>
 *   <li>per problem type: each error the API can produce has the URN of its own type, whichever component writes it (the
 *       exception handler, the security chain, the body-limit filter or the framework);</li>
 *   <li>per route: a marker sent as a path segment and as a query value to the issue, endorse, preview, application,
 *       decision and override routes never comes back in any response header or body, for 400, 401, 403, 404, 405, 413
 *       and 415.</li>
 * </ul>
 */
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
@Import(ProblemProbeController.class)
class ProblemInstanceIT {

    private static final String URN_PREFIX = "urn:policyforge:problem:";
    private static final String URN_PATTERN = "^urn:policyforge:problem:[a-z-]+$";
    private static final String PASSWORD = "password";
    private static final String MARKER = "zzmarker0x7f3a";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String JSON = "application/json";

    /** Over the 16 KB default cap of NFR-04.4. */
    private static final String OVERSIZED_BODY = "{\"pad\":\"" + "x".repeat(17 * 1024) + "\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    // ---- the URN of each problem type ----

    /** One error to raise, the status it must have and the slug of its URN (the last segment of the problem type). */
    record Case(String name, Supplier<MockHttpServletRequestBuilder> request, int status, String slug) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder builder) {
        return builder.with(httpBasic("customer1", PASSWORD));
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case("400 validation failed (service)",
                        () -> asCustomer(get("/api/_problem/validation")), 400, "validation-failed"),
                new Case("400 validation failed (public quote with no fields)",
                        () -> post("/api/quotes").contentType(JSON).content("{}"), 400, "validation-failed"),
                new Case("400 framework: unreadable body",
                        () -> post("/api/quotes").contentType(JSON).content("{not json"), 400, "bad-request"),
                new Case("400 framework: missing request parameter",
                        () -> asCustomer(get("/api/_problem/param")), 400, "bad-request"),
                new Case("401 anonymous (security entry point)",
                        () -> get("/api/policies/mine"), 401, "unauthorized"),
                new Case("401 bad credentials (Basic filter)",
                        () -> get("/api/policies/mine").with(httpBasic("customer1", "wrong")), 401, "unauthorized"),
                new Case("401 authentication exception (exception handler)",
                        () -> asCustomer(get("/api/_problem/unauthenticated")), 401, "unauthorized"),
                new Case("403 wrong role (method security)",
                        () -> get("/api/policies/mine").with(httpBasic("underwriter1", PASSWORD)), 403, "forbidden"),
                new Case("403 access denied (exception handler)",
                        () -> asCustomer(get("/api/_problem/denied")), 403, "forbidden"),
                new Case("404 not found (service)",
                        () -> asCustomer(get("/api/_problem/not-found")), 404, "not-found"),
                new Case("404 framework: no such path",
                        () -> asCustomer(get("/api/" + MARKER + "/nothing-here")), 404, "not-found"),
                new Case("405 framework: method not supported",
                        () -> asCustomer(delete("/api/applications/mine")), 405, "method-not-allowed"),
                new Case("409 conflict",
                        () -> asCustomer(get("/api/_problem/conflict")), 409, "conflict"),
                new Case("409 invalid policy state",
                        () -> asCustomer(get("/api/_problem/invalid-state")), 409, "invalid-policy-state"),
                new Case("413 body too large (body-limit filter)",
                        () -> post("/api/quotes").contentType(JSON).content(OVERSIZED_BODY), 413,
                        "payload-too-large"),
                new Case("415 framework: unsupported media type",
                        () -> post("/api/quotes").contentType("text/plain").content("{}"), 415,
                        "unsupported-media-type"),
                new Case("500 unexpected error (exception handler)",
                        () -> asCustomer(get("/api/_problem/boom")), 500, "internal-error"),
                new Case("500 framework: error response",
                        () -> asCustomer(get("/api/_problem/framework-500")), 500, "internal-server-error"),
                new Case("503 policy number sequence exhausted",
                        () -> asCustomer(get("/api/_problem/exhausted")), 503,
                        "policy-number-sequence-exhausted"));
    }

    @ParameterizedTest(name = "NFR-04.1: {0} has the fixed instance of its problem type")
    @MethodSource("cases")
    void everyProblemHasTheFixedInstanceOfItsType(Case failing) throws Exception {
        MvcResult result = mvc.perform(failing.request().get()).andReturn();

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isEqualTo(failing.status());
        assertThat(result.getResponse().getContentType()).startsWith(PROBLEM_JSON);
        JsonNode problem = json.readTree(result.getResponse().getContentAsString());
        assertThat(problem.get("instance").asText()).isEqualTo(URN_PREFIX + failing.slug()).matches(URN_PATTERN);
    }

    @Test
    @DisplayName("NFR-04.1: a 406 (an Accept the API cannot satisfy) carries no instance but a URN, and never the request path")
    void notAcceptableHasNoPathInstance() throws Exception {
        MvcResult result = mvc.perform(get("/api/products").queryParam("q", MARKER).accept("application/xml"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(406);
        assertThat(everythingSent(result.getResponse())).doesNotContain(MARKER).doesNotContain("/api/products");
        String body = result.getResponse().getContentAsString();
        if (!body.isEmpty()) {
            assertThat(json.readTree(body).get("instance").asText()).isEqualTo(URN_PREFIX + "not-acceptable");
        }
    }

    @Test
    @DisplayName("NFR-04.1: the 503 for an exhausted policy number sequence names its business code and a fixed detail")
    void exhaustedSequenceHasItsCodeAndDetail() throws Exception {
        MvcResult result = mvc.perform(asCustomer(get("/api/_problem/exhausted"))).andReturn();

        JsonNode problem = json.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(problem.get("code").asText()).isEqualTo("POLICY_NUMBER_SEQUENCE_EXHAUSTED");
        assertThat(problem.get("title").asText()).isNotBlank();
        assertThat(problem.get("detail").asText()).isNotBlank();
    }

    // ---- a marker in the path and the query never comes back ----

    /** One request to a route with the marker in its path segment and its query, and the status it must get. */
    record Probe(String name, HttpMethod method, String path, String user, String contentType, String body,
            int status) {
        @Override
        public String toString() {
            return name;
        }

        MockHttpServletRequestBuilder builder() {
            MockHttpServletRequestBuilder builder = request(method, path);
            if (user != null) {
                builder = builder.with(httpBasic(user, PASSWORD));
            }
            if (contentType != null) {
                builder = builder.contentType(contentType).content(body);
            }
            return builder;
        }
    }

    private static final String ADDRESS_BODY = "{\"type\":\"CHANGE_ADDRESS\",\"address\":"
            + "{\"line1\":\"1 Demo Street\",\"city\":\"Pune\",\"postcode\":\"411001\"}}";
    private static final String ADDRESS_BODY_WITH_UNKNOWN_KEY = "{\"type\":\"CHANGE_ADDRESS\",\"unexpectedKey\":1,"
            + "\"address\":{\"line1\":\"1 Demo Street\",\"city\":\"Pune\",\"postcode\":\"411001\"}}";
    private static final String DECISION_BODY = "{\"decision\":\"APPROVE\",\"reasonCode\":\"UW-MAN-001\","
            + "\"comment\":\"Acceptable after medical review\"}";
    private static final String DECISION_BODY_WITH_UNKNOWN_KEY = "{\"decision\":\"APPROVE\","
            + "\"reasonCode\":\"UW-MAN-001\",\"comment\":\"Acceptable after medical review\",\"unexpectedKey\":1}";
    private static final String OVERRIDE_BODY = "{\"reasonCode\":\"UW-OVR-001\","
            + "\"comment\":\"Commercial exception signed off\"}";
    private static final String OVERRIDE_BODY_WITH_UNKNOWN_KEY = "{\"reasonCode\":\"UW-OVR-001\","
            + "\"comment\":\"Commercial exception signed off\",\"unexpectedKey\":1}";

    /** A POST route: who may call it, who may not, a body that passes the shape checks and one with an unknown key. */
    private record PostRoute(String name, String path, String user, String otherUser, String validBody,
            String unknownKeyBody) {
    }

    static Stream<Probe> markerProbes() {
        String query = "?q=" + MARKER;
        List<Probe> probes = new ArrayList<>();
        List<PostRoute> routes = List.of(
                new PostRoute("issue", "/api/applications/" + MARKER + "/issue", "customer1", "underwriter1", "{}",
                        "{\"unexpectedKey\":1}"),
                new PostRoute("endorse", "/api/policies/" + MARKER + "/endorsements", "customer1", "underwriter1",
                        ADDRESS_BODY, ADDRESS_BODY_WITH_UNKNOWN_KEY),
                new PostRoute("preview", "/api/policies/" + MARKER + "/endorsements/preview", "customer1",
                        "underwriter1", ADDRESS_BODY, ADDRESS_BODY_WITH_UNKNOWN_KEY),
                new PostRoute("decision", "/api/underwriting/" + MARKER + "/decision", "underwriter1", "customer1",
                        DECISION_BODY, DECISION_BODY_WITH_UNKNOWN_KEY),
                new PostRoute("override", "/api/underwriting/" + MARKER + "/override", "admin1", "underwriter1",
                        OVERRIDE_BODY, OVERRIDE_BODY_WITH_UNKNOWN_KEY));
        for (PostRoute route : routes) {
            String path = route.path() + query;
            probes.add(new Probe(route.name() + " 400", HttpMethod.POST, path, route.user(), JSON,
                    route.unknownKeyBody(), 400));
            probes.add(new Probe(route.name() + " 401", HttpMethod.POST, path, null, JSON, route.validBody(), 401));
            probes.add(new Probe(route.name() + " 403", HttpMethod.POST, path, route.otherUser(), JSON,
                    route.validBody(), 403));
            probes.add(new Probe(route.name() + " 404", HttpMethod.POST, path, route.user(), JSON,
                    route.validBody(), 404));
            probes.add(new Probe(route.name() + " 405", HttpMethod.GET, path, route.user(), null, null, 405));
            probes.add(new Probe(route.name() + " 413", HttpMethod.POST, path, route.user(), JSON, OVERSIZED_BODY,
                    413));
            probes.add(new Probe(route.name() + " 415", HttpMethod.POST, path, route.user(), "text/plain",
                    route.validBody(), 415));
        }
        // The application-status route: the caller's applications (GET /api/applications/mine) and a lookup by ID.
        probes.add(new Probe("application-status 401", HttpMethod.GET, "/api/applications/mine" + query, null, null,
                null, 401));
        probes.add(new Probe("application-status 403", HttpMethod.GET, "/api/applications/mine" + query,
                "underwriter1", null, null, 403));
        probes.add(new Probe("application-status 404", HttpMethod.GET, "/api/applications/" + MARKER + query,
                "customer1", null, null, 404));
        probes.add(new Probe("application-status 405", HttpMethod.DELETE, "/api/applications/mine" + query,
                "customer1", null, null, 405));
        return probes.stream();
    }

    @ParameterizedTest(name = "NFR-04.1: {0}: the marker is in no response header or body, and any instance is a fixed URN")
    @MethodSource("markerProbes")
    void aMarkerInThePathAndQueryIsNeverEchoed(Probe probe) throws Exception {
        MvcResult result = mvc.perform(probe.builder()).andReturn();

        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(probe.status());
        assertThat(everythingSent(response)).as("headers and body of the response").doesNotContain(MARKER);
        String body = response.getContentAsString();
        if (!body.isEmpty()) {
            JsonNode problem = json.readTree(body);
            assertThat(problem.get("instance").asText()).matches(URN_PATTERN);
        }
    }

    @Test
    @DisplayName("NFR-04.1: the marker probes cover 400, 401, 403, 404, 405, 413 and 415 on the issue, endorse, preview, application-status, decision and override routes")
    void theProbesCoverEveryStatusOnEveryRoute() {
        List<Probe> probes = markerProbes().toList();

        Set<Integer> statuses = probes.stream().map(Probe::status).collect(Collectors.toSet());
        assertThat(statuses).containsExactlyInAnyOrder(400, 401, 403, 404, 405, 413, 415);
        for (String route : List.of("issue", "endorse", "preview", "application-status", "decision", "override")) {
            assertThat(probes).as(route).anyMatch(probe -> probe.name().startsWith(route + " "));
        }
    }

    /** Every header name and value, and the body, as one string. */
    private static String everythingSent(MockHttpServletResponse response) throws Exception {
        StringBuilder all = new StringBuilder();
        for (String name : response.getHeaderNames()) {
            all.append(name).append(": ").append(String.join(",", response.getHeaders(name))).append('\n');
        }
        all.append(response.getContentAsString());
        return all.toString();
    }
}
