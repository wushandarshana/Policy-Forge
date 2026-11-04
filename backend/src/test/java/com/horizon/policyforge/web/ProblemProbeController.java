package com.horizon.policyforge.web;

import com.horizon.policyforge.domain.policy.InvalidPolicyStateException;
import com.horizon.policyforge.domain.policy.PolicyNumberSequenceExhaustedException;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.service.ConflictException;
import com.horizon.policyforge.service.ResourceNotFoundException;
import com.horizon.policyforge.service.ValidationFailedException;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints that raise each kind of error through the real MVC and security stack (NFR-04.1: every problem
 * carries the fixed {@code instance} of its type). Registered with {@code @Import}, so only the tests that ask for it see
 * these routes. The paths are under {@code /api}, so they need a signed-in user.
 */
@RestController
class ProblemProbeController {

    @GetMapping("/api/_problem/validation")
    String validation() {
        throw new ValidationFailedException(List.of(new FieldViolation("probe", "is invalid")));
    }

    @GetMapping("/api/_problem/conflict")
    String conflict() {
        throw new ConflictException(ConflictException.POLICY_UPDATE_CONFLICT);
    }

    @GetMapping("/api/_problem/invalid-state")
    String invalidState() {
        throw new InvalidPolicyStateException(PolicyStatus.CANCELLED, PolicyStatus.ENDORSED);
    }

    @GetMapping("/api/_problem/not-found")
    String notFound() {
        throw new ResourceNotFoundException("Probe resource not found");
    }

    @GetMapping("/api/_problem/denied")
    String denied() {
        throw new AccessDeniedException("probe");
    }

    @GetMapping("/api/_problem/unauthenticated")
    String unauthenticated() {
        throw new BadCredentialsException("probe");
    }

    @GetMapping("/api/_problem/boom")
    String boom() {
        throw new IllegalStateException("secret-internal-detail");
    }

    /** A framework problem (about:blank) with status 500. */
    @GetMapping("/api/_problem/framework-500")
    String frameworkServerError() {
        throw new ErrorResponseException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping("/api/_problem/exhausted")
    String exhausted() {
        throw new PolicyNumberSequenceExhaustedException();
    }

    /** A missing required parameter is a framework 400. */
    @GetMapping("/api/_problem/param")
    String param(@RequestParam("n") int n) {
        return "ok " + n;
    }
}
