package com.horizon.policyforge.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Default-deny request body size limit (NFR-04.4, AC-01.9, security review F-3 and R-1). Every request body is capped
 * at 16 KB. The one explicit allowance is {@code POST /api/products/{code}/versions} carrying HTTP Basic credentials,
 * which may send up to 64 KB (Spring Security, which runs after this filter, still authenticates and authorises it).
 * <p>
 * The allowance is chosen on the decoded, normalised path Spring MVC routes on ({@link RequestPath} matched with a
 * {@link PathPattern}), never on the raw {@code getRequestURI()}, so {@code /api/%71uotes} is treated exactly like
 * {@code /api/quotes}. Anything that does not match the allowance, including a path that is not normalised, gets the
 * default (KD-010).
 * <p>
 * A declared {@code Content-Length} over the cap is refused at once; otherwise the body is read with the cap enforced,
 * so a chunked body with no {@code Content-Length} is refused too. Refusals are 413 problem+json with fixed text, and
 * nothing reaches security or the controllers, so nothing is stored. The filter runs before the Spring Security chain,
 * so an anonymous oversized request to any path is a 413, not a 401 (NFR-04.4.4).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    /** 16 KB: the default for every request. */
    static final int DEFAULT_MAX_BODY_BYTES = 16 * 1024;

    /** 64 KB: authenticated rule-set publish only. */
    static final int PUBLISH_MAX_BODY_BYTES = 64 * 1024;

    static final URI INSTANCE = URI.create("urn:policyforge:problem:payload-too-large");

    private static final PathPattern PUBLISH = PathPatternParser.defaultInstance.parse("/api/products/{code}/versions");

    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int cap = capFor(request);
        if (request.getContentLengthLong() > cap) {
            tooLarge(response);
            return;
        }
        byte[] body = readCapped(request.getInputStream(), cap);
        if (body == null) {
            tooLarge(response);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /** The byte cap for this request: 64 KB for a Basic-authenticated publish on the routed path, else 16 KB. */
    static int capFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || !hasBasicCredentials(request)) {
            return DEFAULT_MAX_BODY_BYTES;
        }
        PathContainer path;
        try {
            // The same parse DispatcherServlet does before PathPattern matching: segments are matched decoded.
            path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
        } catch (RuntimeException unparseable) {
            return DEFAULT_MAX_BODY_BYTES;
        }
        return isNormalised(path) && PUBLISH.matches(path) ? PUBLISH_MAX_BODY_BYTES : DEFAULT_MAX_BODY_BYTES;
    }

    private static boolean hasBasicCredentials(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6);
    }

    /** No empty ("//"), "." or ".." segments and no matrix parameters: anything else is not a route we relax. */
    private static boolean isNormalised(PathContainer path) {
        boolean previousWasSeparator = false;
        for (PathContainer.Element element : path.elements()) {
            if (element instanceof PathContainer.PathSegment segment) {
                String value = segment.valueToMatch();
                if (value.isEmpty() || ".".equals(value) || "..".equals(value) || !segment.parameters().isEmpty()) {
                    return false;
                }
                previousWasSeparator = false;
            } else {
                if (previousWasSeparator) {
                    return false;
                }
                previousWasSeparator = true;
            }
        }
        return true;
    }

    /** The whole body, or null when it is longer than {@code cap}. Reads at most one byte past the cap. */
    private static byte[] readCapped(InputStream in, int cap) throws IOException {
        byte[] read = in.readNBytes(cap + 1);
        return read.length > cap ? null : read;
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                GlobalExceptionHandler.TOO_LARGE_DETAIL);
        problem.setTitle("Payload too large");
        problem.setType(URI.create("/problems/payload-too-large"));
        problem.setInstance(INSTANCE);
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /** Replays a body that has already been read and size-checked. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not supported");
                }

                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return source.read(buffer, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
