package com.horizon.policyforge.domain.ruleset;

import java.util.List;

/**
 * One condition of an underwriting rule. The operator is kept as written so that the validator can report an unknown
 * one. {@code value} carries the comparison operand of a scalar or numeric operator and {@code values} the operand
 * list of {@code IN}. Operands are strings, so numeric comparison parses them as {@code BigDecimal}.
 */
public record Condition(String field, String op, String value, List<String> values) {

    public Condition {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static Condition of(String field, String op) {
        return new Condition(field, op, null, List.of());
    }

    public static Condition of(String field, String op, String value) {
        return new Condition(field, op, value, List.of());
    }
}
