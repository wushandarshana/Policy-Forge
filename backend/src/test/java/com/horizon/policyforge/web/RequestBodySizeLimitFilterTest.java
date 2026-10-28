package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * NFR-04.4 / AC-01.9: the default-deny body size limit. 16 KB for every request, 64 KB only for a Basic-authenticated
 * POST /api/products/{code}/versions, chosen on the decoded routed path; with and without a Content-Length header.
 */
@Tag("AC-01")
@Tag("AC-01.9")
@Tag("NFR-04")
@Tag("NFR-04.4")
class RequestBodySizeLimitFilterTest {

    /** Synthetic placeholder credentials; never checked by this filter. */
    private static final String BASIC = "Basic c3ludGhldGljOnN5bnRoZXRpYw==";

    private final RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(new ObjectMapper());

    /** A request whose body arrives chunked: no Content-Length header. */
    private static MockHttpServletRequest chunked(String uri, byte[] body, boolean basic) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setContent(body);
        request.setContentType("application/json");
        if (basic) {
            request.addHeader("Authorization", BASIC);
        }
        return request;
    }

    private static MockHttpServletRequest declared(String uri, int bytes, boolean basic) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContent(new byte[bytes]);
        request.setContentType("application/json");
        if (basic) {
            request.addHeader("Authorization", BASIC);
        }
        return request;
    }

    private int statusOf(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private boolean passedOn(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    @Test
    @DisplayName("AC-01.9: a chunked POST /api/quotes body over 16 KB with no Content-Length is a 413 problem+json and never reaches the controller")
    void chunkedOversizedBodyIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chunked("/api/quotes", new byte[16 * 1024 + 1], false), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("The request body is too large.")
                .contains("urn:policyforge:problem:payload-too-large");
        assertThat(chain.getRequest()).as("the request is not passed on").isNull();
    }

    @Test
    @DisplayName("AC-01.9: a declared Content-Length over 16 KB is a 413 before the body is read")
    void declaredOversizedBodyIsRejected() throws Exception {
        MockHttpServletRequest request = declared("/api/quotes", 20 * 1024, false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("AC-01.9: a chunked body of exactly 16 KB is passed on whole with its length")
    void bodyAtTheCapIsPassedOn() throws Exception {
        byte[] body = "x".repeat(16 * 1024).getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> seen = new AtomicReference<>();
        AtomicReference<Long> length = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException {
                seen.set(request.getInputStream().readAllBytes());
                length.set(request.getContentLengthLong());
            }
        };

        filter.doFilter(chunked("/api/quotes", body, false), new MockHttpServletResponse(), chain);

        assertThat(seen.get()).isEqualTo(body);
        assertThat(length.get()).isEqualTo(body.length);
    }

    @Test
    @DisplayName("NFR-04.4.1: percent-encoded variants of /api/quotes get the 16 KB default")
    void encodedPathsGetTheDefault() throws Exception {
        assertThat(statusOf(declared("/api/%71uotes", 20 * 1024, false))).isEqualTo(413);
        assertThat(statusOf(declared("/api/quote%73", 20 * 1024, false))).isEqualTo(413);
    }

    @Test
    @DisplayName("NFR-04.4.2: a chunked body over 16 KB to an encoded path with no Content-Length is a 413")
    void chunkedEncodedPathIsLimited() throws Exception {
        assertThat(statusOf(chunked("/api/%71uotes", new byte[16 * 1024 + 1], false))).isEqualTo(413);
    }

    @Test
    @DisplayName("NFR-04.4.3: a Basic-authenticated POST /api/products/{code}/versions may send up to 64 KB, also on an encoded path, but not more")
    void publishAllowanceIsSixtyFourKb() throws Exception {
        assertThat(passedOn(declared("/api/products/MOTOR/versions", 64 * 1024, true))).as("64 KB publish").isTrue();
        assertThat(passedOn(chunked("/api/products/MOTOR/versions", new byte[64 * 1024], true))).as("chunked").isTrue();
        assertThat(passedOn(declared("/api/products/MOTOR/version%73", 40 * 1024, true)))
                .as("decoded route gets the allowance").isTrue();

        assertThat(statusOf(declared("/api/products/MOTOR/versions", 64 * 1024 + 1, true))).isEqualTo(413);
        assertThat(statusOf(chunked("/api/products/MOTOR/versions", new byte[64 * 1024 + 1], true))).isEqualTo(413);
    }

    @Test
    @DisplayName("NFR-04.4.3: the 64 KB allowance needs Basic credentials, POST and the exact normalised route; anything else gets 16 KB")
    void allowanceIsExplicit() throws Exception {
        assertThat(statusOf(declared("/api/products/MOTOR/versions", 20 * 1024, false))).as("anonymous").isEqualTo(413);
        MockHttpServletRequest bearer = declared("/api/products/MOTOR/versions", 20 * 1024, false);
        bearer.addHeader("Authorization", "Bearer token");
        assertThat(statusOf(bearer)).as("not Basic").isEqualTo(413);
        MockHttpServletRequest put = declared("/api/products/MOTOR/versions", 20 * 1024, true);
        put.setMethod("PUT");
        assertThat(statusOf(put)).as("not POST").isEqualTo(413);
        assertThat(statusOf(declared("/api/quotes", 20 * 1024, true))).as("other route").isEqualTo(413);
        assertThat(statusOf(declared("/api/products/MOTOR/versions/", 20 * 1024, true))).as("trailing slash").isEqualTo(413);
        assertThat(statusOf(declared("/api/products/MOTOR/versions;x=1", 20 * 1024, true))).as("matrix").isEqualTo(413);
        assertThat(statusOf(declared("/api//products/MOTOR/versions", 20 * 1024, true))).as("double slash").isEqualTo(413);
        assertThat(statusOf(declared("/api/products/MOTOR/./versions", 20 * 1024, true))).as("dot").isEqualTo(413);
        assertThat(statusOf(declared("/api/products/X/versions/../../../quotes", 20 * 1024, true))).as("dot-dot")
                .isEqualTo(413);
    }

    @Test
    @DisplayName("NFR-04.4.4: every path is limited by default, including unmapped /api/** and non-api paths")
    void everyPathIsLimitedByDefault() throws Exception {
        assertThat(statusOf(declared("/api/unknown-endpoint", 20 * 1024, false))).isEqualTo(413);
        assertThat(statusOf(declared("/actuator/health", 20 * 1024, false))).isEqualTo(413);
        assertThat(statusOf(declared("/", 20 * 1024, true))).isEqualTo(413);
    }
}
