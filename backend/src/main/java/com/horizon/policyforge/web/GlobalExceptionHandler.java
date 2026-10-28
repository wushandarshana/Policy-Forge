package com.horizon.policyforge.web;

import com.horizon.policyforge.service.ResourceNotFoundException;
import com.horizon.policyforge.service.ValidationFailedException;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central mapping of every error to RFC 7807 {@code application/problem+json} (contract C3).
 * Validation failures are 400 with {@code errors[]} of {@code {field, message}}. Messages never echo submitted values
 * and unexpected errors never leak internals (NFR-03).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Comparator<ApiFieldError> BY_FIELD_THEN_MESSAGE =
            Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message);

    static final String NOT_FOUND_DETAIL = "The requested resource was not found.";
    static final String TOO_LARGE_DETAIL = "The request body is too large.";
    private static final URI ABOUT_BLANK = URI.create("about:blank");
    /** Spring fills a missing instance with the request path, so a 404 carries this fixed value instead (F-4). */
    private static final URI NOT_FOUND_INSTANCE = URI.create("urn:policyforge:problem:not-found");

    /** One entry of the {@code errors[]} array. */
    public record ApiFieldError(String field, String message) {
    }

    /**
     * Framework errors (415, 405, unreadable body, ...) get Spring's default problem body, whose detail can repeat the
     * request's content type or method. Replace that detail with a fixed text per status (F-4).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && ABOUT_BLANK.equals(problem.getType())) {
            problem.setDetail(fixedDetail(response.getStatusCode()));
            if (response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                problem.setInstance(NOT_FOUND_INSTANCE);
            }
        }
        return response;
    }

    static String fixedDetail(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request could not be read.";
            case 404 -> NOT_FOUND_DETAIL;
            case 405 -> "This method is not supported for this resource.";
            case 406 -> "The requested representation is not available.";
            case 413 -> TOO_LARGE_DETAIL;
            case 415 -> "The request content type must be application/json.";
            default -> "The request could not be processed.";
        };
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.add(new ApiFieldError(field, error.getDefaultMessage()));
        }
        return handleExceptionInternal(ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new ApiFieldError(field, error.getDefaultMessage()));
            }
        }
        return handleExceptionInternal(ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(lastNode(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        return withInstance(validationProblem(errors), request);
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ProblemDetail handleValidationFailed(ValidationFailedException ex, HttpServletRequest request) {
        List<ApiFieldError> errors = ex.violations().stream()
                .map((FieldViolation v) -> new ApiFieldError(v.field(), v.message()))
                .toList();
        return withInstance(validationProblem(errors), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        // Fixed detail and instance: the message and the path name the requested code (or a date), and a 404 never
        // echoes what was asked for (F-4).
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Not found", "/problems/not-found", NOT_FOUND_DETAIL);
        problem.setInstance(NOT_FOUND_INSTANCE);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return withInstance(problem(HttpStatus.FORBIDDEN, "Forbidden", "/problems/forbidden",
                "You do not have permission to perform this action."), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail body = withInstance(problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "/problems/unauthorized",
                "Authentication is required."), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, ProblemJsonSecurityHandler.CHALLENGE)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // The correlationId is already on every log line through the MDC.
        LOG.error("Unhandled error method={} path={}", request.getMethod(), request.getRequestURI(), ex);
        return withInstance(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "/problems/internal-error", "An unexpected error occurred."), request);
    }

    private static ProblemDetail validationProblem(List<ApiFieldError> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "/problems/validation-failed",
                "One or more fields are invalid.");
        problem.setProperty("errors", errors.stream().sorted(BY_FIELD_THEN_MESSAGE).toList());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
        return problem;
    }

    private static ProblemDetail withInstance(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static String lastNode(Path path) {
        String name = "";
        for (Path.Node node : path) {
            name = node.getName() == null ? name : node.getName();
        }
        return name;
    }
}
