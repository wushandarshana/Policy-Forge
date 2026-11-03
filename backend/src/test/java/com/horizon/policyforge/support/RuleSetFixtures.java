package com.horizon.policyforge.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Rule-set JSON for tests: the shipped seed files and edited copies of them (the Catalog Manager workflow). */
public final class RuleSetFixtures {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RuleSetFixtures() {
    }

    /** The exact text of a shipped seed file, e.g. {@code seedText("motor", 1)}. */
    public static String seedText(String slug, int version) {
        try {
            return new ClassPathResource("rules/" + slug + "/v" + version + ".json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A mutable copy of a seed file. */
    public static ObjectNode seedTree(String slug, int version) {
        try {
            return (ObjectNode) JSON.readTree(seedText(slug, version));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String render(JsonNode tree) {
        try {
            return JSON.writeValueAsString(tree);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** MOTOR v1 edited to baseRate 0.030 (AC-01.6) and the given effective date. */
    public static String motorV2(String effectiveFrom) {
        ObjectNode tree = seedTree("motor", 1);
        tree.put("version", 2);
        tree.put("effectiveFrom", effectiveFrom);
        ((ObjectNode) tree.get("rating")).put("baseRate", "0.030");
        return render(tree);
    }

    /**
     * TERM_LIFE v1 edited so that UW-TL-001 declines above age 65 (AC-04.5, U-15). It is a test fixture, never a shipped
     * rule file, and every test that publishes it does so in its own application context.
     */
    public static String termLifeV2AgeThreshold65(String effectiveFrom) {
        ObjectNode tree = seedTree("term-life", 1);
        tree.put("version", 2);
        tree.put("effectiveFrom", effectiveFrom);
        ObjectNode ageRule = (ObjectNode) rules(tree).get(0);
        ageRule.put("description", "Entry age above 65");
        ((ObjectNode) ageRule.get("all").get(0)).put("value", "65");
        return render(tree);
    }

    /**
     * TERM_LIFE v1 edited to a policy fee of 1000.00 instead of 250.00, so the clean AC-04.1 profile quotes 7600.00
     * instead of 6850.00 (AC-06.2: an endorsement keeps the rule version its policy was priced with). It is a test
     * fixture, never a shipped rule file, and every test that publishes it does so in its own application context.
     */
    public static String termLifeV2PolicyFee1000(String effectiveFrom) {
        ObjectNode tree = seedTree("term-life", 1);
        tree.put("version", 2);
        tree.put("effectiveFrom", effectiveFrom);
        ((ObjectNode) tree.get("rating")).put("policyFee", "1000.00");
        return render(tree);
    }

    /** The {@code underwriting.rules} array of a rule-set tree. */
    public static ArrayNode rules(ObjectNode tree) {
        return (ArrayNode) tree.get("underwriting").get("rules");
    }
}
