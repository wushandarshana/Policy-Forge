package com.horizon.policyforge.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The container's error dispatch ({@code /error}), for failures that never reach {@link GlobalExceptionHandler}: a path
 * Spring Security's firewall refuses (a matrix parameter, an encoded dot segment, an empty segment) and any error raised
 * outside the MVC stack. Spring Boot's own error controller answers with {@code {"path": <the request path>, ...}},
 * which repeats whatever the caller put in the path (NFR-04.1, contract C7). This one answers with the same fixed
 * problem+json as every other error: the status's reason phrase as the title, a fixed detail, the fixed URN instance
 * and no request data at all. It is not under {@code /api} and has no role: {@code SecurityConfig} leaves the error
 * path open, as it must be for a request that failed before it was authenticated.
 */
@Controller
public class ProblemErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = statusOf(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, GlobalExceptionHandler.fixedDetail(status));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(ProblemInstances.of(problem.getType(), status));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** The status the container reports, or 500 when it is missing or not an HTTP status. */
    private static HttpStatus statusOf(Object attribute) {
        if (attribute instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
