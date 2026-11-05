package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationFixtures.application;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.service.QuoteService;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationRows;
import com.horizon.policyforge.support.ApplicationRows.Snapshot;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * NFR-04.4 for the decision and override endpoints, end to end. MockMvc does not decode the path the way Tomcat does, so
 * this goes through the real embedded server with a raw HTTP client that sends the encoded path verbatim. Each body is a
 * complete, valid request padded with trailing whitespace, so the only thing that can refuse it is the size check:
 * without the cap it would be a 200. After every refusal the application's status, decision rows and audit entries are
 * compared with what they were before.
 */
@Tag("NFR-04")
@Tag("NFR-04.4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnderwritingBodyLimitIT {

    private static final int KB = 1024;
    private static final String COMMENT = "Acceptable after medical review";

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

    /** A new application of customer1 with the given outcome profile, created through the real endpoint. */
    private String newApplication(int age, boolean smoker, String sumInsured, String bmi) throws Exception {
        String quoteId = quoteService.create("TERM_LIFE", termLifeInputs(age, smoker, sumInsured, bmi, List.of()))
                .quoteId();
        LocalDate dob = LocalDate.now(clock).minusYears(age);
        HttpResponse<String> response = send("customer1", "POST", "/api/applications",
                json.writeValueAsString(application(quoteId, kyc(dob))), false);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).get("applicationId").asText();
    }

    private String referred() throws Exception {
        return newApplication(40, true, "12000000.00", "24");
    }

    private String declined() throws Exception {
        return newApplication(62, false, "5000000.00", "37");
    }

    private String decisionJson() throws Exception {
        return json.writeValueAsString(ApplicationApi.decisionBody("APPROVE", "UW-MAN-001", COMMENT));
    }

    private String overrideJson() throws Exception {
        return json.writeValueAsString(ApplicationApi.overrideBody("UW-OVR-001", COMMENT));
    }

    private static String padTo(String jsonText, int bytes) {
        return jsonText + " ".repeat(Math.max(0, bytes - jsonText.length()));
    }

    private Snapshot snapshot(String applicationId) {
        return ApplicationRows.snapshot(jdbc, applicationId);
    }

    /** Sends the raw (still encoded) path verbatim; chunked means no Content-Length header. */
    private HttpResponse<String> send(String user, String method, String rawPath, String body, boolean chunked)
            throws Exception {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String credentials = Base64.getEncoder().encodeToString((user + ":password").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + credentials);
        if ("POST".equals(method)) {
            builder.POST(chunked ? BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
                    : BodyPublishers.ofByteArray(bytes));
        } else {
            builder.GET();
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private int post(String user, String rawPath, String body, boolean chunked) throws Exception {
        return send(user, "POST", rawPath, body, chunked).statusCode();
    }

    @Test
    @DisplayName("NFR-04.4.1: a decision body over 16 KB is a 413 and the application is unchanged, while the same body without padding is accepted")
    void oversizedDecisionIsRefused() throws Exception {
        String id = referred();
        Snapshot before = snapshot(id);

        assertThat(post("underwriter1", "/api/underwriting/" + id + "/decision", padTo(decisionJson(), 20 * KB),
                false)).isEqualTo(413);

        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(post("underwriter1", "/api/underwriting/" + id + "/decision", decisionJson(), false))
                .as("the unpadded body is a valid decision").isEqualTo(200);
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("NFR-04.4.1: an override body over 16 KB is a 413 and the application is unchanged, while the same body without padding is accepted")
    void oversizedOverrideIsRefused() throws Exception {
        String id = declined();
        Snapshot before = snapshot(id);

        assertThat(post("admin1", "/api/underwriting/" + id + "/override", padTo(overrideJson(), 20 * KB), false))
                .isEqualTo(413);

        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(post("admin1", "/api/underwriting/" + id + "/override", overrideJson(), false))
                .as("the unpadded body is a valid override").isEqualTo(200);
        assertThat(ApplicationRows.status(jdbc, id)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("NFR-04.4.1: a body over 16 KB to the percent-encoded decision and override paths is a 413 and nothing changes")
    void oversizedBodiesOnEncodedPathsAreRefused() throws Exception {
        String referred = referred();
        String declined = declined();
        Snapshot referredBefore = snapshot(referred);
        Snapshot declinedBefore = snapshot(declined);

        assertThat(post("underwriter1", "/api/%75nderwriting/" + referred + "/decision",
                padTo(decisionJson(), 20 * KB), false)).isEqualTo(413);
        assertThat(post("underwriter1", "/api/underwriting/" + referred + "/decisio%6e",
                padTo(decisionJson(), 20 * KB), false)).isEqualTo(413);
        assertThat(post("admin1", "/api/%75nderwriting/" + declined + "/override",
                padTo(overrideJson(), 20 * KB), false)).isEqualTo(413);
        assertThat(post("admin1", "/api/underwriting/" + declined + "/overrid%65",
                padTo(overrideJson(), 20 * KB), false)).isEqualTo(413);

        assertThat(snapshot(referred)).isEqualTo(referredBefore);
        assertThat(snapshot(declined)).isEqualTo(declinedBefore);
    }

    @Test
    @DisplayName("NFR-04.4.2: a chunked body (no Content-Length) over 16 KB to the decision and override endpoints is a 413 and nothing changes")
    void chunkedOversizedBodiesAreRefused() throws Exception {
        String referred = referred();
        String declined = declined();
        Snapshot referredBefore = snapshot(referred);
        Snapshot declinedBefore = snapshot(declined);

        assertThat(post("underwriter1", "/api/underwriting/" + referred + "/decision",
                padTo(decisionJson(), 20 * KB), true)).isEqualTo(413);
        assertThat(post("admin1", "/api/underwriting/" + declined + "/override", padTo(overrideJson(), 20 * KB),
                true)).isEqualTo(413);

        assertThat(snapshot(referred)).isEqualTo(referredBefore);
        assertThat(snapshot(declined)).isEqualTo(declinedBefore);
    }

    @Test
    @DisplayName("NFR-04.4.1: an oversized body is a 413 even from a caller who may not use the endpoint, because the limit runs before authentication")
    void limitRunsBeforeAuthorisation() throws Exception {
        String id = referred();
        Snapshot before = snapshot(id);

        assertThat(post("customer1", "/api/underwriting/" + id + "/decision", padTo(decisionJson(), 20 * KB), false))
                .isEqualTo(413);
        assertThat(post("underwriter1", "/api/underwriting/" + id + "/override", padTo(overrideJson(), 20 * KB),
                false)).isEqualTo(413);

        assertThat(snapshot(id)).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.4.1: the queue read still works through the real server for an underwriter (control for the body-limit tests)")
    void queueIsReachableThroughTheRealServer() throws Exception {
        String id = referred();

        HttpResponse<String> response = send("underwriter1", "GET", "/api/underwriting/queue", null, false);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode queue = json.readTree(response.body());
        assertThat(ApplicationApi.indexOf(queue, id)).isNotNegative();
    }
}
