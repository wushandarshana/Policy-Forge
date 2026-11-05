package com.horizon.policyforge.domain.underwriting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.ruleset.Condition;
import com.horizon.policyforge.domain.ruleset.ConditionOperator;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Operator semantics of the condition evaluator (AC-04 mechanics) and the fail-closed rule of U-6. */
@Tag("AC-04")
class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private boolean holds(String field, String op, String value, Object fact) {
        Map<String, Object> facts = new HashMap<>();
        facts.put(field, fact);
        return evaluator.holds(Condition.of(field, op, value), facts);
    }

    @Test
    @DisplayName("U-5: GT, GTE, LT and LTE compare as BigDecimal across integers, strings and BigDecimal values")
    void numericComparisonsAreExact() {
        assertThat(holds("age", "GT", "60", 61)).isTrue();
        assertThat(holds("age", "GT", "60", 60)).isFalse();
        assertThat(holds("age", "GTE", "60", 60)).isTrue();
        assertThat(holds("age", "LT", "21", 20)).isTrue();
        assertThat(holds("age", "LT", "21", 21)).isFalse();
        assertThat(holds("age", "LTE", "21", 21)).isTrue();
        assertThat(holds("bmi", "GT", "35", "35.0000000001")).isTrue();
        assertThat(holds("bmi", "GT", "35", "35.00")).isFalse();
        assertThat(holds("sumInsured", "GT", "10000000", new BigDecimal("10000000.00"))).isFalse();
        assertThat(holds("sumInsured", "GT", "10000000", new BigDecimal("10000000.01"))).isTrue();
        assertThat(holds("n", "GT", "5", 6L)).isTrue();
        assertThat(holds("n", "GT", "5", BigInteger.valueOf(6))).isTrue();
        // A value that a double would round onto the threshold is still above it.
        assertThat(holds("sumInsured", "GT", "9007199254740992", new BigDecimal("9007199254740993"))).isTrue();
    }

    @Test
    @DisplayName("U-5: EQ and NEQ compare numbers by value, booleans and strings by text")
    void equalityIsTypeAware() {
        assertThat(holds("vehicleUse", "EQ", "COMMERCIAL", "COMMERCIAL")).isTrue();
        assertThat(holds("vehicleUse", "EQ", "COMMERCIAL", "PRIVATE")).isFalse();
        assertThat(holds("vehicleUse", "NEQ", "COMMERCIAL", "PRIVATE")).isTrue();
        assertThat(holds("hasFloodDefences", "EQ", "false", false)).isTrue();
        assertThat(holds("hasFloodDefences", "EQ", "false", true)).isFalse();
        assertThat(holds("hasFloodDefences", "NEQ", "false", true)).isTrue();
        assertThat(holds("termYears", "EQ", "20", 20)).isTrue();
        assertThat(holds("sumInsured", "EQ", "5000000", new BigDecimal("5000000.00"))).isTrue();
        assertThat(holds("sumInsured", "EQ", "5000000", "5000000.00")).isTrue();
        assertThat(holds("sumInsured", "NEQ", "5000000", new BigDecimal("5000000.01"))).isTrue();
    }

    @Test
    @DisplayName("U-5: IN holds when the value equals any listed operand")
    void inMatchesAnyListedValue() {
        Map<String, Object> facts = Map.of("floodZone", "HIGH");

        assertThat(evaluator.holds(new Condition("floodZone", "IN", null, List.of("MEDIUM", "HIGH")), facts)).isTrue();
        assertThat(evaluator.holds(new Condition("floodZone", "IN", null, List.of("LOW", "MEDIUM")), facts)).isFalse();
        assertThat(evaluator.holds(new Condition("age", "IN", null, List.of("30", "40")), Map.of("age", 40))).isTrue();
    }

    @Test
    @DisplayName("U-5: NOT_EMPTY holds for a non-empty list, map or non-blank text, and IS_TRUE only for true")
    void presenceAndTruthOperators() {
        assertThat(holds("healthDeclarations", "NOT_EMPTY", null, List.of("DIABETES"))).isTrue();
        assertThat(holds("healthDeclarations", "NOT_EMPTY", null, List.of())).isFalse();
        assertThat(holds("healthDeclarations", "NOT_EMPTY", null, Map.of("a", "b"))).isTrue();
        assertThat(holds("healthDeclarations", "NOT_EMPTY", null, Map.of())).isFalse();
        assertThat(holds("note", "NOT_EMPTY", null, "text")).isTrue();
        assertThat(holds("note", "NOT_EMPTY", null, "  ")).isFalse();
        assertThat(holds("smoker", "IS_TRUE", null, true)).isTrue();
        assertThat(holds("smoker", "IS_TRUE", null, false)).isFalse();
        assertThat(holds("smoker", "IS_TRUE", null, "true")).isTrue();
    }

    @ParameterizedTest(name = "U-6: {0} on an absent field raises a domain exception")
    @EnumSource(ConditionOperator.class)
    @DisplayName("U-6: every operator raises a domain exception, and never reads as false, when the field is absent")
    void absentFieldRaisesForEveryOperator(ConditionOperator op) {
        Condition condition = new Condition("missing", op.name(), "1", List.of("1"));

        assertThatThrownBy(() -> evaluator.holds(condition, Map.of("other", 1)))
                .isInstanceOf(UnderwritingEvaluationException.class)
                .hasMessageContaining("missing");
        Map<String, Object> nullValued = new HashMap<>();
        nullValued.put("missing", null);
        assertThatThrownBy(() -> evaluator.holds(condition, nullValued))
                .isInstanceOf(UnderwritingEvaluationException.class);
    }

    @Test
    @DisplayName("U-6: values that cannot be compared raise a domain exception that never repeats the value")
    void incomparableValuesRaise() {
        String secret = "ABCDE1234F";

        assertThatThrownBy(() -> holds("pan", "GT", "60", secret))
                .isInstanceOf(UnderwritingEvaluationException.class)
                .hasMessageContaining("pan").hasMessageNotContaining(secret);
        assertThatThrownBy(() -> holds("smoker", "GT", "60", true))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("age", "GT", "sixty", 61))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("age", "GT", null, 61))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("bmi", "GT", "35", 36.5d))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("bmi", "GT", "35", 36.5f))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("smoker", "IS_TRUE", null, 1))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("age", "NOT_EMPTY", null, 5))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("smoker", "EQ", "yes", true))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("age", "EQ", "x", List.of(1)))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> evaluator.holds(new Condition("age", "IN", null, List.of()), Map.of("age", 1)))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("age", "BETWEEN", "1", 5))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> holds("age", "GT", "1E999999999", 5))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> evaluator.holds(Condition.of("age", "GT", "1"), null))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThatThrownBy(() -> evaluator.holds(Condition.of(null, "GT", "1"), Map.of("age", 1)))
                .isInstanceOf(UnderwritingEvaluationException.class);
        assertThat(Arrays.asList(ConditionOperator.values())).hasSize(9);
    }
}
