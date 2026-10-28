package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NFR-04 security baseline (sprint J12: the baseline is configured here, NFR-04.1 itself is not claimed yet because
 * the underwriter endpoints do not exist). Public paths must never answer 401; every other /api path must.
 */
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBaselineIT {

    private static final String QUOTE_PATH = "/api/quotes/Q-00000000-0000-4000-8000-000000000000";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserDetailsService users;

    @Test
    @DisplayName("NFR-04 baseline: anonymous /actuator/health is allowed")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("NFR-04 baseline: the public API paths are not behind authentication")
    void publicApiPathsAreNotAuthenticated() throws Exception {
        int[] statuses = {
            mvc.perform(get("/api/products")).andReturn().getResponse().getStatus(),
            mvc.perform(get("/api/products/MOTOR/versions")).andReturn().getResponse().getStatus(),
            mvc.perform(post("/api/quotes").contentType("application/json").content("{}"))
                    .andReturn().getResponse().getStatus(),
            mvc.perform(get(QUOTE_PATH)).andReturn().getResponse().getStatus(),
        };
        for (int status : statuses) {
            assertThat(status).as("public path status").isNotIn(401, 403);
        }
    }

    @Test
    @DisplayName("NFR-04 baseline: the SPA entry point and static assets are not behind authentication")
    void staticAssetsAreNotAuthenticated() throws Exception {
        int[] statuses = {
            mvc.perform(get("/")).andReturn().getResponse().getStatus(),
            mvc.perform(get("/index.html")).andReturn().getResponse().getStatus(),
            mvc.perform(get("/assets/app.js")).andReturn().getResponse().getStatus(),
        };
        for (int status : statuses) {
            assertThat(status).as("static path status").isNotIn(401, 403);
        }
    }

    @ParameterizedTest(name = "NFR-04 baseline: anonymous GET {0} is 401")
    @ValueSource(strings = {
        "/api/quotes",
        "/api/applications/mine",
        "/api/policies/mine",
        "/api/admin/dashboard",
        "/api/underwriting/queue",
        "/api/anything-else",
    })
    void otherApiPathsRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Basic")));
    }

    @Test
    @DisplayName("NFR-04 baseline: write methods on the public prefixes still require authentication")
    void writesOnPublicPrefixesRequireAuthentication() throws Exception {
        mvc.perform(post("/api/products/MOTOR/versions").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(QUOTE_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(delete(QUOTE_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(QUOTE_PATH)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "NFR-04 baseline: {0} authenticates with HTTP Basic")
    @ValueSource(strings = {"customer1", "customer2", "underwriter1", "admin1"})
    void syntheticUsersAuthenticate(String username) throws Exception {
        int status = mvc.perform(get("/api/anything-else").with(httpBasic(username, "password")))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("authenticated request must get past the security filter").isNotIn(401, 403);
    }

    @Test
    @DisplayName("NFR-04 baseline: wrong credentials are rejected with 401")
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(get("/api/anything-else").with(httpBasic("customer1", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("NFR-04 baseline: users carry the roles from app_spec section 2")
    void usersCarryTheDocumentedRoles() {
        assertThat(rolesOf("customer1")).containsExactlyInAnyOrder("ROLE_CUSTOMER");
        assertThat(rolesOf("customer2")).containsExactlyInAnyOrder("ROLE_CUSTOMER");
        assertThat(rolesOf("underwriter1")).containsExactlyInAnyOrder("ROLE_UNDERWRITER");
        assertThat(rolesOf("admin1"))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_UNDERWRITER", "ROLE_PRODUCT_MANAGER");
    }

    private Set<String> rolesOf(String username) {
        return users.loadUserByUsername(username).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    @Tag("NFR-04")
    @DisplayName("NFR-04.2: an unknown product's version list is a 404 problem+json whose detail does not echo the requested code")
    void notFoundDetailDoesNotEchoInput() throws Exception {
        var result = mvc.perform(get("/api/products/ZZQINJECT/versions")).andReturn();

        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                .startsWith("application/problem+json");
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("ZZQINJECT");
    }

    @Test
    @Tag("NFR-04")
    @DisplayName("NFR-04.2: an anonymous GET of an unlisted path under /api/products is a 401, so only the two public GET paths are anonymous")
    void unlistedProductsPathIsNotAnonymous() throws Exception {
        mvc.perform(get("/api/products/MOTOR/internal")).andExpect(status().isUnauthorized());
    }
}
