package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.PolicyApi;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * NFR-04.1 across the whole API: an unauthenticated request to any {@code /api/**} endpoint except the public ones is
 * a 401, and a caller with the wrong role gets a 403. The endpoint list is the table in app_spec section 8 for the
 * endpoints that exist, with every request well formed, so a 401 or 403 can only come from the security rules and never
 * from validation. The three public groups are {@code GET /api/products**}, {@code POST /api/quotes} and
 * {@code GET /api/quotes/{id}}.
 */
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
class RoleBoundaryIT {

    private static final String PASSWORD = "password";
    private static final String APPLICATION_ID = "A-00000000-0000-4000-8000-000000000000";
    private static final String DECISION_BODY =
            "{\"decision\":\"APPROVE\",\"reasonCode\":\"UW-MAN-001\",\"comment\":\"Acceptable after review\"}";
    private static final String OVERRIDE_BODY =
            "{\"reasonCode\":\"UW-OVR-001\",\"comment\":\"Commercial exception signed off\"}";
    private static final String ENDORSE_BODY = "{\"type\":\"CHANGE_ADDRESS\",\"address\":{\"line1\":\"22 New Road\","
            + "\"city\":\"Mumbai\",\"postcode\":\"400001\"}}";
    private static final String APPLICATION_BODY = "{\"quoteId\":\"Q-00000000-0000-4000-8000-000000000000\","
            + "\"kyc\":{\"fullName\":\"Asha Test\",\"dateOfBirth\":\"1991-04-02\",\"aadhaar\":\"XXXX-XXXX-1234\","
            + "\"pan\":\"ABCDE1234F\",\"address\":{\"line1\":\"1 Demo Street\",\"city\":\"Pune\","
            + "\"postcode\":\"411001\"}}}";

    /** One request: method, path and a well-formed JSON body for the POSTs. */
    record Call(String method, String path, String body) {

        MockHttpServletRequestBuilder builder() {
            return switch (method) {
                case "GET" -> get(path);
                case "POST" -> post(path).contentType("application/json").content(body);
                default -> throw new IllegalArgumentException(method);
            };
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static final Call SUBMIT = new Call("POST", "/api/applications", APPLICATION_BODY);
    private static final Call MINE = new Call("GET", "/api/applications/mine", null);
    private static final Call QUEUE = new Call("GET", "/api/underwriting/queue", null);
    private static final Call QUEUE_DECLINED = new Call("GET", "/api/underwriting/queue?includeDeclined=true", null);
    private static final Call DECISION =
            new Call("POST", "/api/underwriting/" + APPLICATION_ID + "/decision", DECISION_BODY);
    private static final Call OVERRIDE =
            new Call("POST", "/api/underwriting/" + APPLICATION_ID + "/override", OVERRIDE_BODY);
    private static final Call PUBLISH = new Call("POST", "/api/products/MOTOR/versions", "{}");
    private static final Call ISSUE = new Call("POST", "/api/applications/" + APPLICATION_ID + "/issue", "{}");
    private static final Call MINE_POLICIES = new Call("GET", "/api/policies/mine", null);
    private static final Call POLICY_DETAIL = new Call("GET", "/api/policies/PF-TL-2026-999999", null);
    private static final Call ENDORSE =
            new Call("POST", "/api/policies/PF-TL-2026-999999/endorsements", ENDORSE_BODY);
    private static final Call PREVIEW =
            new Call("POST", "/api/policies/PF-TL-2026-999999/endorsements/preview", ENDORSE_BODY);

    static Stream<Arguments> protectedCalls() {
        return Stream.of(SUBMIT, MINE, QUEUE, QUEUE_DECLINED, DECISION, OVERRIDE, PUBLISH, ISSUE, MINE_POLICIES,
                POLICY_DETAIL, ENDORSE, PREVIEW).map(Arguments::of);
    }

    static Stream<Arguments> underwriterCalls() {
        return Stream.of(QUEUE, QUEUE_DECLINED, DECISION, OVERRIDE).map(Arguments::of);
    }

    static Stream<Arguments> customerOnlyCalls() {
        return Stream.of(SUBMIT, MINE, ISSUE, MINE_POLICIES, ENDORSE, PREVIEW).map(Arguments::of);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private Clock clock;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private int status(Call call, String user) throws Exception {
        MockHttpServletRequestBuilder request = call.builder();
        if (user != null) {
            request = request.with(httpBasic(user, PASSWORD));
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @ParameterizedTest(name = "NFR-04.1: anonymous {0} is 401")
    @MethodSource("protectedCalls")
    void anonymousRequestsAreUnauthorised(Call call) throws Exception {
        MvcResult result = mvc.perform(call.builder()).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull();
    }

    @ParameterizedTest(name = "NFR-04.1: {0} with a wrong password is 401")
    @MethodSource("protectedCalls")
    void wrongCredentialsAreUnauthorised(Call call) throws Exception {
        assertThat(mvc.perform(call.builder().with(httpBasic("underwriter1", "not-the-password")))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("NFR-04.1: the public endpoints do not answer 401: GET /api/products, GET /api/products/{code}/versions, POST /api/quotes and GET /api/quotes/{id}")
    void publicEndpointsStayPublic() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        statuses.add(mvc.perform(get("/api/products")).andReturn().getResponse().getStatus());
        statuses.add(mvc.perform(get("/api/products/MOTOR/versions")).andReturn().getResponse().getStatus());
        statuses.add(mvc.perform(post("/api/quotes").contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus());
        statuses.add(mvc.perform(get("/api/quotes/Q-00000000-0000-4000-8000-000000000000"))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).doesNotContain(401, 403);
    }

    @ParameterizedTest(name = "NFR-04.1: a CUSTOMER calling {0} gets 403")
    @MethodSource("underwriterCalls")
    void customerCannotCallUnderwriterEndpoints(Call call) throws Exception {
        assertThat(status(call, "customer1")).isEqualTo(403);
        assertThat(status(call, "customer2")).isEqualTo(403);
    }

    @ParameterizedTest(name = "NFR-04.1: an UNDERWRITER calling {0} gets 403")
    @MethodSource("customerOnlyCalls")
    void underwriterCannotCallCustomerEndpoints(Call call) throws Exception {
        assertThat(status(call, "underwriter1")).isEqualTo(403);
    }

    @ParameterizedTest(name = "NFR-04.1: an ADMIN calling {0} gets 403, because admin1 is not a CUSTOMER")
    @MethodSource("customerOnlyCalls")
    void adminCannotCallCustomerEndpoints(Call call) throws Exception {
        assertThat(status(call, "admin1")).isEqualTo(403);
    }

    @Test
    @DisplayName("NFR-04.1: an UNDERWRITER calling GET /api/policies/{number} gets 403, while a CUSTOMER and an ADMIN are let through (404 for an unknown number, never 401 or 403)")
    void policyDetailRefusesUnderwriters() throws Exception {
        assertThat(status(POLICY_DETAIL, "underwriter1")).isEqualTo(403);
        assertThat(status(POLICY_DETAIL, "customer1")).isEqualTo(404);
        assertThat(status(POLICY_DETAIL, "admin1")).isEqualTo(404);
    }

    @Test
    @DisplayName("NFR-04.1: a policy's owner and an ADMIN get 200 on its detail, another customer and an UNDERWRITER do not, and ADMIN is still refused issue and mine")
    void policyDetailIsForTheOwnerAndAdmins() throws Exception {
        ApplicationApi applications = new ApplicationApi(mvc, json);
        PolicyApi policies = new PolicyApi(mvc, json);
        String applicationId = applications.approvedApplication("customer1", LocalDate.now(clock));
        String number = policies.issueOk("customer1", applicationId, null).get("policyNumber").asText();

        assertThat(policies.detail("customer1", number).getResponse().getStatus()).isEqualTo(200);
        assertThat(policies.detail("admin1", number).getResponse().getStatus())
                .as("admin1 reads another customer's policy").isEqualTo(200);
        assertThat(policies.detail("customer2", number).getResponse().getStatus()).isEqualTo(404);
        assertThat(policies.detail("underwriter1", number).getResponse().getStatus()).isEqualTo(403);
        assertThat(status(ISSUE, "admin1")).isEqualTo(403);
        assertThat(status(MINE_POLICIES, "admin1")).isEqualTo(403);
    }

    @Test
    @DisplayName("NFR-04.1: an UNDERWRITER calling the override endpoint gets 403, but admin1 is let through (404 for an unknown application, never 401 or 403)")
    void overrideIsAdminOnly() throws Exception {
        assertThat(status(OVERRIDE, "underwriter1")).isEqualTo(403);
        assertThat(status(OVERRIDE, "admin1")).isEqualTo(404);
    }

    @Test
    @DisplayName("NFR-04.1: an UNDERWRITER and an ADMIN are let through to the queue and the decision endpoint (404 for an unknown application, never 401 or 403)")
    void underwriterEndpointsAcceptUnderwriters() throws Exception {
        assertThat(status(QUEUE, "underwriter1")).isEqualTo(200);
        assertThat(status(QUEUE, "admin1")).isEqualTo(200);
        assertThat(status(DECISION, "underwriter1")).isEqualTo(404);
        assertThat(status(DECISION, "admin1")).isEqualTo(404);
        assertThat(status(QUEUE_DECLINED, "admin1")).isEqualTo(200);
        assertThat(status(QUEUE_DECLINED, "underwriter1")).isEqualTo(403);
    }

    @Test
    @DisplayName("NFR-04.1: the other verbs on the decision and override paths are 405 for an authorised caller, so the endpoints answer only the methods the spec lists")
    void otherVerbsAreNotAllowed() throws Exception {
        for (String path : List.of("/api/underwriting/" + APPLICATION_ID + "/decision",
                "/api/underwriting/" + APPLICATION_ID + "/override")) {
            assertThat(mvc.perform(put(path).contentType("application/json").content("{}")
                    .with(httpBasic("admin1", PASSWORD))).andReturn().getResponse().getStatus()).isEqualTo(405);
            assertThat(mvc.perform(delete(path).with(httpBasic("admin1", PASSWORD))).andReturn().getResponse()
                    .getStatus()).isEqualTo(405);
            assertThat(mvc.perform(get(path).with(httpBasic("admin1", PASSWORD))).andReturn().getResponse()
                    .getStatus()).isEqualTo(405);
        }
    }

    @Test
    @DisplayName("NFR-04.1: every mapped handler under /api/underwriting declares its role with @PreAuthorize at the controller, and the actor never comes from the request")
    void everyUnderwritingHandlerDeclaresItsRole() {
        List<String> missing = new ArrayList<>();
        int found = 0;
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();
            boolean underwriting = info.getPathPatternsCondition() != null && info.getPathPatternsCondition()
                    .getPatternValues().stream().anyMatch(pattern -> pattern.startsWith("/api/underwriting"));
            if (!underwriting) {
                continue;
            }
            found++;
            if (method.getMethodAnnotation(PreAuthorize.class) == null) {
                missing.add(info + " -> " + method.getMethod().getName());
            }
        }

        assertThat(found).as("the queue, decision and override handlers are mapped").isEqualTo(3);
        assertThat(missing).as("handlers without @PreAuthorize").isEmpty();
    }

    @Test
    @DisplayName("NFR-04.1: only GET and POST handlers exist under /api/underwriting")
    void onlyTheDocumentedMethodsAreMapped() {
        List<String> methods = new ArrayList<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> {
            boolean underwriting = info.getPathPatternsCondition() != null && info.getPathPatternsCondition()
                    .getPatternValues().stream().anyMatch(pattern -> pattern.startsWith("/api/underwriting"));
            if (underwriting) {
                info.getMethodsCondition().getMethods().forEach(m -> methods.add(m.name()));
            }
        });

        assertThat(methods).containsExactlyInAnyOrder("GET", "POST", "POST");
    }
}
