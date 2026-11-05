package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * NFR-04.1 end to end. MockMvc never reaches the container's error page, so these go through the real embedded server
 * with a raw HTTP client that sends the path verbatim. A path Spring Security's firewall refuses (a matrix parameter, an
 * encoded dot segment, an empty segment) or that Tomcat itself refuses is a framework 400 that is answered by the
 * container's error dispatch. By default Spring Boot writes {@code {"path": <the request path>, ...}} there, which
 * repeats whatever the caller put in the path. The error dispatch must answer with the same fixed problem+json as every
 * other error: no path, a fixed detail and the fixed URN instance (contract C7).
 */
@Tag("NFR-04")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPathEndToEndIT {

    private static final String MARKER = "zzmarker0x7f3a";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String rawPath) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** The same request with preemptive Basic credentials of customer1. */
    private HttpResponse<String> getAsCustomer(String rawPath) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("customer1:password".getBytes(StandardCharsets.UTF_8)))
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String everythingSent(HttpResponse<String> response) {
        StringBuilder all = new StringBuilder();
        response.headers().map().forEach((name, values) -> all.append(name).append(": ")
                .append(String.join(",", values)).append('\n'));
        return all.append(response.body()).toString();
    }

    @ParameterizedTest(name = "NFR-04.1: the firewall-refused path {0} is a 400 problem+json with the fixed bad-request instance, and the path is not echoed")
    @ValueSource(strings = {
        "/api/policies/" + MARKER + ";x=1/endorsements",
        "/api/policies/" + MARKER + "%2e%2e/x",
        "/api//" + MARKER,
        "/api/applications/" + MARKER + "/issue;jsessionid=1",
    })
    void aRefusedPathIsAFixedProblem(String rawPath) throws Exception {
        HttpResponse<String> response = get(rawPath);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(everythingSent(response)).as("headers and body").doesNotContain(MARKER);
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.has("path")).as("no path member").isFalse();
        assertThat(problem.get("status").asInt()).isEqualTo(400);
        assertThat(problem.get("detail").asText()).isEqualTo("The request could not be read.");
        assertThat(problem.get("instance").asText()).isEqualTo("urn:policyforge:problem:bad-request");
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @ParameterizedTest(name = "NFR-04.1: the path {0}, refused by the container itself, never has its text echoed")
    @ValueSource(strings = {
        "/api/policies/" + MARKER + "%5cx",
        "/api/policies/" + MARKER + "%2fx",
        "/api/policies/" + MARKER + "%00x",
    })
    void aPathTheContainerRefusesIsNotEchoed(String rawPath) throws Exception {
        HttpResponse<String> response = get(rawPath);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(everythingSent(response)).as("headers and body").doesNotContain(MARKER);
    }

    @Test
    @DisplayName("NFR-04.1: an anonymous request to a protected path through the real server is a 401 with no WWW-Authenticate header and a fixed instance")
    void aRealServer401HasNoChallenge() throws Exception {
        HttpResponse<String> response = get("/api/policies/mine");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(json.readTree(response.body()).get("instance").asText())
                .isEqualTo("urn:policyforge:problem:unauthorized");
    }

    @Test
    @DisplayName("NFR-04.1: an unknown path through the real server is a 404 problem+json with the fixed not-found instance and no marker")
    void aRealServer404IsAFixedProblem() throws Exception {
        for (String path : List.of("/api/" + MARKER + "/nothing", "/" + MARKER + "/nothing")) {
            HttpResponse<String> response = getAsCustomer(path);

            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat(everythingSent(response)).as(path).doesNotContain(MARKER);
            assertThat(json.readTree(response.body()).get("instance").asText())
                    .isEqualTo("urn:policyforge:problem:not-found");
        }
    }
}
