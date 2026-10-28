package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.repository.QuoteEntity;
import com.horizon.policyforge.repository.QuoteRepository;
import com.horizon.policyforge.support.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The public quote API against the real application (AC-01 at API level, NFR-04.2, NFR-03). The clock is a fixed test
 * clock so {@code validUntil} is exact. These tests only add quote rows, so they share one context and count rows
 * before and after where "nothing is stored" matters.
 */
@Tag("AC-01")
@Tag("NFR-04")
@Tag("NFR-03")
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class QuoteApiIT {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-28");
    private static final String QUOTE_ID_PATTERN =
            "^Q-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
    private static final String HEALTH_TEXT = "SYNTHETIC_DECLARATION_ALPHA";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(TODAY);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QuoteRepository quoteRepository;

    private static Map<String, Object> termLifeInputs(Object sumInsured) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("age", 35);
        inputs.put("smoker", false);
        inputs.put("sumInsured", sumInsured);
        inputs.put("termYears", 20);
        inputs.put("bmi", "27.3189");
        inputs.put("healthDeclarations", List.of(HEALTH_TEXT));
        return inputs;
    }

    private static Map<String, Object> motorInputs() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("vehicleValue", "800000.00");
        inputs.put("vehicleAgeYears", 2);
        inputs.put("driverAge", 30);
        inputs.put("claimsLast3Years", 0);
        inputs.put("noClaimBonusYears", 2);
        inputs.put("vehicleUse", "PRIVATE");
        return inputs;
    }

    private static Map<String, Object> householdInputs() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("buildingSumInsured", "6000000.00");
        inputs.put("contentsSumInsured", "1000000.00");
        inputs.put("propertyAgeYears", 12);
        inputs.put("constructionType", "BRICK");
        inputs.put("floodZone", "LOW");
        inputs.put("hasSecuritySystem", true);
        inputs.put("hasFloodDefences", false);
        return inputs;
    }

    private MvcResult postQuote(String productCode, Map<String, Object> riskInputs) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (productCode != null) {
            body.put("productCode", productCode);
        }
        if (riskInputs != null) {
            body.put("riskInputs", riskInputs);
        }
        return mvc.perform(post("/api/quotes").contentType("application/json").content(json.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private int storedQuotes() {
        return jdbc.queryForObject("select count(*) from quote", Integer.class);
    }

    private static List<String> errorFields(JsonNode problem) {
        TreeSet<String> fields = new TreeSet<>();
        problem.get("errors").forEach(error -> fields.add(error.get("field").asText()));
        return new ArrayList<>(fields);
    }

    @Test
    @DisplayName("AC-01.1: an anonymous POST /api/quotes for TERM_LIFE age 35, non-smoker, 5,000,000.00, 20 years is a 201 with premium 6850.00 and ruleVersion 1")
    void termLifeQuoteIsPricedFromTheActiveRuleVersion() throws Exception {
        int before = storedQuotes();
        MvcResult result = postQuote("TERM_LIFE", termLifeInputs("5000000.00"));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode quote = bodyOf(result);
        assertThat(quote.get("annualPremium").asText()).isEqualTo("6850.00");
        assertThat(quote.get("sumInsured").asText()).isEqualTo("5000000.00");
        assertThat(quote.get("ruleVersion").asInt()).isEqualTo(1);
        assertThat(quote.get("productCode").asText()).isEqualTo("TERM_LIFE");
        assertThat(quote.get("quoteId").asText()).matches(QUOTE_ID_PATTERN);
        assertThat(quote.get("validUntil").asText()).as("today + quoteValidityDays (30)").isEqualTo("2026-11-27");
        assertThat(quote.get("breakdown").isArray()).isTrue();
        assertThat(quote.get("breakdown")).isNotEmpty().allSatisfy(item -> {
            assertThat(item.get("factor").asText()).isNotBlank();
            assertThat(item.get("value").isTextual()).as("breakdown values are decimal strings").isTrue();
        });
        assertThat(quote.get("breakdown").get(0).get("factor").asText()).isEqualTo("sumInsured");

        assertThat(storedQuotes()).isEqualTo(before + 1);
        Map<String, Object> row = jdbc.queryForMap(
                "select product_code, rule_version, annual_premium, sum_insured, valid_until from quote where quote_id = ?",
                quote.get("quoteId").asText());
        assertThat(row.get("PRODUCT_CODE")).isEqualTo("TERM_LIFE");
        assertThat(row.get("RULE_VERSION")).isEqualTo(1);
        assertThat(row.get("ANNUAL_PREMIUM").toString()).isEqualTo("6850.00");
        assertThat(row.get("SUM_INSURED").toString()).isEqualTo("5000000.00");
    }

    @Test
    @DisplayName("AC-01.2: the MOTOR spec example quoted through the API is 15150.00 on rule version 1")
    void motorQuoteThroughTheApi() throws Exception {
        MvcResult result = postQuote("MOTOR", motorInputs());

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode quote = bodyOf(result);
        assertThat(quote.get("annualPremium").asText()).isEqualTo("15150.00");
        assertThat(quote.get("sumInsured").asText()).isEqualTo("800000.00");
        assertThat(quote.get("ruleVersion").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-01.3: the HOUSEHOLD spec example quoted through the API is 11350.00 with sum insured building plus contents")
    void householdQuoteThroughTheApi() throws Exception {
        MvcResult result = postQuote("HOUSEHOLD", householdInputs());

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode quote = bodyOf(result);
        assertThat(quote.get("annualPremium").asText()).isEqualTo("11350.00");
        assertThat(quote.get("sumInsured").asText()).isEqualTo("7000000.00");
    }

    @Test
    @DisplayName("AC-01.7: a sum insured below the minimum, a missing termYears and a missing smoker are one 400 naming each field and no quote is stored")
    void invalidInputsAreRejectedNamingEachField() throws Exception {
        Map<String, Object> inputs = termLifeInputs("100000.00");
        inputs.remove("termYears");
        inputs.remove("smoker");
        int before = storedQuotes();

        MvcResult result = postQuote("TERM_LIFE", inputs);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        JsonNode problem = bodyOf(result);
        assertThat(errorFields(problem)).containsExactly("smoker", "sumInsured", "termYears");
        problem.get("errors").forEach(error -> assertThat(error.get("message").asText()).isNotBlank());
        assertThat(storedQuotes()).as("no quote stored for a rejected request").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-01.7: a MOTOR vehicle value above the maximum is a 400 naming vehicleValue, and an absent riskInputs or productCode is a 400 too")
    void outOfRangeAndMissingRequestParts() throws Exception {
        int before = storedQuotes();

        Map<String, Object> tooExpensive = motorInputs();
        tooExpensive.put("vehicleValue", "20000000.00");
        MvcResult motor = postQuote("MOTOR", tooExpensive);
        assertThat(motor.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorFields(bodyOf(motor))).containsExactly("vehicleValue");

        MvcResult noInputs = postQuote("MOTOR", null);
        assertThat(noInputs.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorFields(bodyOf(noInputs))).containsExactly("riskInputs");

        MvcResult noProduct = postQuote(null, motorInputs());
        assertThat(noProduct.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorFields(bodyOf(noProduct))).containsExactly("productCode");

        assertThat(storedQuotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-01.7: a quote for an unknown product is a 404 problem+json and nothing is stored")
    void unknownProductIsNotFound() throws Exception {
        int before = storedQuotes();

        MvcResult result = postQuote("PET_INSURANCE", motorInputs());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(storedQuotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.2: an anonymous GET of a stored TERM_LIFE quote with BMI and health declarations returns premium, breakdown, rule version and validity but no health inputs, and the ID is Q-UUIDv4")
    void publicQuoteReadOmitsHealthInputs() throws Exception {
        JsonNode created = bodyOf(postQuote("TERM_LIFE", termLifeInputs("5000000.00")));
        String quoteId = created.get("quoteId").asText();
        assertThat(quoteId).matches(QUOTE_ID_PATTERN);

        String stored = jdbc.queryForObject("select risk_inputs_json from quote where quote_id = ?", String.class,
                quoteId);
        assertThat(stored).as("the full inputs are kept server-side").contains("27.3189").contains(HEALTH_TEXT);

        MvcResult read = mvc.perform(get("/api/quotes/" + quoteId)).andReturn();

        assertThat(read.getResponse().getStatus()).as("anonymous read").isEqualTo(200);
        String raw = read.getResponse().getContentAsString();
        JsonNode quote = json.readTree(raw);
        assertThat(quote.get("quoteId").asText()).isEqualTo(quoteId).matches(QUOTE_ID_PATTERN);
        assertThat(quote.get("annualPremium").asText()).isEqualTo("6850.00");
        assertThat(quote.get("ruleVersion").asInt()).isEqualTo(1);
        assertThat(quote.get("validUntil").asText()).isEqualTo("2026-11-27");
        assertThat(quote.get("breakdown")).isNotEmpty();
        assertThat(quote.get("breakdown")).isEqualTo(created.get("breakdown"));
        assertThat(raw).doesNotContain("bmi").doesNotContain("healthDeclarations").doesNotContain("27.3189")
                .doesNotContain(HEALTH_TEXT);
        // The non-health inputs may be shown, but never the health ones.
        if (quote.has("riskInputs")) {
            assertThat(quote.get("riskInputs").has("bmi")).isFalse();
            assertThat(quote.get("riskInputs").has("healthDeclarations")).isFalse();
        }
    }

    @Test
    @DisplayName("NFR-04.2: quote IDs are unguessable, so two identical quotes get different Q-UUIDv4 IDs")
    void quoteIdsAreRandom() throws Exception {
        String first = bodyOf(postQuote("MOTOR", motorInputs())).get("quoteId").asText();
        String second = bodyOf(postQuote("MOTOR", motorInputs())).get("quoteId").asText();

        assertThat(first).matches(QUOTE_ID_PATTERN);
        assertThat(second).matches(QUOTE_ID_PATTERN);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("NFR-04.2: an unknown quote ID is a 404 problem+json, for a well-formed ID and for garbage")
    void unknownQuoteIsNotFound() throws Exception {
        for (String id : List.of("Q-00000000-0000-4000-8000-000000000000", "not-a-quote")) {
            MvcResult result = mvc.perform(get("/api/quotes/" + id)).andReturn();
            assertThat(result.getResponse().getStatus()).as(id).isEqualTo(404);
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        }
    }

    @Test
    @DisplayName("NFR-03: quoting logs the quote ID but never the risk inputs, BMI or health declaration text, for accepted and rejected requests")
    void quotingLogsIdsOnly(CapturedOutput output) throws Exception {
        int offset = output.getOut().length();
        String quoteId = bodyOf(postQuote("TERM_LIFE", termLifeInputs("5000000.00"))).get("quoteId").asText();
        mvc.perform(get("/api/quotes/" + quoteId)).andReturn();
        Map<String, Object> bad = termLifeInputs("100000.00");
        postQuote("TERM_LIFE", bad);

        String logged = output.getOut().substring(offset);
        assertThat(logged).as("the quote ID is logged").contains(quoteId);
        assertThat(logged).doesNotContain(HEALTH_TEXT).doesNotContain("27.3189").doesNotContain("healthDeclarations")
                .doesNotContain("5000000.00");
    }

    @Test
    @Tag("NFR-04")
    @DisplayName("NFR-04.3.2: an anonymous POST /api/quotes as text/plain with a valid JSON body is a 415 and no quote is stored")
    void quoteAsTextPlainIsUnsupportedMediaType() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", "MOTOR");
        body.put("riskInputs", motorInputs());
        int before = storedQuotes();

        MvcResult result = mvc.perform(post("/api/quotes").contentType("text/plain")
                .content(json.writeValueAsString(body))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(storedQuotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-01.9: extra BMI and aadhaar keys in TERM_LIFE riskInputs are a 400 problem+json naming each key and no quote is stored")
    void unknownRiskInputKeysAreRejectedNamingEachKey() throws Exception {
        Map<String, Object> inputs = termLifeInputs("5000000.00");
        inputs.put("BMI", "27.3");
        inputs.put("aadhaar", "XXXX-XXXX-1234");
        int before = storedQuotes();

        MvcResult result = postQuote("TERM_LIFE", inputs);

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(errorFields(bodyOf(result))).contains("BMI", "aadhaar");
        assertThat(storedQuotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-01.9: an anonymous POST /api/quotes with a body over 16 KB is a 413 and no quote is stored")
    void oversizedQuoteBodyIsRejected() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", "TERM_LIFE");
        body.put("riskInputs", termLifeInputs("5000000.00"));
        // Valid JSON padded with trailing whitespace past 16 KB, so only the size check can reject it.
        String text = json.writeValueAsString(body);
        String padded = text + " ".repeat(20 * 1024 - text.length());
        int before = storedQuotes();

        MvcResult result = mvc.perform(post("/api/quotes").contentType("application/json").content(padded))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(413);
        assertThat(storedQuotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("NFR-04.2: the public read of a stored TERM_LIFE quote exposes exactly the declared non-health riskInputs keys age, smoker, sumInsured and termYears")
    void publicQuoteReadRiskInputsAreAnExactAllowList() throws Exception {
        String quoteId = bodyOf(postQuote("TERM_LIFE", termLifeInputs("5000000.00"))).get("quoteId").asText();

        JsonNode quote = bodyOf(mvc.perform(get("/api/quotes/" + quoteId)).andReturn());

        assertThat(quote.has("riskInputs")).isTrue();
        TreeSet<String> keys = new TreeSet<>();
        quote.get("riskInputs").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("age", "smoker", "sumInsured", "termYears");

        // A stored row whose risk inputs hold undeclared keys (written straight to the repository, because the API
        // rejects such keys): the public read is still exactly the declared non-health inputs, never the extras.
        Map<String, Object> tampered = termLifeInputs("5000000.00");
        tampered.put("BMI", "31.4159");
        tampered.put("aadhaar", "XXXX-XXXX-9876");
        tampered.put("medicalHistory", "SYNTHETIC-HISTORY-OMEGA");
        String storedId = "Q-" + UUID.randomUUID();
        quoteRepository.save(QuoteEntity.of(storedId, "TERM_LIFE", 1, new BigDecimal("5000000.00"),
                new BigDecimal("6850.00"), "[{\"factor\":\"policyFee\",\"value\":\"250.00\"}]",
                json.writeValueAsString(tampered), TODAY.plusDays(30), Instant.parse("2026-10-28T00:00:00Z")));

        MvcResult read = mvc.perform(get("/api/quotes/" + storedId)).andReturn();

        assertThat(read.getResponse().getStatus()).as("anonymous read").isEqualTo(200);
        String raw = read.getResponse().getContentAsString();
        TreeSet<String> storedKeys = new TreeSet<>();
        json.readTree(raw).get("riskInputs").fieldNames().forEachRemaining(storedKeys::add);
        assertThat(storedKeys).containsExactly("age", "smoker", "sumInsured", "termYears");
        assertThat(raw).doesNotContain("BMI").doesNotContain("31.4159").doesNotContain("aadhaar")
                .doesNotContain("XXXX-XXXX-9876").doesNotContain("medicalHistory")
                .doesNotContain("SYNTHETIC-HISTORY-OMEGA").doesNotContain("bmi").doesNotContain(HEALTH_TEXT);
    }
}
