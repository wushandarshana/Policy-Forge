package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.service.RuleSetJsonParser;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rule sets read from the shipped v1 files and the spec's worked risk inputs, for the rating tests. */
final class RatingFixtures {

    private static final RuleSetJsonParser PARSER = new RuleSetJsonParser();

    private RatingFixtures() {
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

    /** MOTOR v2 with baseRate 0.030 (AC-01.6), built from the v1 file. */
    static RuleSetVersion motorV2() {
        return parse(RuleSetFixtures.motorV2("2026-01-01"));
    }

    static Map<String, Object> termLife(int age, boolean smoker, String sumInsured, int termYears) {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("age", age);
        in.put("smoker", smoker);
        in.put("sumInsured", sumInsured);
        in.put("termYears", termYears);
        in.put("bmi", "24.5");
        in.put("healthDeclarations", List.of());
        return in;
    }

    /** AC-01.1 input: age 35, non-smoker, 5,000,000.00, 20 years. */
    static Map<String, Object> termLifeAc011() {
        return termLife(35, false, "5000000.00", 20);
    }

    /** AC-01.2 input. */
    static Map<String, Object> motorAc012() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("vehicleValue", "800000.00");
        in.put("vehicleAgeYears", 2);
        in.put("driverAge", 30);
        in.put("claimsLast3Years", 0);
        in.put("noClaimBonusYears", 2);
        in.put("vehicleUse", "PRIVATE");
        return in;
    }

    /** AC-01.3 input. */
    static Map<String, Object> householdAc013() {
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

    static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        copy.put(key, value);
        return copy;
    }

    static Map<String, Object> without(Map<String, Object> base, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        copy.remove(key);
        return copy;
    }
}
