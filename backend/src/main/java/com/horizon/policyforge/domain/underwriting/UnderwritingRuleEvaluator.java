package com.horizon.policyforge.domain.underwriting;

import com.horizon.policyforge.domain.ruleset.Condition;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.domain.ruleset.UnderwritingRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates every underwriting rule of the rule-set version it is given against the facts of a risk (AC-04). Stateless
 * and deterministic; no rule code, threshold or product is known here, everything comes from the rule set.
 *
 * <p>Precedence: any {@code DECLINE} rule gives {@link UnderwritingOutcome#DECLINE}, otherwise any {@code REFER} rule
 * gives {@link UnderwritingOutcome#MANUAL_REVIEW}, otherwise {@link UnderwritingOutcome#AUTO_BIND}. The result lists
 * every fired rule sorted by code, or {@code UW-OK} when none fired (U-8).
 *
 * <p>The evaluator fails closed (U-6). All conditions of a rule are evaluated, with no short-circuit, so a condition
 * that names an absent field raises even when an earlier condition of the same rule is false. A rule with an unknown
 * outcome or no conditions raises too.
 */
public final class UnderwritingRuleEvaluator {

    private static final String DECLINE = "DECLINE";
    private static final String REFER = "REFER";

    private final ConditionEvaluator conditions = new ConditionEvaluator();

    /**
     * Evaluates the rule set against {@code facts}: the risk inputs plus the derived fields (see
     * {@link #withDerived}).
     *
     * @throws UnderwritingEvaluationException when a rule cannot be evaluated
     */
    public UnderwritingResult evaluate(RuleSetVersion ruleSet, Map<String, Object> facts) {
        if (ruleSet == null || ruleSet.underwritingRules() == null) {
            throw new UnderwritingEvaluationException("The rule set has no underwriting rules section");
        }
        List<ReasonCode> fired = new ArrayList<>();
        boolean decline = false;
        boolean refer = false;
        for (UnderwritingRule rule : ruleSet.underwritingRules()) {
            boolean isDecline = DECLINE.equals(rule.outcome());
            if (!isDecline && !REFER.equals(rule.outcome())) {
                throw new UnderwritingEvaluationException("Rule " + rule.code() + " has an unknown outcome");
            }
            if (fires(rule, facts)) {
                fired.add(new ReasonCode(rule.code(), rule.description()));
                decline |= isDecline;
                refer |= !isDecline;
            }
        }
        if (fired.isEmpty()) {
            return new UnderwritingResult(UnderwritingOutcome.AUTO_BIND, List.of(ReasonCode.OK), ruleSet.version());
        }
        fired.sort(Comparator.comparing(ReasonCode::code));
        UnderwritingOutcome outcome = decline ? UnderwritingOutcome.DECLINE
                : refer ? UnderwritingOutcome.MANUAL_REVIEW : UnderwritingOutcome.AUTO_BIND;
        return new UnderwritingResult(outcome, fired, ruleSet.version());
    }

    /**
     * The facts to evaluate: a copy of the risk inputs plus the derived {@code sumInsured} (U-5) and, when given,
     * {@code age}. The sum insured is a {@link BigDecimal}; a {@code null} argument adds nothing.
     */
    public static Map<String, Object> withDerived(Map<String, Object> riskInputs, BigDecimal sumInsured, Integer age) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (riskInputs != null) {
            facts.putAll(riskInputs);
        }
        if (sumInsured != null) {
            facts.put("sumInsured", sumInsured);
        }
        if (age != null) {
            facts.put("age", age);
        }
        return facts;
    }

    private boolean fires(UnderwritingRule rule, Map<String, Object> facts) {
        if (rule.all().isEmpty()) {
            throw new UnderwritingEvaluationException("Rule " + rule.code() + " has no conditions");
        }
        boolean allHold = true;
        for (Condition condition : rule.all()) {
            try {
                allHold &= conditions.holds(condition, facts);
            } catch (UnderwritingEvaluationException e) {
                throw new UnderwritingEvaluationException(
                        "Rule " + rule.code() + " cannot be evaluated: " + e.getMessage());
            }
        }
        return allHold;
    }
}
