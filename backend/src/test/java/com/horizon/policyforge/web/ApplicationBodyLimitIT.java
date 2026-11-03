package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationFixtures.application;
import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.service.QuoteService;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * NFR-04.4 for the application endpoint, end to end. MockMvc does not decode the path the way Tomcat does, so this goes
 * through the real embedded server with a raw HTTP client that sends the encoded path verbatim. The body is a complete,
 * valid application for a real quote padded with trailing whitespace, so the only thing that can refuse it is the size
 * check: without the cap it would be a 201.
 */
@Tag("NFR-04")
@Tag("NFR-04.4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationBodyLimitIT {

    private static final int KB = 1024;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private Clock clock;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    private String newQuoteId() {
        return quoteService.create("TERM_LIFE", cleanTermLifeInputs()).quoteId();
    }

    private String applicationJson(String quoteId) throws Exception {
        LocalDate dob = LocalDate.now(clock).minusYears(35);
        return json.writeValueAsString(application(quoteId, kyc(dob)));
    }

    private static String padTo(String jsonText, int bytes) {
        return jsonText + " ".repeat(Math.max(0, bytes - jsonText.length()));
    }

    private int applicationsForQuote(String quoteId) {
        return jdbc.queryForObject("select count(*) from application where quote_id = ?", Integer.class, quoteId);
    }

    private int applications() {
        return jdbc.queryForObject("select count(*) from application", Integer.class);
    }

    /** Sends the raw (still encoded) path verbatim as customer1; chunked means no Content-Length header. */
    private int send(String rawPath, String body, boolean chunked) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String credentials = Base64.getEncoder().encodeToString("customer1:password".getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + credentials);
        builder.POST(chunked ? BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
                : BodyPublishers.ofByteArray(bytes));
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    @Test
    @DisplayName("NFR-04.4.1: a body over 16 KB to POST /api/applications is a 413 and nothing is stored, while the same body without padding is accepted")
    void oversizedApplicationIsRefused() throws Exception {
        String quoteId = newQuoteId();
        String valid = applicationJson(quoteId);
        int before = applications();

        assertThat(send("/api/applications", padTo(valid, 20 * KB), false)).isEqualTo(413);

        assertThat(applications()).isEqualTo(before);
        assertThat(applicationsForQuote(quoteId)).isZero();
        assertThat(send("/api/applications", valid, false)).as("the unpadded body is a valid application")
                .isEqualTo(201);
        assertThat(applicationsForQuote(quoteId)).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-04.4.1: a body over 16 KB to the percent-encoded path /api/%61pplications is a 413 and nothing is stored")
    void oversizedApplicationOnEncodedPathIsRefused() throws Exception {
        String quoteId = newQuoteId();
        int before = applications();

        assertThat(send("/api/%61pplications", padTo(applicationJson(quoteId), 20 * KB), false)).isEqualTo(413);
        assertThat(send("/api/application%73", padTo(applicationJson(quoteId), 20 * KB), false)).isEqualTo(413);

        assertThat(applications()).isEqualTo(before);
        assertThat(applicationsForQuote(quoteId)).isZero();
    }

    @Test
    @DisplayName("NFR-04.4.2: a chunked body (no Content-Length) over 16 KB to POST /api/applications is a 413 and nothing is stored")
    void chunkedOversizedApplicationIsRefused() throws Exception {
        String quoteId = newQuoteId();
        int before = applications();

        assertThat(send("/api/applications", padTo(applicationJson(quoteId), 20 * KB), true)).isEqualTo(413);

        assertThat(applications()).isEqualTo(before);
        assertThat(applicationsForQuote(quoteId)).isZero();
    }
}
