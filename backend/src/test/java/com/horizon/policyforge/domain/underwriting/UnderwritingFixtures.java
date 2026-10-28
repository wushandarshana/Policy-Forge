package com.horizon.policyforge.domain.underwriting;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.service.RuleSetJsonParser;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The shipped v1 rule files, a TERM_LIFE v2 fixture and the spec's risk profiles, for the underwriting tests. */
final class UnderwritingFixtures {

    private static final RuleSetJsonParser PARSER = new RuleSetJsonParser();

    private UnderwritingFixtures() {
    }

    static RuleSetVersion parse(String json) {
        RuleSetJsonParser.ParsedRuleSet parsed = PARSER.parse(json);
        if (!parsed.violations().isEmpty()) {
            throw new IllegalStateException("Fixture does not parse: " + parsed.violations());
        }
        return parsed.ruleSet();
    }

    static RuleSetVersion termLifeV1() {
        return parse(RuleSetFixtures.seedText("term-life", 1));
    }

    static RuleSetVersion motorV1() {
        return parse(RuleSetFixtures.seedText("motor", 1));
    }

    static RuleSetVersion householdV1() {
        return parse(RuleSetFixtures.seedText("household", 1));
    }

    /** TERM_LIFE v2 (U-15 test fixture, never a shipped file): v1 with the UW-TL-001 age threshold raised to 65. */
    static RuleSetVersion termLifeV2AgeThreshold65() {
        ObjectNode tree = RuleSetFixtures.seedTree("term-life", 1);
        tree.put("version", 2);
        tree.put("effectiveFrom", "2026-06-01");
        ArrayNode rules = RuleSetFixtures.rules(tree);
        ObjectNode ageRule = (ObjectNode) rules.get(0);
        if (!"UW-TL-001".equals(ageRule.get("code").asText())) {
            throw new IllegalStateException("UW-TL-001 is expected first in the TERM_LIFE v1 file");
        }
        ((ObjectNode) ageRule.get("all").get(0)).put("value", "65");
        return parse(RuleSetFixtures.render(tree));
    }

    /** Clean TERM_LIFE risk: age 35, non-smoker, BMI 24, no declarations, SI 5,000,000.00 (AC-04.1). */
    static Map<String, Object> termLife() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("age", 35);
        in.put("smoker", false);
        in.put("sumInsured", "5000000.00");
        in.put("termYears", 20);
        in.put("bmi", "24");
        in.put("healthDeclarations", List.of());
        return in;
    }

    /** Clean MOTOR risk (AC-01.2 profile): the derived sumInsured is the vehicle value. */
    static Map<String, Object> motor() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("vehicleValue", "800000.00");
        in.put("vehicleAgeYears", 2);
        in.put("driverAge", 30);
        in.put("claimsLast3Years", 0);
        in.put("noClaimBonusYears", 2);
        in.put("vehicleUse", "PRIVATE");
        return in;
    }

    /** Clean HOUSEHOLD risk (AC-01.3 profile): the derived sumInsured is building plus contents. */
    static Map<String, Object> household() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("buildingSumInsured", "6000000.00");
        in.put("contentsSumInsured", "1000000.00");
        in.put("propertyAgeYears", 12);
        in.put("constructionType", "BRICK");
        in.put("floodZone", "LOW");
        in.put("hasSecuritySystem", true);
        in.put("hasFloodDefences", false);
        return in;
    }

    static Map<String, Object> with(Map<String, Object> base, Object... keyValues) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        for (int i = 0; i < keyValues.length; i += 2) {
            copy.put((String) keyValues[i], keyValues[i + 1]);
        }
        return copy;
    }

    /** TERM_LIFE facts: the inputs already carry the age, and the sum insured is the input itself. */
    static Map<String, Object> termLifeFacts(Map<String, Object> riskInputs) {
        return UnderwritingRuleEvaluator.withDerived(riskInputs, new BigDecimal((String) riskInputs.get("sumInsured")),
                (Integer) riskInputs.get("age"));
    }

    static Map<String, Object> motorFacts(Map<String, Object> riskInputs) {
        return UnderwritingRuleEvaluator.withDerived(riskInputs,
                new BigDecimal((String) riskInputs.get("vehicleValue")), null);
    }

    static Map<String, Object> householdFacts(Map<String, Object> riskInputs) {
        BigDecimal total = new BigDecimal((String) riskInputs.get("buildingSumInsured"))
                .add(new BigDecimal((String) riskInputs.get("contentsSumInsured")));
        return UnderwritingRuleEvaluator.withDerived(riskInputs, total, null);
    }
}
