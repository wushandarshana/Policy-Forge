package com.horizon.policyforge.domain.ruleset;

import java.util.Optional;

/** Operators an underwriting condition may use. A rule fires when all of its conditions hold. */
public enum ConditionOperator {

    EQ(Operand.SCALAR),
    NEQ(Operand.SCALAR),
    GT(Operand.NUMBER),
    GTE(Operand.NUMBER),
    LT(Operand.NUMBER),
    LTE(Operand.NUMBER),
    IN(Operand.LIST),
    NOT_EMPTY(Operand.NONE),
    IS_TRUE(Operand.NONE);

    /** What kind of comparison value the operator needs. */
    public enum Operand {
        NONE, SCALAR, NUMBER, LIST
    }

    private final Operand operand;

    ConditionOperator(Operand operand) {
        this.operand = operand;
    }

    public Operand operand() {
        return operand;
    }

    /** Parses an operator exactly as written in a rule file; empty when unknown. */
    public static Optional<ConditionOperator> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (ConditionOperator op : values()) {
            if (op.name().equals(name)) {
                return Optional.of(op);
            }
        }
        return Optional.empty();
    }
}
