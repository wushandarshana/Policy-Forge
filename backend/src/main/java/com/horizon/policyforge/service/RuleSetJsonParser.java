package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizon.policyforge.domain.ruleset.Band;
import com.horizon.policyforge.domain.ruleset.Condition;
import com.horizon.policyforge.domain.ruleset.Lifecycle;
import com.horizon.policyforge.domain.ruleset.RatingFactors;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.domain.ruleset.RuleSetViolation;
import com.horizon.policyforge.domain.ruleset.SumInsuredLimits;
import com.horizon.policyforge.domain.ruleset.UnderwritingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads rule-set JSON into the domain model. JSON handling lives here, outside the domain layer.
 *
 * <p>Parsing is tolerant on purpose: a missing section becomes {@code null} in the draft and is reported by
 * {@code RuleSetValidator}, while shape problems only the parser can see (a decimal written as a JSON number, an
 * unparseable date) come back as violations of their own. Decimals must be JSON strings so they reach
 * {@link BigDecimal} exactly (NFR-01).
 */
@Component
public class RuleSetJsonParser {

    /** Result of reading a document. {@code readable} is false when the text is not a JSON object at all. */
    public record ParsedRuleSet(RuleSetVersion ruleSet, List<RuleSetViolation> violations, boolean readable) {
    }

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    /** The document as a JSON tree, for returning a stored rule set to a client. */
    public JsonNode document(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored rule set is not valid JSON", e);
        }
    }

    /**
     * Returns the document with its {@code version} field set to {@code version}, keeping every other field as it
     * was. The server assigns the version number (current max + 1), so a client's own value is not trusted. Text that
     * is not a JSON object is returned unchanged, and {@link #parse} reports it.
     */
    public String withVersion(String json, int version) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root instanceof ObjectNode object) {
                object.put("version", version);
                return mapper.writeValueAsString(object);
            }
        } catch (JsonProcessingException e) {
            // Reported by parse().
        }
        return json;
    }

    public ParsedRuleSet parse(String json) {
        List<RuleSetViolation> violations = new ArrayList<>();
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            violations.add(new RuleSetViolation("body", "is not valid JSON, or repeats a field name"));
            return new ParsedRuleSet(empty(), violations, false);
        }
        if (root == null || !root.isObject()) {
            violations.add(new RuleSetViolation("body", "must be a JSON object"));
            return new ParsedRuleSet(empty(), violations, false);
        }

        Integer version = integer(root.get("version"), "version", violations);
        RuleSetVersion ruleSet = new RuleSetVersion(
                text(root, "productCode", violations),
                text(root, "productName", violations),
                version == null ? 0 : version,
                date(root.get("effectiveFrom"), "effectiveFrom", violations),
                text(root, "ratingModel", violations),
                text(root, "currency", violations),
                sumInsured(object(root, "sumInsured", violations), violations),
                rating(object(root, "rating", violations), violations),
                underwriting(object(root, "underwriting", violations), violations),
                lifecycle(object(root, "lifecycle", violations), violations));
        return new ParsedRuleSet(ruleSet, violations, true);
    }

    private static RuleSetVersion empty() {
        return new RuleSetVersion(null, null, 0, null, null, null, null, null, null, null);
    }

    private SumInsuredLimits sumInsured(JsonNode node, List<RuleSetViolation> violations) {
        if (node == null) {
            return null;
        }
        return new SumInsuredLimits(
                decimal(node.get("min"), "sumInsured.min", violations),
                decimal(node.get("max"), "sumInsured.max", violations));
    }

    private RatingFactors rating(JsonNode node, List<RuleSetViolation> violations) {
        if (node == null) {
            return null;
        }
        Map<String, BigDecimal> scalars = new LinkedHashMap<>();
        Map<String, List<Band>> bands = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> lookups = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            String path = "rating." + name;
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                bands.put(name, bandRows(value, path, violations));
            } else if (value.isObject()) {
                lookups.put(name, lookupEntries(value, path, violations));
            } else {
                BigDecimal scalar = decimal(value, path, violations);
                if (scalar != null) {
                    scalars.put(name, scalar);
                }
            }
        });
        return new RatingFactors(scalars, bands, lookups);
    }

    private List<Band> bandRows(JsonNode array, String path, List<RuleSetViolation> violations) {
        List<Band> rows = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode row = array.get(i);
            String rowPath = path + "[" + i + "]";
            if (!row.isObject()) {
                violations.add(new RuleSetViolation(rowPath, "must be an object with min, max and factor"));
                continue;
            }
            Integer min = integer(row.get("min"), rowPath + ".min", violations);
            Integer max = integer(row.get("max"), rowPath + ".max", violations);
            BigDecimal factor = decimal(row.get("factor"), rowPath + ".factor", violations);
            if (min == null || max == null) {
                if (row.get("min") == null) {
                    violations.add(new RuleSetViolation(rowPath + ".min", "is required"));
                }
                if (row.get("max") == null) {
                    violations.add(new RuleSetViolation(rowPath + ".max", "is required"));
                }
                continue;
            }
            if (factor == null && row.get("factor") == null) {
                violations.add(new RuleSetViolation(rowPath + ".factor", "is required"));
            }
            rows.add(new Band(min, max, factor));
        }
        return rows;
    }

    private Map<String, BigDecimal> lookupEntries(JsonNode object, String path, List<RuleSetViolation> violations) {
        Map<String, BigDecimal> entries = new LinkedHashMap<>();
        object.fields().forEachRemaining(entry -> {
            BigDecimal value = decimal(entry.getValue(), path + "." + entry.getKey(), violations);
            if (value != null) {
                entries.put(entry.getKey(), value);
            }
        });
        return entries;
    }

    private List<UnderwritingRule> underwriting(JsonNode node, List<RuleSetViolation> violations) {
        if (node == null) {
            return null;
        }
        JsonNode rules = node.get("rules");
        if (rules == null || rules.isNull()) {
            return null;
        }
        if (!rules.isArray()) {
            violations.add(new RuleSetViolation("underwriting.rules", "must be an array"));
            return null;
        }
        List<UnderwritingRule> result = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            JsonNode rule = rules.get(i);
            String path = "underwriting.rules[" + i + "]";
            if (!rule.isObject()) {
                violations.add(new RuleSetViolation(path, "must be an object"));
                continue;
            }
            result.add(new UnderwritingRule(
                    text(rule, "code", path, violations),
                    text(rule, "outcome", path, violations),
                    text(rule, "description", path, violations),
                    conditions(rule.get("all"), path + ".all", violations)));
        }
        return result;
    }

    private List<Condition> conditions(JsonNode array, String path, List<RuleSetViolation> violations) {
        List<Condition> result = new ArrayList<>();
        if (array == null || array.isNull()) {
            return result;
        }
        if (!array.isArray()) {
            violations.add(new RuleSetViolation(path, "must be an array"));
            return result;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonNode condition = array.get(i);
            String conditionPath = path + "[" + i + "]";
            if (!condition.isObject()) {
                violations.add(new RuleSetViolation(conditionPath, "must be an object"));
                continue;
            }
            String value = null;
            List<String> values = new ArrayList<>();
            JsonNode operand = condition.get("value");
            if (operand != null && operand.isArray()) {
                for (int j = 0; j < operand.size(); j++) {
                    if (operand.get(j).isTextual()) {
                        values.add(operand.get(j).asText());
                    } else {
                        violations.add(new RuleSetViolation(conditionPath + ".value[" + j + "]", "must be a string"));
                    }
                }
            } else if (operand != null && !operand.isNull()) {
                if (operand.isTextual()) {
                    value = operand.asText();
                } else {
                    violations.add(new RuleSetViolation(conditionPath + ".value", "must be a string"));
                }
            }
            result.add(new Condition(
                    text(condition, "field", conditionPath, violations),
                    text(condition, "op", conditionPath, violations),
                    value,
                    values));
        }
        return result;
    }

    private Lifecycle lifecycle(JsonNode node, List<RuleSetViolation> violations) {
        if (node == null) {
            return null;
        }
        return new Lifecycle(
                integer(node.get("termMonths"), "lifecycle.termMonths", violations),
                integer(node.get("gracePeriodDays"), "lifecycle.gracePeriodDays", violations),
                integer(node.get("coolingOffDays"), "lifecycle.coolingOffDays", violations),
                decimal(node.get("cancellationFee"), "lifecycle.cancellationFee", violations),
                integer(node.get("renewalOfferDaysBeforeExpiry"), "lifecycle.renewalOfferDaysBeforeExpiry",
                        violations),
                integer(node.get("quoteValidityDays"), "lifecycle.quoteValidityDays", violations));
    }

    // ---- primitive readers: a missing or null field is null with no violation (the validator reports it) ----

    private static JsonNode object(JsonNode parent, String field, List<RuleSetViolation> violations) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            violations.add(new RuleSetViolation(field, "must be an object"));
            return null;
        }
        return node;
    }

    private static String text(JsonNode parent, String field, List<RuleSetViolation> violations) {
        return text(parent, field, null, violations);
    }

    private static String text(JsonNode parent, String field, String parentPath, List<RuleSetViolation> violations) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            violations.add(new RuleSetViolation(parentPath == null ? field : parentPath + "." + field,
                    "must be a string"));
            return null;
        }
        return node.asText();
    }

    private static BigDecimal decimal(JsonNode node, String path, List<RuleSetViolation> violations) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            violations.add(new RuleSetViolation(path, "must be a decimal string such as \"0.025\", not a JSON number"));
            return null;
        }
        if (!node.isTextual()) {
            violations.add(new RuleSetViolation(path, "must be a decimal string"));
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            violations.add(new RuleSetViolation(path, "must be a decimal string"));
            return null;
        }
    }

    private static Integer integer(JsonNode node, String path, List<RuleSetViolation> violations) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            try {
                return Integer.valueOf(node.asText());
            } catch (NumberFormatException e) {
                // fall through to the violation
            }
        }
        violations.add(new RuleSetViolation(path, "must be a whole number"));
        return null;
    }

    private static LocalDate date(JsonNode node, String path, List<RuleSetViolation> violations) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            try {
                return LocalDate.parse(node.asText());
            } catch (DateTimeParseException e) {
                // fall through to the violation
            }
        }
        violations.add(new RuleSetViolation(path, "must be an ISO date such as 2026-01-01"));
        return null;
    }
}
