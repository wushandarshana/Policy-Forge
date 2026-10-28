package com.horizon.policyforge.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NFR-06: request correlation. The log-capture assertions read the real console output of the real JSON encoder.
 * Only output written after the request starts is inspected, and MockMvc runs the request on the test thread, so
 * every JSON line on this thread in that window belongs to the request under test.
 */
@Tag("NFR-06")
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdIT {

    private static final String HEADER = "X-Correlation-Id";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("NFR-06.1: a request without X-Correlation-Id gets a generated header and the same correlationId in every JSON log line")
    void generatesCorrelationIdAndLogsItInEveryLine(CapturedOutput output) throws Exception {
        int offset = output.getOut().length();
        String generated = mvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader(HEADER);

        assertThat(generated).as("generated %s header", HEADER).isNotBlank();
        List<JsonNode> requestLines = jsonLinesOnCurrentThread(output.getOut().substring(offset));
        assertThat(requestLines).as("JSON log lines emitted while handling the request").isNotEmpty();
        assertThat(requestLines).allSatisfy(line ->
                assertThat(line.path("correlationId").asText()).isEqualTo(generated));
    }

    @Test
    @DisplayName("NFR-06.2: a supplied X-Correlation-Id abc-123 is echoed unchanged and used in the log lines")
    void echoesSuppliedCorrelationId(CapturedOutput output) throws Exception {
        int offset = output.getOut().length();
        String echoed = mvc.perform(get("/actuator/health").header(HEADER, "abc-123"))
                .andReturn().getResponse().getHeader(HEADER);

        assertThat(echoed).isEqualTo("abc-123");
        List<JsonNode> requestLines = jsonLinesOnCurrentThread(output.getOut().substring(offset));
        assertThat(requestLines).isNotEmpty();
        assertThat(requestLines).allSatisfy(line ->
                assertThat(line.path("correlationId").asText()).isEqualTo("abc-123"));
    }

    @Test
    @DisplayName("NFR-06 hardening: an unsafe X-Correlation-Id is replaced by a generated one, never echoed")
    void replacesUnsafeCorrelationId() throws Exception {
        String unsafe = "bad id \"<script>\"";

        String returned = mvc.perform(get("/actuator/health").header(HEADER, unsafe))
                .andReturn().getResponse().getHeader(HEADER);

        assertThat(returned).isNotBlank().isNotEqualTo(unsafe).matches("[A-Za-z0-9._-]{1,64}");
    }

    private static List<JsonNode> jsonLinesOnCurrentThread(String console) throws Exception {
        String thread = Thread.currentThread().getName();
        List<JsonNode> lines = new ArrayList<>();
        for (String raw : console.split("\\R")) {
            if (!raw.startsWith("{")) {
                continue;
            }
            JsonNode node = JSON.readTree(raw);
            if (thread.equals(node.path("thread_name").asText())) {
                lines.add(node);
            }
        }
        return lines;
    }
}
