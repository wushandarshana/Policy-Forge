package com.horizon.policyforge.domain.underwriting;

import com.horizon.policyforge.domain.ruleset.Condition;
import com.horizon.policyforge.domain.ruleset.ConditionOperator;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates one underwriting condition against the facts of a risk. Stateless and deterministic.
 *
 * <p>Numbers are compared as {@link BigDecimal} built from integers or from decimal text, never from a binary
 * floating-point value, so a threshold such as {@code 10000000} is exact (NFR-01). The evaluator fails closed (U-6): a
 * missing field, a missing operand, an unknown operator or two values that cannot be compared raise
 * {@link UnderwritingEvaluationException} and never read as "the condition does not hold". Messages name the field,
 * never the value.
 */
public final class ConditionEvaluator {

    /** Plain decimal text with bounded length, so a hostile exponent can never reach {@link BigDecimal}. */
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?\\d{1,30}(\\.\\d{1,15})?");

    /** Whether the condition holds for these facts. */
    public boolean holds(Condition condition, Map<String, Object> facts) {
        if (condition == null || condition.field() == null || condition.field().isBlank()) {
            throw new UnderwritingEvaluationException("A condition does not name a field");
        }
        String field = condition.field();
        ConditionOperator op = ConditionOperator.parse(condition.op()).orElseThrow(
                () -> new UnderwritingEvaluationException("The condition on field " + field + " has an unknown operator"));
        Object fact = facts == null ? null : facts.get(field);
        if (fact == null) {
            throw new UnderwritingEvaluationException("Field " + field + " is absent");
        }
        return switch (op) {
            case EQ -> equalsOperand(field, fact, operand(field, condition));
            case NEQ -> !equalsOperand(field, fact, operand(field, condition));
            case GT -> compare(field, fact, operand(field, condition)) > 0;
            case GTE -> compare(field, fact, operand(field, condition)) >= 0;
            case LT -> compare(field, fact, operand(field, condition)) < 0;
            case LTE -> compare(field, fact, operand(field, condition)) <= 0;
            case IN -> in(field, fact, condition);
            case NOT_EMPTY -> notEmpty(field, fact);
            case IS_TRUE -> isTrue(field, fact);
        };
    }

    private static String operand(String field, Condition condition) {
        if (condition.value() == null) {
            throw new UnderwritingEvaluationException("The condition on field " + field + " has no comparison value");
        }
        return condition.value();
    }

    private static int compare(String field, Object fact, String operand) {
        return toDecimal(field, fact).compareTo(toDecimal(field, operand));
    }

    /** Booleans and text compare by text, numbers by value; anything else cannot be compared. */
    private static boolean equalsOperand(String field, Object fact, String operand) {
        if (fact instanceof Boolean flag) {
            if (!"true".equals(operand) && !"false".equals(operand)) {
                throw incomparable(field);
            }
            return flag.toString().equals(operand);
        }
        if (isIntegral(fact) || fact instanceof BigDecimal) {
            return compare(field, fact, operand) == 0;
        }
        if (fact instanceof CharSequence text) {
            String asText = text.toString();
            if (PLAIN_DECIMAL.matcher(asText).matches() && PLAIN_DECIMAL.matcher(operand).matches()) {
                return compare(field, asText, operand) == 0;
            }
            return asText.equals(operand);
        }
        throw incomparable(field);
    }

    /** Every listed operand is checked, so an incomparable one raises even after an earlier match. */
    private static boolean in(String field, Object fact, Condition condition) {
        if (condition.values().isEmpty()) {
            throw new UnderwritingEvaluationException("The condition on field " + field + " lists no values");
        }
        boolean found = false;
        for (String candidate : condition.values()) {
            if (candidate == null) {
                throw incomparable(field);
            }
            found |= equalsOperand(field, fact, candidate);
        }
        return found;
    }

    private static boolean notEmpty(String field, Object fact) {
        if (fact instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (fact instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (fact instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        throw incomparable(field);
    }

    private static boolean isTrue(String field, Object fact) {
        if (fact instanceof Boolean flag) {
            return flag;
        }
        if (fact instanceof CharSequence text) {
            String asText = text.toString();
            if ("true".equals(asText) || "false".equals(asText)) {
                return Boolean.parseBoolean(asText);
            }
        }
        throw incomparable(field);
    }

    private static boolean isIntegral(Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
                || value instanceof BigInteger;
    }

    /** Exact decimal of an integer, a {@link BigDecimal} or plain decimal text. Binary floating-point is refused. */
    private static BigDecimal toDecimal(String field, Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof CharSequence text && PLAIN_DECIMAL.matcher(text).matches()) {
            return new BigDecimal(text.toString());
        }
        throw incomparable(field);
    }

    private static UnderwritingEvaluationException incomparable(String field) {
        return new UnderwritingEvaluationException("Field " + field + " cannot be compared with the rule's value");
    }
}
