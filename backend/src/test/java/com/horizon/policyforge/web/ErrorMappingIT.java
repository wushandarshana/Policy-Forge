package com.horizon.policyforge.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horizon.policyforge.service.ResourceNotFoundException;
import com.horizon.policyforge.service.ValidationFailedException;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/**
 * Central error mapping (contract C3: RFC 7807 problem+json, validation as 400 with errors[] of {field, message})
 * plus the 401/403 half of the NFR-04 authentication boundary. A probe controller that exists only in this test class
 * raises each kind of error through the real MVC and security stack.
 */
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
@Import(ErrorMappingIT.ProbeController.class)
class ErrorMappingIT {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Contract C3: bean validation failures are 400 problem+json with errors[] of {field, message}")
    void beanValidationFailureListsEveryField() throws Exception {
        mvc.perform(post("/api/_probe/validate")
                        .with(httpBasic("customer1", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"count\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "count")))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    @DisplayName("Contract C3: validation errors never echo the submitted value")
    void validationMessagesDoNotEchoValues() throws Exception {
        mvc.perform(post("/api/_probe/validate")
                        .with(httpBasic("customer1", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"count\":-987654}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("987654"))));
    }

    @Test
    @DisplayName("Contract C3: a ValidationFailedException from a service becomes 400 with errors[]")
    void serviceValidationFailureBecomes400() throws Exception {
        mvc.perform(get("/api/_probe/service-validation").with(httpBasic("customer1", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("sumInsured"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at most 100"))
                .andExpect(jsonPath("$.errors[1].field").value("termYears"));
    }

    @Test
    @DisplayName("Contract C3: constraint violations on request parameters are 400 with errors[]")
    void parameterConstraintViolationBecomes400() throws Exception {
        mvc.perform(get("/api/_probe/param").param("count", "0").with(httpBasic("customer1", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("count"));
    }

    @Test
    @DisplayName("Contract C3: a malformed JSON body is 400 problem+json")
    void malformedBodyIs400() throws Exception {
        mvc.perform(post("/api/_probe/validate")
                        .with(httpBasic("customer1", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    @DisplayName("Contract C3: a ResourceNotFoundException is 404 problem+json")
    void resourceNotFoundIs404() throws Exception {
        mvc.perform(get("/api/_probe/missing").with(httpBasic("customer1", "password")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("/problems/not-found"));
    }

    @Test
    @DisplayName("Contract C3: an unknown /api path for a signed-in user is 404 problem+json")
    void unknownPathIs404() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(httpBasic("customer1", "password")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    @DisplayName("Contract C3: an unexpected exception is 500 problem+json without internal detail")
    void unexpectedErrorIs500WithoutLeaking() throws Exception {
        mvc.perform(get("/api/_probe/boom").with(httpBasic("customer1", "password")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(content().string(not(containsString("secret-internal-detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("NFR-04 baseline: an anonymous request to a protected path is 401 problem+json")
    void anonymousIs401ProblemJson() throws Exception {
        mvc.perform(get("/api/_probe/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("/problems/unauthorized"));
    }

    @Test
    @DisplayName("NFR-04 baseline: bad credentials are 401 problem+json")
    void badCredentialsAre401ProblemJson() throws Exception {
        mvc.perform(get("/api/_probe/admin").with(httpBasic("admin1", "nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    @DisplayName("NFR-04 baseline: a signed-in user without the role is 403 problem+json (method security)")
    void wrongRoleIs403ProblemJson() throws Exception {
        mvc.perform(get("/api/_probe/admin").with(httpBasic("customer1", "password")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.type").value("/problems/forbidden"));
    }

    @Test
    @DisplayName("NFR-04 baseline: a user with the role passes @PreAuthorize")
    void rightRoleIsAllowed() throws Exception {
        mvc.perform(get("/api/_probe/admin").with(httpBasic("admin1", "password")))
                .andExpect(status().isOk());
    }

    /** Test-only endpoints. Registered through @Import, so no other test context sees them. */
    @RestController
    static class ProbeController {

        record ProbeRequest(@NotBlank String name, @Min(1) Integer count) {
        }

        @PostMapping("/api/_probe/validate")
        String validate(@Valid @RequestBody ProbeRequest request) {
            return "ok";
        }

        @GetMapping("/api/_probe/param")
        String param(@RequestParam @Min(1) int count) {
            return "ok";
        }

        @GetMapping("/api/_probe/service-validation")
        String serviceValidation() {
            throw new ValidationFailedException(List.of(
                    new FieldViolation("termYears", "must be one of 10, 20, 30"),
                    new FieldViolation("sumInsured", "must be at most 100")));
        }

        @GetMapping("/api/_probe/missing")
        String missing() {
            throw new ResourceNotFoundException("Probe resource not found");
        }

        @GetMapping("/api/_probe/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @GetMapping("/api/_probe/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "ok";
        }
    }
}
