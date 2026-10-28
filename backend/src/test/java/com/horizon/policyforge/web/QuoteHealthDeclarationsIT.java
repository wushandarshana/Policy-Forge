package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** AC-01.10: TERM_LIFE healthDeclarations is a list of at most 10 codes matching ^[A-Z][A-Z0-9_]{0,63}$. */
@Tag("AC-01")
@Tag("AC-01.10")
@SpringBootTest
@AutoConfigureMockMvc
class QuoteHealthDeclarationsIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    static Stream<Arguments> illegal() {
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("CODE_" + i);
        }
        return Stream.of(
                Arguments.of("an object entry", List.of(Map.of("aadhaar", "XXXX-XXXX-1234"))),
                Arguments.of("a numeric entry", List.of(123)),
                Arguments.of("a lower-case code", List.of("diabetes")),
                Arguments.of("a 65-character code", List.of("A".repeat(65))),
                Arguments.of("11 entries", eleven));
    }

    private MvcResult postQuote(Object healthDeclarations) throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("age", 35);
        inputs.put("smoker", false);
        inputs.put("sumInsured", "5000000.00");
        inputs.put("termYears", 20);
        inputs.put("bmi", "27.3189");
        inputs.put("healthDeclarations", healthDeclarations);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", "TERM_LIFE");
        body.put("riskInputs", inputs);
        return mvc.perform(post("/api/quotes").contentType("application/json")
                .content(json.writeValueAsString(body))).andReturn();
    }

    private int quotes() {
        return jdbc.queryForObject("select count(*) from quote", Integer.class);
    }

    @ParameterizedTest(name = "AC-01.10: healthDeclarations with {0} is a 400 naming healthDeclarations and stores nothing")
    @DisplayName("AC-01.10: illegal healthDeclarations is a 400 naming healthDeclarations and stores nothing")
    @MethodSource("illegal")
    void illegalHealthDeclarationsAreRejected(String label, Object value) throws Exception {
        int before = quotes();

        MvcResult result = postQuote(value);

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(400);
        JsonNode problem = json.readTree(result.getResponse().getContentAsString());
        List<String> fields = new ArrayList<>();
        problem.get("errors").forEach(e -> fields.add(e.get("field").asText()));
        assertThat(fields).contains("healthDeclarations");
        assertThat(quotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-01.10: healthDeclarations [DIABETES, ASTHMA] is accepted with 201 (guard)")
    void legalHealthDeclarationsAreAccepted() throws Exception {
        MvcResult result = postQuote(List.of("DIABETES", "ASTHMA"));
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
    }
}
