package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * NFR-04.4 end to end. MockMvc does not decode the path the way Tomcat does, so these go through the real embedded
 * server with a raw HTTP client that sends the encoded path verbatim (security review R-1: {@code /api/%71uotes}
 * bypassed the 16 KB cap). Oversized quote bodies are valid JSON padded with trailing whitespace, so the only thing
 * that can reject them is the size check.
 */
@Tag("NFR-04")
@Tag("NFR-04.4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BodySizeLimitEndToEndIT {

    private static final int KB = 1024;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newHttpClient();

    private static String validQuoteJson() throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("age", 35);
        inputs.put("smoker", false);
        inputs.put("sumInsured", "5000000.00");
        inputs.put("termYears", 20);
        inputs.put("bmi", "27.3189");
        inputs.put("healthDeclarations", List.of("DIABETES"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", "TERM_LIFE");
        body.put("riskInputs", inputs);
        return new ObjectMapper().writeValueAsString(body);
    }

    private static String padTo(String jsonText, int bytes) {
        return jsonText + " ".repeat(Math.max(0, bytes - jsonText.length()));
    }

    /** MOTOR v2 whose first underwriting rule description is long enough to reach the requested body size. */
    private static String motorV2WithPadding(int targetBytes) {
        ObjectNode tree = RuleSetFixtures.seedTree("motor", 1);
        tree.put("version", 2);
        tree.put("effectiveFrom", "2026-06-01");
        ((ObjectNode) RuleSetFixtures.rules(tree).get(0)).put("description", "d".repeat(targetBytes));
        return RuleSetFixtures.render(tree);
    }

    private int quotes() {
        return jdbc.queryForObject("select count(*) from quote", Integer.class);
    }

    private int ruleSets() {
        return jdbc.queryForObject("select count(*) from rule_set_version", Integer.class);
    }

    /** Sends the raw (still encoded) path verbatim; chunked means no Content-Length header. */
    private int send(String rawPath, String body, boolean chunked, String basicUser) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .header("Content-Type", "application/json");
        if (basicUser != null) {
            builder.header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString((basicUser + ":password").getBytes(StandardCharsets.UTF_8)));
        }
        builder.POST(chunked ? BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
                : BodyPublishers.ofByteArray(bytes));
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    @Test
    @DisplayName("NFR-04.4.1: an anonymous POST to the percent-encoded path /api/%71uotes with a body over 16 KB is a 413 and no quote is stored")
    void encodedQuotesPathIsLimited() throws Exception {
        int before = quotes();
        int status = send("/api/%71uotes", padTo(validQuoteJson(), 20 * KB), false, null);
        assertThat(status).isEqualTo(413);
        assertThat(quotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.4.1: an anonymous POST to the percent-encoded path /api/quote%73 with a body over 16 KB is a 413 and no quote is stored")
    void encodedQuotesPathTrailingEscapeIsLimited() throws Exception {
        int before = quotes();
        int status = send("/api/quote%73", padTo(validQuoteJson(), 20 * KB), false, null);
        assertThat(status).isEqualTo(413);
        assertThat(quotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.4.2: a chunked POST (no Content-Length) over 16 KB to the encoded path /api/%71uotes is a 413 and no quote is stored")
    void chunkedEncodedPathIsLimited() throws Exception {
        int before = quotes();
        int status = send("/api/%71uotes", padTo(validQuoteJson(), 20 * KB), true, null);
        assertThat(status).isEqualTo(413);
        assertThat(quotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.4.3: an authenticated PRODUCT_MANAGER publish of a valid rule set of about 40 KB is not refused for size")
    void publishUpToSixtyFourKbIsNotRefusedForSize() throws Exception {
        int status = send("/api/products/MOTOR/versions", motorV2WithPadding(40 * KB), false, "admin1");
        assertThat(status).isEqualTo(201);
    }

    @Test
    @DisplayName("NFR-04.4.3: an authenticated PRODUCT_MANAGER publish of a rule set over 64 KB is a 413 and no version is stored")
    void publishOverSixtyFourKbIsRefused() throws Exception {
        int before = ruleSets();
        int status = send("/api/products/MOTOR/versions", motorV2WithPadding(70 * KB), false, "admin1");
        assertThat(status).isEqualTo(413);
        assertThat(ruleSets()).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.4.4: an anonymous POST over 16 KB to an unmapped /api/** path is a 413 (default-deny), not a routing or auth answer")
    void unknownApiPathIsLimitedByDefault() throws Exception {
        int status = send("/api/unknown-endpoint", padTo("{}", 20 * KB), false, null);
        assertThat(status).isEqualTo(413);
    }
}
