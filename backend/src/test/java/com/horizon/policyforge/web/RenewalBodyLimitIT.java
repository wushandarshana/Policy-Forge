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
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
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
 * NFR-04.4 for the renewal job and the renewal payment, end to end. MockMvc does not decode the path the way Tomcat
 * does, so this goes through the real embedded server with a raw HTTP client that sends the encoded path verbatim. The
 * body is a valid request ({@code {}}) padded with trailing whitespace, so the only thing that can refuse it is the size
 * check: without the cap it would be a 200. The server runs on the real clock, so the job is given an explicit date of
 * the test policy's own expiry minus 30 days, and the payment happens on today's date, inside its window.
 */
@Tag("NFR-04")
@Tag("NFR-04.4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "policyforge.test.context=renewal-body-limit")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RenewalBodyLimitIT {

    private static final int KB = 1024;
    private static final String JOB_PATH = "/api/admin/jobs/renewal-cycle";

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

    /** Sends the raw (still encoded) path verbatim as {@code user}; chunked means no Content-Length header. */
    private HttpResponse<String> post(String user, String rawPath, String body, boolean chunked) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String credentials = Base64.getEncoder().encodeToString((user + ":password").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + credentials);
        builder.POST(chunked ? BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
                : BodyPublishers.ofByteArray(bytes));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private int send(String user, String rawPath, String body, boolean chunked) throws Exception {
        return post(user, rawPath, body, chunked).statusCode();
    }

    /** A new ACTIVE policy of customer1, issued through the real endpoint with today's effective date. */
    private JsonNode issuedPolicy() throws Exception {
        HttpResponse<String> issued = post("customer1", "/api/applications/" + approvedApplicationId() + "/issue", "{}",
                false);
        assertThat(issued.statusCode()).as(issued.body()).isEqualTo(201);
        return json.readTree(issued.body());
    }

    /** The date 30 days before the policy's expiry: the first day its renewal offer is due. */
    private static String offerDay(JsonNode policy) {
        return LocalDate.parse(policy.get("expiryDate").asText()).minusDays(30).toString();
    }

    private int termRows(String policyNumber) {
        return PolicyRows.terms(jdbc, policyNumber).size();
    }

    private int auditRows() {
        return RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN").size();
    }

    @Test
    @DisplayName("NFR-04.4: a body over 16 KB to POST /api/admin/jobs/renewal-cycle is a 413 and nothing is run or audited, while the same body without padding runs the cycle")
    void oversizedJobIsRefused() throws Exception {
        JsonNode policy = issuedPolicy();
        String number = policy.get("policyNumber").asText();
        String path = JOB_PATH + "?date=" + offerDay(policy);
        int auditBefore = auditRows();

        assertThat(send("admin1", path, padTo("{}", 20 * KB), false)).isEqualTo(413);
        assertThat(send("admin1", path, padTo("{}", 20 * KB), true)).as("chunked, no Content-Length").isEqualTo(413);

        assertThat(auditRows()).as("nothing audited").isEqualTo(auditBefore);
        assertThat(termRows(number)).as("nothing offered").isEqualTo(1);
        assertThat(send("admin1", path, "{}", false)).as("the unpadded body is a valid job request").isEqualTo(200);
        assertThat(termRows(number)).isEqualTo(2);
        assertThat(auditRows()).isEqualTo(auditBefore + 1);
    }

    @Test
    @DisplayName("NFR-04.4: a body over 16 KB to a percent-encoded variant of the job path is a 413 and nothing is run, and the encoded path does reach the endpoint")
    void oversizedJobOnEncodedPathIsRefused() throws Exception {
        JsonNode policy = issuedPolicy();
        String number = policy.get("policyNumber").asText();
        String query = "?date=" + offerDay(policy);
        List<String> encodedPaths = List.of(
                "/api/%61dmin/jobs/renewal-cycle" + query,
                "/api/admin/%6Aobs/renewal-cycle" + query,
                "/api/admin/jobs/renewal-cycl%65" + query);
        int auditBefore = auditRows();

        for (String path : encodedPaths) {
            assertThat(send("admin1", path, padTo("{}", 20 * KB), false)).as(path).isEqualTo(413);
        }

        assertThat(auditRows()).as("nothing audited").isEqualTo(auditBefore);
        assertThat(termRows(number)).as("nothing offered").isEqualTo(1);
        assertThat(send("admin1", "/api/%61dmin/jobs/renewal-cycle" + query, "{}", false))
                .as("the encoded path routes to the same endpoint, so only the cap refused the padded body")
                .isEqualTo(200);
        assertThat(termRows(number)).isEqualTo(2);
    }

    @Test
    @DisplayName("NFR-04.4: a body over 16 KB to POST /api/policies/{number}/renewal/pay is a 413 and nothing is written, while the same body without padding pays")
    void oversizedPaymentIsRefused() throws Exception {
        JsonNode policy = issuedPolicy();
        String number = policy.get("policyNumber").asText();
        assertThat(send("admin1", JOB_PATH + "?date=" + offerDay(policy), "{}", false)).isEqualTo(200);
        String path = "/api/policies/" + number + "/renewal/pay";
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        assertThat(send("customer1", path, padTo("{}", 20 * KB), false)).isEqualTo(413);
        assertThat(send("customer1", path, padTo("{}", 20 * KB), true)).as("chunked, no Content-Length")
                .isEqualTo(413);

        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ACTIVE");
        assertThat(send("customer1", path, "{}", false)).as("the unpadded body is a valid payment").isEqualTo(200);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("RENEWED");
    }

    @Test
    @DisplayName("NFR-04.4: a body over 16 KB to a percent-encoded variant of the payment path is a 413 and nothing is written, and the encoded path does reach the endpoint")
    void oversizedPaymentOnEncodedPathIsRefused() throws Exception {
        JsonNode policy = issuedPolicy();
        String number = policy.get("policyNumber").asText();
        assertThat(send("admin1", JOB_PATH + "?date=" + offerDay(policy), "{}", false)).isEqualTo(200);
        String encodedPolicy = "%50" + number.substring(1);
        List<String> encodedPaths = List.of(
                "/api/%70olicies/" + number + "/renewal/pay",
                "/api/policies/" + encodedPolicy + "/renewal/pay",
                "/api/policies/" + number + "/renewa%6C/pay",
                "/api/policies/" + number + "/renewal/%70ay");
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        for (String path : encodedPaths) {
            assertThat(send("customer1", path, padTo("{}", 20 * KB), false)).as(path).isEqualTo(413);
        }

        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);
        assertThat(send("customer1", "/api/%70olicies/" + number + "/renewal/pay", "{}", false))
                .as("the encoded path routes to the same endpoint, so only the cap refused the padded body")
                .isEqualTo(200);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("RENEWED");
    }
}
