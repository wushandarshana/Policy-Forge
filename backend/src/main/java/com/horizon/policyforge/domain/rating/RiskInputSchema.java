package com.horizon.policyforge.domain.rating;

import com.horizon.policyforge.domain.ruleset.RatingModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The risk inputs a rating model declares (AC-01.9, NFR-04.2): the single source of truth for which keys a quote
 * request may carry and which of them are health data. Anything else is rejected at quote time, and the public view
 * of stored inputs is built from this allow-list (declared minus health), never by removing keys.
 *
 * @param declared every accepted input key, in display order
 * @param health   the declared keys that are health data and never shown publicly
 */
public record RiskInputSchema(List<String> declared, Set<String> health) {

    static final String UNKNOWN_INPUT_MESSAGE = "is not an accepted input";

    public RiskInputSchema {
        declared = List.copyOf(declared);
        health = Set.copyOf(health);
        if (!declared.containsAll(health)) {
            throw new IllegalArgumentException("health inputs must be declared inputs");
        }
    }

    /** The schema of the rating model a rule set names. */
    public static RiskInputSchema forModel(RatingModel model) {
        return RatingAlgorithms.forModel(model).schema();
    }

    /** One violation per submitted key that is not declared, in key order. The field is the key itself. */
    public List<RiskInputViolation> unknownKeys(Map<String, ?> riskInputs) {
        List<RiskInputViolation> violations = new ArrayList<>();
        if (riskInputs == null) {
            return violations;
        }
        for (String key : new TreeSet<>(riskInputs.keySet())) {
            if (!declared.contains(key)) {
                violations.add(new RiskInputViolation(key, UNKNOWN_INPUT_MESSAGE));
            }
        }
        return violations;
    }

    /** Only the declared, non-health inputs that are present, in declared order. Undeclared keys never appear. */
    public Map<String, Object> publicView(Map<String, ?> riskInputs) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (riskInputs == null) {
            return view;
        }
        for (String key : declared) {
            if (!health.contains(key) && riskInputs.get(key) != null) {
                view.put(key, riskInputs.get(key));
            }
        }
        return view;
    }
}
