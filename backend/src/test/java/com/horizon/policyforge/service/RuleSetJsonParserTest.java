package com.horizon.policyforge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horizon.policyforge.domain.ruleset.RuleSetValidator;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.domain.ruleset.RuleSetViolation;
import com.horizon.policyforge.service.RuleSetJsonParser.ParsedRuleSet;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The JSON-to-domain reader behind the catalog (AC-02.5 shape errors, NFR-01 exact decimals). */
@Tag("AC-02")
class RuleSetJsonParserTest {

    private final RuleSetJsonParser parser = new RuleSetJsonParser();
    private final RuleSetValidator validator = new RuleSetValidator();

    private static List<String> paths(List<RuleSetViolation> violations) {
        return violations.stream().map(RuleSetViolation::path).toList();
    }

    @Test
    @DisplayName("AC-02.5: a seed file parses into BigDecimal values with no violations from the parser or the validator")
    void seedFileParsesExactly() {
        ParsedRuleSet parsed = parser.parse(RuleSetFixtures.seedText("motor", 1));

        assertThat(parsed.readable()).isTrue();
        assertThat(parsed.violations()).isEmpty();
        RuleSetVersion motor = parsed.ruleSet();
        assertThat(validator.validate(motor)).isEmpty();
        assertThat(motor.productCode()).isEqualTo("MOTOR");
        assertThat(motor.version()).isEqualTo(1);
        assertThat(motor.effectiveFrom()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(motor.rating().scalar("baseRate")).isEqualTo(new BigDecimal("0.025"));
        assertThat(motor.rating().scalar("baseRate").scale()).isEqualTo(3);
        assertThat(motor.rating().bandTable("vehicleAgeFactor")).hasSize(3);
        assertThat(motor.rating().lookup("useFactor").get("COMMERCIAL")).isEqualTo(new BigDecimal("1.40"));
        assertThat(motor.sumInsured().min()).isEqualTo(new BigDecimal("100000.00"));
        assertThat(motor.underwritingRules()).hasSize(4);
        assertThat(motor.lifecycle().cancellationFee()).isEqualTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("AC-02.5: a decimal written as a JSON number is a violation, because decimals must be strings")
    void jsonNumberForDecimalIsRejected() {
        ObjectNode draft = RuleSetFixtures.seedTree("motor", 1);
        ((ObjectNode) draft.get("rating")).put("baseRate", 0.025);

        ParsedRuleSet parsed = parser.parse(RuleSetFixtures.render(draft));

        assertThat(paths(parsed.violations())).contains("rating.baseRate");
    }

    @Test
    @DisplayName("AC-02.5: malformed JSON, a non-object body and a repeated field name are unreadable, not a crash")
    void unreadableBodies() {
        assertThat(parser.parse("{not json").readable()).isFalse();
        assertThat(parser.parse("[1,2]").readable()).isFalse();
        assertThat(parser.parse("").readable()).isFalse();
        assertThat(parser.parse("{\"productCode\":\"A\",\"productCode\":\"B\"}").readable()).isFalse();
        assertThat(paths(parser.parse("{not json").violations())).containsExactly("body");
    }

    @Test
    @DisplayName("AC-02.5: a bad date, a non-string operand and a non-whole number are each reported with their path")
    void shapeViolationsCarryPaths() {
        ObjectNode draft = RuleSetFixtures.seedTree("motor", 1);
        draft.put("effectiveFrom", "01/01/2026");
        ((ObjectNode) draft.get("lifecycle")).put("gracePeriodDays", "soon");
        ((ObjectNode) RuleSetFixtures.rules(draft).get(0).get("all").get(0)).put("value", 3);

        ParsedRuleSet parsed = parser.parse(RuleSetFixtures.render(draft));

        assertThat(paths(parsed.violations())).contains("effectiveFrom", "lifecycle.gracePeriodDays",
                "underwriting.rules[0].all[0].value");
    }

    @Test
    @DisplayName("AC-02.3: withVersion sets only the version field and keeps every other field and its order")
    void withVersionOnlyChangesVersion() {
        String original = RuleSetFixtures.render(RuleSetFixtures.seedTree("motor", 1));

        String renumbered = parser.withVersion(original, 7);

        assertThat(renumbered).isEqualTo(original.replace("\"version\":1", "\"version\":7"));
        assertThat(parser.withVersion("{not json", 7)).isEqualTo("{not json");
    }
}
