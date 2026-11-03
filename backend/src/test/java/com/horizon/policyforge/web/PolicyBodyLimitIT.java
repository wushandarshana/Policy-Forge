package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.application.KycAddress;
import com.horizon.policyforge.domain.application.KycInput;
import com.horizon.policyforge.service.ApplicationResult;
import com.horizon.policyforge.service.ApplicationService;
import com.horizon.policyforge.service.QuoteService;
import com.horizon.policyforge.service.SubmitApplicationCommand;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.ApplicationRows;
import com.horizon.policyforge.support.PolicyRows;
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
 * NFR-04.4 for the issue endpoint, end to end. MockMvc does not decode the path the way Tomcat does, so this goes
 * through the real embedded server with a raw HTTP client that sends the encoded path verbatim. The body is a valid issue
 * request ({@code {}}) padded with trailing whitespace, so the only thing that can refuse it is the size check: without
 * the cap it would be a 201.
 */
@Tag("NFR-04")
@Tag("NFR-04.4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyBodyLimitIT {

    private static final int KB = 1024;

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
    @DisplayName("NFR-04.4.1: a body over 16 KB to POST /api/applications/{id}/issue is a 413 and nothing is stored, while the same body without padding issues the policy")
    void oversizedIssueIsRefused() throws Exception {
        String applicationId = approvedApplicationId();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

        assertThat(send("/api/applications/" + applicationId + "/issue", padTo("{}", 20 * KB), false)).isEqualTo(413);

        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
        assertThat(ApplicationRows.status(jdbc, applicationId)).isEqualTo("APPROVED");
        assertThat(send("/api/applications/" + applicationId + "/issue", "{}", false))
                .as("the unpadded body is a valid issue request").isEqualTo(201);
        assertThat(PolicyRows.policiesForApplication(jdbc, applicationId)).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-04.4.1: a body over 16 KB to a percent-encoded variant of the issue path is a 413 and nothing is stored, and the encoded path does reach the endpoint")
    void oversizedIssueOnEncodedPathIsRefused() throws Exception {
        String applicationId = approvedApplicationId();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

        assertThat(send("/api/%61pplications/" + applicationId + "/issue", padTo("{}", 20 * KB), false)).isEqualTo(413);
        assertThat(send("/api/applications/" + applicationId + "/issu%65", padTo("{}", 20 * KB), false)).isEqualTo(413);
        assertThat(send("/api/applications/%41" + applicationId.substring(1) + "/issue", padTo("{}", 20 * KB), false))
                .isEqualTo(413);

        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
        assertThat(send("/api/%61pplications/" + applicationId + "/issue", "{}", false))
                .as("the encoded path routes to the same endpoint, so only the cap refused the padded body")
                .isEqualTo(201);
    }

    @Test
    @DisplayName("NFR-04.4.2: a chunked body (no Content-Length) over 16 KB to POST /api/applications/{id}/issue is a 413 and nothing is stored")
    void chunkedOversizedIssueIsRefused() throws Exception {
        String applicationId = approvedApplicationId();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

        assertThat(send("/api/applications/" + applicationId + "/issue", padTo("{}", 20 * KB), true)).isEqualTo(413);

        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
        assertThat(PolicyRows.policiesForApplication(jdbc, applicationId)).isZero();
    }
}
