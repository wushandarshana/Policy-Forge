package com.horizon.policyforge.domain.ruleset;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Checks a rule set before it is stored or used (NFR-01, NFR-05). It never stops at the first problem: it returns every
 * violation it finds, so a product manager can fix a draft in one pass. An empty result means the rule set is usable.
 *
 * <p>Stateless and deterministic. Messages name the problem but never repeat a submitted value.
 */
public final class RuleSetValidator {

    private static final Pattern PRODUCT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,49}");
    private static final Set<String> OUTCOMES = Set.of("DECLINE", "REFER");
    private static final int MONEY_SCALE = 2;

    public List<RuleSetViolation> validate(RuleSetVersion ruleSet) {
        List<RuleSetViolation> out = new ArrayList<>();
        identity(ruleSet, out);

        Optional<RatingModel> model = RatingModel.parse(ruleSet.ratingModel());
        if (isBlank(ruleSet.ratingModel())) {
            out.add(new RuleSetViolation("ratingModel", "is required"));
        } else if (model.isEmpty()) {
            out.add(new RuleSetViolation("ratingModel", "is not a known rating model"));
        }

        sumInsured(ruleSet.sumInsured(), out);
        if (ruleSet.rating() == null) {
            out.add(new RuleSetViolation("rating", "section is required"));
        } else {
            rating(ruleSet.rating(), model, out);
        }
        underwriting(ruleSet.underwritingRules(), model, out);
        lifecycle(ruleSet.lifecycle(), out);
        return out;
    }

    private static void identity(RuleSetVersion ruleSet, List<RuleSetViolation> out) {
        if (isBlank(ruleSet.productCode())) {
            out.add(new RuleSetViolation("productCode", "is required"));
        } else if (!PRODUCT_CODE.matcher(ruleSet.productCode()).matches()) {
            out.add(new RuleSetViolation("productCode",
                    "must be upper-case letters, digits and underscores, starting with a letter"));
        }
        if (isBlank(ruleSet.productName())) {
            out.add(new RuleSetViolation("productName", "is required"));
        }
        if (ruleSet.version() < 1) {
            out.add(new RuleSetViolation("version", "must be 1 or greater"));
        }
        if (ruleSet.effectiveFrom() == null) {
            out.add(new RuleSetViolation("effectiveFrom", "is required and must be an ISO date"));
        }
        if (isBlank(ruleSet.currency())) {
            out.add(new RuleSetViolation("currency", "is required"));
        }
    }

    private static void sumInsured(SumInsuredLimits limits, List<RuleSetViolation> out) {
        if (limits == null) {
            out.add(new RuleSetViolation("sumInsured", "section is required"));
            return;
        }
        checkPositiveMoney("sumInsured.min", limits.min(), out);
        checkPositiveMoney("sumInsured.max", limits.max(), out);
        if (limits.min() != null && limits.max() != null && limits.min().compareTo(limits.max()) >= 0) {
            out.add(new RuleSetViolation("sumInsured", "min must be less than max"));
        }
    }

    private static void checkPositiveMoney(String path, BigDecimal value, List<RuleSetViolation> out) {
        if (value == null) {
            out.add(new RuleSetViolation(path, "is required"));
        } else if (value.signum() <= 0) {
            out.add(new RuleSetViolation(path, "must be greater than zero"));
        } else if (hasMoreThanTwoDecimals(value)) {
            out.add(new RuleSetViolation(path, "must have at most 2 decimal places"));
        }
    }

    private static void rating(RatingFactors rating, Optional<RatingModel> model, List<RuleSetViolation> out) {
        rating.scalars().forEach((name, value) -> {
            String path = "rating." + name;
            if (value == null) {
                out.add(new RuleSetViolation(path, "is required"));
            } else if (value.signum() < 0) {
                out.add(new RuleSetViolation(path, "must not be negative"));
            } else if ("policyFee".equals(name) && hasMoreThanTwoDecimals(value)) {
                out.add(new RuleSetViolation(path, "must have at most 2 decimal places"));
            }
        });
        rating.bands().forEach((name, rows) -> bands("rating." + name, rows, out));
        rating.lookups().forEach((name, entries) -> entries.forEach((key, value) -> {
            if (value == null) {
                out.add(new RuleSetViolation("rating." + name + "." + key, "is required"));
            } else if (value.signum() < 0) {
                out.add(new RuleSetViolation("rating." + name + "." + key, "must not be negative"));
            }
        }));

        model.ifPresent(m -> requireShape(rating, m, out));
    }

    private static void requireShape(RatingFactors rating, RatingModel model, List<RuleSetViolation> out) {
        for (String name : new TreeSet<>(model.requiredScalars())) {
            if (!rating.scalars().containsKey(name)) {
                out.add(new RuleSetViolation("rating." + name, "is required"));
            }
        }
        for (String name : new TreeSet<>(model.requiredBands())) {
            if (!rating.bands().containsKey(name)) {
                out.add(new RuleSetViolation("rating." + name, "band table is required"));
            }
        }
        for (Map.Entry<String, Set<String>> required : model.requiredLookups().entrySet()) {
            Map<String, BigDecimal> table = rating.lookups().get(required.getKey());
            String path = "rating." + required.getKey();
            if (table == null) {
                out.add(new RuleSetViolation(path, "table is required"));
                continue;
            }
            Set<String> missing = new TreeSet<>(required.getValue());
            missing.removeAll(table.keySet());
            if (!missing.isEmpty()) {
                out.add(new RuleSetViolation(path, "must contain the keys " + missing));
            }
        }
    }

    private static void bands(String path, List<Band> rows, List<RuleSetViolation> out) {
        if (rows.isEmpty()) {
            out.add(new RuleSetViolation(path, "must have at least one band"));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Band band = rows.get(i);
            String rowPath = path + "[" + i + "]";
            if (band.min() < 0) {
                out.add(new RuleSetViolation(rowPath, "band edges must not be negative"));
            }
            if (band.min() > band.max()) {
                out.add(new RuleSetViolation(rowPath, "min must not be greater than max"));
            }
            if (i > 0 && band.min() != rows.get(i - 1).max() + 1) {
                out.add(new RuleSetViolation(rowPath,
                        "must start one above the previous band's max (sorted, contiguous, no overlap)"));
            }
            if (band.factor() == null) {
                out.add(new RuleSetViolation(rowPath + ".factor", "is required"));
            } else if (band.factor().signum() < 0) {
                out.add(new RuleSetViolation(rowPath + ".factor", "must not be negative"));
            }
        }
    }

    private static void underwriting(List<UnderwritingRule> rules, Optional<RatingModel> model,
            List<RuleSetViolation> out) {
        if (rules == null) {
            out.add(new RuleSetViolation("underwriting.rules", "is required"));
            return;
        }
        Set<String> seenCodes = new HashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            UnderwritingRule rule = rules.get(i);
            String path = "underwriting.rules[" + i + "]";
            if (isBlank(rule.code())) {
                out.add(new RuleSetViolation(path + ".code", "is required"));
            } else {
                if (model.isPresent() && !rule.code().startsWith(model.get().underwritingPrefix())) {
                    out.add(new RuleSetViolation(path + ".code",
                            "must start with " + model.get().underwritingPrefix()));
                }
                if (!seenCodes.add(rule.code())) {
                    out.add(new RuleSetViolation(path + ".code", "duplicates the code of an earlier rule"));
                }
            }
            if (!OUTCOMES.contains(rule.outcome())) {
                out.add(new RuleSetViolation(path + ".outcome", "must be DECLINE or REFER"));
            }
            if (isBlank(rule.description())) {
                out.add(new RuleSetViolation(path + ".description", "is required"));
            }
            if (rule.all().isEmpty()) {
                out.add(new RuleSetViolation(path + ".all", "must have at least one condition"));
            }
            for (int j = 0; j < rule.all().size(); j++) {
                condition(path + ".all[" + j + "]", rule.all().get(j), model, out);
            }
        }
    }

    private static void condition(String path, Condition condition, Optional<RatingModel> model,
            List<RuleSetViolation> out) {
        if (isBlank(condition.field())) {
            out.add(new RuleSetViolation(path + ".field", "is required"));
        } else if (model.isPresent() && !model.get().underwritingFields().contains(condition.field())) {
            out.add(new RuleSetViolation(path + ".field", "is not a known input of this rating model"));
        }

        if (isBlank(condition.op())) {
            out.add(new RuleSetViolation(path + ".op", "is required"));
            return;
        }
        Optional<ConditionOperator> op = ConditionOperator.parse(condition.op());
        if (op.isEmpty()) {
            out.add(new RuleSetViolation(path + ".op", "is not a known operator"));
            return;
        }
        switch (op.get().operand()) {
            case SCALAR -> {
                if (isBlank(condition.value())) {
                    out.add(new RuleSetViolation(path + ".value", "is required for this operator"));
                }
            }
            case NUMBER -> {
                if (isBlank(condition.value())) {
                    out.add(new RuleSetViolation(path + ".value", "is required for this operator"));
                } else if (!isDecimal(condition.value())) {
                    out.add(new RuleSetViolation(path + ".value", "must be a decimal number"));
                }
            }
            case LIST -> {
                if (condition.values().isEmpty()) {
                    out.add(new RuleSetViolation(path + ".values", "must list at least one value"));
                }
            }
            case NONE -> {
                // NOT_EMPTY and IS_TRUE take no operand.
            }
        }
    }

    private static void lifecycle(Lifecycle lifecycle, List<RuleSetViolation> out) {
        if (lifecycle == null) {
            out.add(new RuleSetViolation("lifecycle", "section is required"));
            return;
        }
        count("lifecycle.termMonths", lifecycle.termMonths(), 1, out);
        count("lifecycle.gracePeriodDays", lifecycle.gracePeriodDays(), 0, out);
        count("lifecycle.coolingOffDays", lifecycle.coolingOffDays(), 0, out);
        count("lifecycle.renewalOfferDaysBeforeExpiry", lifecycle.renewalOfferDaysBeforeExpiry(), 0, out);
        count("lifecycle.quoteValidityDays", lifecycle.quoteValidityDays(), 0, out);
        BigDecimal fee = lifecycle.cancellationFee();
        if (fee == null) {
            out.add(new RuleSetViolation("lifecycle.cancellationFee", "is required"));
        } else if (fee.signum() < 0) {
            out.add(new RuleSetViolation("lifecycle.cancellationFee", "must not be negative"));
        } else if (hasMoreThanTwoDecimals(fee)) {
            out.add(new RuleSetViolation("lifecycle.cancellationFee", "must have at most 2 decimal places"));
        }
    }

    private static void count(String path, Integer value, int minimum, List<RuleSetViolation> out) {
        if (value == null) {
            out.add(new RuleSetViolation(path, "is required"));
        } else if (value < minimum) {
            out.add(new RuleSetViolation(path, "must be " + minimum + " or greater"));
        }
    }

    private static boolean hasMoreThanTwoDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() > MONEY_SCALE;
    }

    private static boolean isDecimal(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
