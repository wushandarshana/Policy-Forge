package com.horizon.policyforge.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes 401 and 403 as RFC 7807 problem+json for failures raised inside the security filter chain, where
 * {@link GlobalExceptionHandler} is not reached. The bodies match the ones the advice produces: the same fixed detail
 * and the fixed {@code instance} URN of the problem type, never the request path (NFR-04.1).
 *
 * <p>A 401 carries no {@code WWW-Authenticate} header. A Basic challenge makes a browser open its own credential dialog
 * and cache what is typed, and a cached Basic credential is attached to cross-site requests (KD-007). The SPA signs in
 * with its own form and sends preemptive Basic credentials, so the server never needs to ask. Preemptive Basic still
 * authenticates: the Basic filter reads the header whether or not a challenge was ever sent.
 */
public class ProblemJsonSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemJsonSecurityHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "/problems/unauthorized",
                "Authentication is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Forbidden", "/problems/forbidden",
                "You do not have permission to perform this action.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String type, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setInstance(ProblemInstances.of(problem.getType(), status));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
