package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Product catalog reads on a fresh start (AC-02.1, AC-02.2). The property below gives this class its own application
 * context, so a test elsewhere that publishes MOTOR v2 cannot change what "a fresh start" means here.
 */
@Tag("AC-02")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "policyforge.test.context=catalog-read")
class ProductCatalogReadIT {

    private static final List<String> LAUNCH_PRODUCTS = List.of("TERM_LIFE", "MOTOR", "HOUSEHOLD");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private JsonNode getJson(String path) throws Exception {
        String body = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    @DisplayName("AC-02.1: GET /api/products returns exactly TERM_LIFE, MOTOR and HOUSEHOLD, each with active version 1 and a distinct rating model")
    void freshStartListsTheThreeLaunchProducts() throws Exception {
        JsonNode products = getJson("/api/products");

        assertThat(products.isArray()).isTrue();
        List<String> codes = new ArrayList<>();
        Set<String> models = new HashSet<>();
        for (JsonNode product : products) {
            codes.add(product.get("productCode").asText());
            models.add(product.get("ratingModel").asText());
            assertThat(product.get("activeVersion").asInt()).as("active version of %s", product.get("productCode"))
                    .isEqualTo(1);
            assertThat(product.get("productName").asText()).isNotBlank();
        }
        assertThat(codes).containsExactlyInAnyOrderElementsOf(LAUNCH_PRODUCTS);
        assertThat(models).as("distinct rating models").hasSize(3);
    }

    @Test
    @DisplayName("AC-02.2: the three v1 rule sets differ in rating factors, underwriting code prefixes and lifecycle parameters")
    void v1RuleSetsDiffer() throws Exception {
        JsonNode termLife = getJson("/api/products/TERM_LIFE/versions").get(0).get("ruleSet");
        JsonNode motor = getJson("/api/products/MOTOR/versions").get(0).get("ruleSet");
        JsonNode household = getJson("/api/products/HOUSEHOLD/versions").get(0).get("ruleSet");
        List<JsonNode> all = List.of(termLife, motor, household);

        // Rating factors: no two products share the same factor names.
        List<Set<String>> factorNames = all.stream().map(rs -> fieldNames(rs.get("rating"))).toList();
        assertDistinct(factorNames, "rating factor names");

        // Underwriting codes: each product uses its own prefix, and no code is shared.
        assertThat(codes(termLife)).isNotEmpty().allMatch(code -> code.startsWith("UW-TL-"));
        assertThat(codes(motor)).isNotEmpty().allMatch(code -> code.startsWith("UW-MO-"));
        assertThat(codes(household)).isNotEmpty().allMatch(code -> code.startsWith("UW-HH-"));

        // Lifecycle parameters differ for every pair of products.
        assertDistinct(all.stream().map(rs -> rs.get("lifecycle").toString()).toList(), "lifecycle parameters");
        assertThat(all.stream().map(rs -> rs.get("lifecycle").get("cancellationFee").asText()).toList())
                .containsExactly("500.00", "250.00", "200.00");
    }

    private static <T> void assertDistinct(List<T> values, String what) {
        assertThat(new HashSet<>(values)).as("%s must differ between the three products", what)
                .hasSize(values.size());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> codes(JsonNode ruleSet) {
        List<String> codes = new ArrayList<>();
        ruleSet.get("underwriting").get("rules").forEach(rule -> codes.add(rule.get("code").asText()));
        return codes;
    }
}
