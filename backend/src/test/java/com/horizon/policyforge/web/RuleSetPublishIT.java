package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizon.policyforge.service.NoActiveRuleSetException;
import com.horizon.policyforge.service.ResourceNotFoundException;
import com.horizon.policyforge.service.RuleSetService;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Publishing and resolving rule-set versions (AC-02.3 to AC-02.5, AC-02.7, new-product behaviour). These tests write
 * to the catalog, so each one gets a fresh application context and database (DirtiesContext after the method). The
 * clock is a movable test clock, so the active-version tests can step across a future effective date.
 */
@Tag("AC-02")
@SpringBootTest
@AutoConfigureMockMvc
class RuleSetPublishIT {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-28");
    private static final String PM_USER = "admin1";
    private static final String PASSWORD = "password";

    @TestConfiguration
    static class MovableClockConfig {
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
    private MutableClock clock;

    @Autowired
    private RuleSetService ruleSets;

    @BeforeEach
    void resetClock() {
        clock.setDate(TODAY);
    }

    private MvcResult publish(String user, String productCode, String body) throws Exception {
        return mvc.perform(post("/api/products/" + productCode + "/versions")
                        .with(httpBasic(user, PASSWORD))
                        .contentType("application/json")
                        .content(body))
                .andReturn();
    }

    private JsonNode activeVersions() throws Exception {
        String body = mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static int activeVersionOf(JsonNode products, String code) {
        for (JsonNode product : products) {
            if (code.equals(product.get("productCode").asText())) {
                return product.get("activeVersion").asInt();
            }
        }
        throw new AssertionError("product not listed: " + code);
    }

    private int storedVersions(String productCode) {
        return jdbc.queryForObject("select count(*) from rule_set_version where product_code = ?", Integer.class,
                productCode);
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("AC-02.3: a PRODUCT_MANAGER publish of a new MOTOR rule set is stored as version 2 and v1 is unchanged byte for byte, with an audit entry carrying the actor ID")
    void publishStoresVersionTwoAndLeavesVersionOneUntouched() throws Exception {
        MvcResult result = publish(PM_USER, "MOTOR", RuleSetFixtures.motorV2("2026-06-01"));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode created = json.readTree(result.getResponse().getContentAsString());
        assertThat(created.get("productCode").asText()).isEqualTo("MOTOR");
        assertThat(created.get("version").asInt()).isEqualTo(2);

        List<Integer> versions = jdbc.queryForList(
                "select version from rule_set_version where product_code = 'MOTOR' order by version", Integer.class);
        assertThat(versions).containsExactly(1, 2);

        String storedV1 = jdbc.queryForObject(
                "select rules_json from rule_set_version where product_code = 'MOTOR' and version = 1", String.class);
        assertThat(storedV1).as("v1 JSON byte for byte").isEqualTo(RuleSetFixtures.seedText("motor", 1));

        List<Map<String, Object>> audit = jdbc.queryForList(
                "select actor_id, action, entity_type, entity_id from audit_entry order by id");
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).get("ACTOR_ID")).isEqualTo(PM_USER);
        assertThat(audit.get(0).get("ENTITY_TYPE")).isEqualTo("RuleSetVersion");
        assertThat(audit.get(0).get("ENTITY_ID").toString()).contains("MOTOR").contains("2");
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("AC-02.4: a MOTOR v2 effective in the future is ignored until the clock reaches its effectiveFrom, and no rule set before v1 is a 404")
    void activeVersionFollowsTheClock() throws Exception {
        MvcResult result = publish(PM_USER, "MOTOR", RuleSetFixtures.motorV2("2027-01-01"));
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);

        clock.setDate(LocalDate.parse("2026-12-31"));
        assertThat(ruleSets.activeVersion("MOTOR").version()).as("day before v2 is effective").isEqualTo(1);
        assertThat(activeVersionOf(activeVersions(), "MOTOR")).isEqualTo(1);

        clock.setDate(LocalDate.parse("2027-01-01"));
        assertThat(ruleSets.activeVersion("MOTOR").version()).as("on v2's effectiveFrom").isEqualTo(2);
        assertThat(activeVersionOf(activeVersions(), "MOTOR")).isEqualTo(2);

        clock.setDate(LocalDate.parse("2025-12-31"));
        assertThatThrownBy(() -> ruleSets.activeVersion("MOTOR"))
                .as("before v1's effectiveFrom of 2026-01-01")
                .isInstanceOf(NoActiveRuleSetException.class)
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("AC-02.5: publishing a rule set with min greater than max, an unknown operator and duplicate rule codes is a 400 listing each violation and nothing is stored")
    void invalidRuleSetIsRejectedWithEveryViolationAndNothingStored() throws Exception {
        ObjectNode draft = RuleSetFixtures.seedTree("motor", 1);
        ObjectNode sumInsured = (ObjectNode) draft.get("sumInsured");
        sumInsured.put("min", "20000000.00");
        sumInsured.put("max", "10000000.00");
        ((ObjectNode) RuleSetFixtures.rules(draft).get(0).get("all").get(0)).put("op", "BETWEEN");
        ((ObjectNode) RuleSetFixtures.rules(draft).get(1)).put("code", "UW-MO-001");

        MvcResult result = publish(PM_USER, "MOTOR", RuleSetFixtures.render(draft));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        JsonNode errors = json.readTree(result.getResponse().getContentAsString()).get("errors");
        List<String> fields = new ArrayList<>();
        errors.forEach(error -> {
            fields.add(error.get("field").asText());
            assertThat(error.get("message").asText()).isNotBlank();
        });
        assertThat(fields).contains("sumInsured", "underwriting.rules[0].all[0].op", "underwriting.rules[1].code");

        assertThat(storedVersions("MOTOR")).as("nothing stored").isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_entry", Integer.class))
                .as("a rejected publish is not audited as a publish").isZero();
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("AC-02.7: a CUSTOMER who posts to /api/products/MOTOR/versions gets 403 and nothing is stored")
    void customerCannotPublish() throws Exception {
        MvcResult result = publish("customer1", "MOTOR", RuleSetFixtures.motorV2("2026-06-01"));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(storedVersions("MOTOR")).isEqualTo(1);
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("AC-02 new product: publishing version 1 for an unknown productCode creates the product when its ratingModel is known")
    void versionOneOfUnknownProductCreatesIt() throws Exception {
        ObjectNode draft = RuleSetFixtures.seedTree("motor", 1);
        draft.put("productCode", "MOTOR_PLUS");
        draft.put("productName", "Horizon Motor Plus");

        MvcResult result = publish(PM_USER, "MOTOR_PLUS", RuleSetFixtures.render(draft));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("version").asInt()).isEqualTo(1);
        assertThat(activeVersionOf(activeVersions(), "MOTOR_PLUS")).isEqualTo(1);
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("AC-02 new product: an unknown ratingModel on a new product is a 400 and nothing is stored")
    void unknownRatingModelIsRejected() throws Exception {
        ObjectNode draft = RuleSetFixtures.seedTree("motor", 1);
        draft.put("productCode", "PET_COVER");
        draft.put("ratingModel", "PET");

        MvcResult result = publish(PM_USER, "PET_COVER", RuleSetFixtures.render(draft));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("ratingModel");
        assertThat(storedVersions("PET_COVER")).isZero();
    }

    @Test
    @Tag("NFR-04")
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("NFR-04.3.1: a PRODUCT_MANAGER POST of a valid rule set to /api/products/MOTOR/versions as text/plain is a 415 and MOTOR still has only version 1")
    void publishAsTextPlainIsUnsupportedMediaType() throws Exception {
        MvcResult result = mvc.perform(post("/api/products/MOTOR/versions")
                        .with(httpBasic(PM_USER, PASSWORD))
                        .contentType("text/plain")
                        .content(RuleSetFixtures.motorV2("2026-06-01")))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(415);
        assertThat(storedVersions("MOTOR")).isEqualTo(1);
    }
}
