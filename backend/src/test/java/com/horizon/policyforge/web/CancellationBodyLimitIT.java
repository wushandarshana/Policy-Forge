package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.application.KycAddress;
import com.horizon.policyforge.domain.application.KycInput;
import com.horizon.policyforge.service.ApplicationResult;
import com.horizon.policyforge.service.ApplicationService;
import com.horizon.policyforge.service.QuoteService;
import com.horizon.policyforge.service.SubmitApplicationCommand;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.CancellationRows;
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
 * NFR-04.4 for the cancellation, end to end. MockMvc does not decode the path the way Tomcat does, so this goes through
 * the real embedded server with a raw HTTP client that sends the encoded path verbatim. The body is a valid request
 * padded with trailing whitespace, so the only thing that can refuse it is the size check: without the cap it would be a
 * 201. The server runs on the real clock, so the policy is issued today and cancelled today (inside cooling-off), and no
 * date is sent.
 */
@Tag("NFR-04")
@Tag("NFR-04.4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "policyforge.test.context=cancellation-body-limit")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CancellationBodyLimitIT {

    private static final int KB = 1024;
    private static final String VALID_BODY = "{\"reason\":\"NO_LONGER_NEEDED\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    /** A new APPROVED TERM_LIFE application of customer1, created through the services. */
    private String approvedApplicationId() {
        String quoteId = quoteService.create("TERM_LIFE", cleanTermLifeInputs()).quoteId();
        KycInput kyc = new KycInput(ApplicationFixtures.FULL_NAME, LocalDate.now(clock).minusYears(35).toString(),
                ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN,
                new KycAddress(ApplicationFixtures.LINE1, ApplicationFixtures.CITY, ApplicationFixtures.POSTCODE));
        ApplicationResult result = applicationService.submit(
                new SubmitApplicationCommand("customer1", quoteId, kyc, List.of()));
        assertThat(result.status().name()).isEqualTo("APPROVED");
        return result.applicationId();
    }

    private static String padTo(String jsonText, int bytes) {
        return jsonText + " ".repeat(Math.max(0, bytes - jsonText.length()));
    }

    /** Sends the raw (still encoded) path verbatim as customer1; chunked means no Content-Length header. */
    private HttpResponse<String> post(String rawPath, String body, boolean chunked) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String credentials = Base64.getEncoder().encodeToString("customer1:password".getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + credentials);
        builder.POST(chunked ? BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
                : BodyPublishers.ofByteArray(bytes));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private int send(String rawPath, String body, boolean chunked) throws Exception {
        return post(rawPath, body, chunked).statusCode();
    }

    /** A new ACTIVE policy number of customer1, issued through the real endpoint with today's effective date. */
    private String issuedPolicyNumber() throws Exception {
        HttpResponse<String> issued = post("/api/applications/" + approvedApplicationId() + "/issue", "{}", false);
        assertThat(issued.statusCode()).as(issued.body()).isEqualTo(201);
        JsonNode body = json.readTree(issued.body());
        return body.get("policyNumber").asText();
    }

    private String status(String policyNumber) {
        return jdbc.queryForObject("select status from policy where policy_number = ?", String.class, policyNumber);
    }

    @Test
    @DisplayName("NFR-04.4: a body over 16 KB to POST /api/policies/{number}/cancel is a 413 and nothing is written, with or without a Content-Length header, while the same body without padding cancels")
    void oversizedCancelIsRefused() throws Exception {
        String number = issuedPolicyNumber();
        String path = "/api/policies/" + number + "/cancel";
        CancellationRows.Snapshot before = CancellationRows.snapshot(jdbc, number);

        assertThat(send(path, padTo(VALID_BODY, 20 * KB), false)).isEqualTo(413);
        assertThat(send(path, padTo(VALID_BODY, 20 * KB), true)).as("chunked, no Content-Length").isEqualTo(413);
        assertThat(send(path, padTo(VALID_BODY, 17 * KB), false)).as("just over the cap").isEqualTo(413);

        assertThat(CancellationRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);
        assertThat(status(number)).isEqualTo("ACTIVE");
        assertThat(send(path, VALID_BODY, false)).as("the unpadded body is a valid cancellation").isEqualTo(201);
        assertThat(status(number)).isEqualTo("CANCELLED");
        assertThat(CancellationRows.refunds(jdbc, number)).hasSize(1);
    }

    @Test
    @DisplayName("NFR-04.4: a body over 16 KB to a percent-encoded variant of the cancel path is a 413 and nothing is written, and the encoded path does reach the endpoint")
    void oversizedCancelOnEncodedPathIsRefused() throws Exception {
        String number = issuedPolicyNumber();
        String encodedPolicy = "%50" + number.substring(1);
        List<String> encodedPaths = List.of(
                "/api/%70olicies/" + number + "/cancel",
                "/api/policies/" + encodedPolicy + "/cancel",
                "/api/policies/" + number + "/can%63el",
                "/api/policies/" + number + "/%63ancel");
        CancellationRows.Snapshot before = CancellationRows.snapshot(jdbc, number);

        for (String path : encodedPaths) {
            assertThat(send(path, padTo(VALID_BODY, 20 * KB), false)).as(path).isEqualTo(413);
            assertThat(send(path, padTo(VALID_BODY, 20 * KB), true)).as(path + " chunked").isEqualTo(413);
        }

        assertThat(CancellationRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);
        assertThat(send("/api/%70olicies/" + number + "/cancel", VALID_BODY, false))
                .as("the encoded path routes to the same endpoint, so only the cap refused the padded body")
                .isEqualTo(201);
        assertThat(status(number)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("NFR-04.4: a body over 16 KB that is not a valid cancellation is still a 413, not a 400, so the cap is checked before the shape")
    void theCapComesBeforeTheShape() throws Exception {
        String number = issuedPolicyNumber();
        String path = "/api/policies/" + number + "/cancel";
        CancellationRows.Snapshot before = CancellationRows.snapshot(jdbc, number);

        assertThat(send(path, padTo("{\"reason\":\"SYNTHETIC_REASON_BETA\"}", 20 * KB), false)).isEqualTo(413);
        assertThat(send(path, padTo("{}", 20 * KB), false)).isEqualTo(413);

        assertThat(CancellationRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);
    }
}
