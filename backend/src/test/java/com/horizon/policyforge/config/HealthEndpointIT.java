package com.horizon.policyforge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/** NFR-07: health endpoint, exercised over a real HTTP port on the first request after startup. */
@Tag("NFR-07")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealthEndpointIT {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("NFR-07.1: GET /actuator/health returns 200 with {\"status\":\"UP\"} in under 1000 ms after startup")
    void healthIsUpWithinOneSecond() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        client.send(request, HttpResponse.BodyHandlers.discarding()); // warm-up, not timed (test review m1)

        long startNanos = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertThat(response.statusCode()).isEqualTo(200);
        ObjectMapper json = new ObjectMapper();
        JsonNode body = json.readTree(response.body());
        assertThat(body).isEqualTo(json.readTree("{\"status\":\"UP\"}"));
        assertThat(elapsedMillis).as("health latency in ms").isLessThan(1000L);
    }
}
