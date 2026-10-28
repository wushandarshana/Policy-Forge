package com.horizon.policyforge.domain.ruleset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Builders for domain-level tests: a valid MOTOR v1 rule set that tests then break one way at a time. */
final class RuleSets {

    private RuleSets() {
    }

    static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    static RatingFactors motorRating() {
        return new RatingFactors(
                Map.of("baseRate", d("0.025"), "claimLoading", d("0.20"), "policyFee", d("150.00")),
                Map.of("vehicleAgeFactor", List.of(
                                new Band(0, 3, d("1.00")), new Band(4, 7, d("1.10")), new Band(8, 15, d("1.25"))),
                        "driverAgeFactor", List.of(
                                new Band(18, 24, d("1.30")), new Band(25, 65, d("1.00")),
                                new Band(66, 99, d("1.15")))),
                Map.of("ncbDiscount", Map.of("0", d("0.00"), "1", d("0.20"), "2", d("0.25"), "3", d("0.35"),
                                "4", d("0.45"), "5", d("0.50")),
                        "useFactor", Map.of("PRIVATE", d("1.00"), "COMMERCIAL", d("1.40"))));
    }

    static List<UnderwritingRule> motorRules() {
        return List.of(
                new UnderwritingRule("UW-MO-001", "DECLINE", "Three or more claims",
                        List.of(Condition.of("claimsLast3Years", "GTE", "3"))),
                new UnderwritingRule("UW-MO-002", "DECLINE", "Vehicle older than 15 years",
                        List.of(Condition.of("vehicleAgeYears", "GT", "15"))));
    }

    static Lifecycle lifecycle() {
        return new Lifecycle(12, 15, 7, d("250.00"), 30, 30);
    }

    static RuleSetVersion validMotor() {
        return motor(1, "2026-01-01");
    }

    static RuleSetVersion motor(int version, String effectiveFrom) {
        return new RuleSetVersion("MOTOR", "Horizon Motor Cover", version, LocalDate.parse(effectiveFrom), "MOTOR",
                "INR", new SumInsuredLimits(d("100000.00"), d("10000000.00")), motorRating(), motorRules(),
                lifecycle());
    }

    static RuleSetVersion withSumInsured(RuleSetVersion base, SumInsuredLimits limits) {
        return new RuleSetVersion(base.productCode(), base.productName(), base.version(), base.effectiveFrom(),
                base.ratingModel(), base.currency(), limits, base.rating(), base.underwritingRules(),
                base.lifecycle());
    }

    static RuleSetVersion withRating(RuleSetVersion base, RatingFactors rating) {
        return new RuleSetVersion(base.productCode(), base.productName(), base.version(), base.effectiveFrom(),
                base.ratingModel(), base.currency(), base.sumInsured(), rating, base.underwritingRules(),
                base.lifecycle());
    }

    static RuleSetVersion withRules(RuleSetVersion base, List<UnderwritingRule> rules) {
        return new RuleSetVersion(base.productCode(), base.productName(), base.version(), base.effectiveFrom(),
                base.ratingModel(), base.currency(), base.sumInsured(), base.rating(), rules, base.lifecycle());
    }

    static RuleSetVersion withLifecycle(RuleSetVersion base, Lifecycle lifecycle) {
        return new RuleSetVersion(base.productCode(), base.productName(), base.version(), base.effectiveFrom(),
                base.ratingModel(), base.currency(), base.sumInsured(), base.rating(), base.underwritingRules(),
                lifecycle);
    }

    static RuleSetVersion withModel(RuleSetVersion base, String ratingModel) {
        return new RuleSetVersion(base.productCode(), base.productName(), base.version(), base.effectiveFrom(),
                ratingModel, base.currency(), base.sumInsured(), base.rating(), base.underwritingRules(),
                base.lifecycle());
    }

    static RuleSetVersion withoutSections(RuleSetVersion base) {
        return new RuleSetVersion(base.productCode(), base.productName(), base.version(), base.effectiveFrom(),
                base.ratingModel(), base.currency(), null, null, null, null);
    }
}
