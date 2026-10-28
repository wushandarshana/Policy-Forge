package com.horizon.policyforge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request correlation (NFR-06). Uses the caller's {@code X-Correlation-Id} when it is a safe token, otherwise
 * generates one, then echoes it on the response and exposes it to every log line of the request as the MDC key
 * {@code correlationId}. Runs before the security filter chain so rejected requests are correlated too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Letters, digits, dot, underscore, hyphen, at most 64 characters: safe to echo and to log. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Logger LOG = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // Method, path and status only: never headers, query strings or bodies (NFR-03).
            LOG.info("request completed method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String supplied) {
        if (supplied != null && SAFE_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
