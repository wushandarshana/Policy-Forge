package com.horizon.policyforge.domain.ruleset;

import static com.horizon.policyforge.domain.ruleset.RuleSets.d;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-02.5 at the domain level: the validator reports every violation at once. */
@Tag("AC-02")
class RuleSetValidatorTest {

    private final RuleSetValidator validator = new RuleSetValidator();

    private List<String> paths(RuleSetVersion ruleSet) {
        return validator.validate(ruleSet).stream().map(RuleSetViolation::path).toList();
    }

    @Test
    @DisplayName("AC-02.5: a well-formed rule set has no violations")
    void validRuleSetHasNoViolations() {
        assertThat(validator.validate(RuleSets.validMotor())).isEmpty();
    }

    @Test
    @DisplayName("AC-02.5: min greater than max, an unknown operator and duplicate rule codes are all reported in one pass")
    void reportsEveryViolationAtOnce() {
        RuleSetVersion base = RuleSets.validMotor();
        List<UnderwritingRule> rules = List.of(
                new UnderwritingRule("UW-MO-001", "DECLINE", "first",
                        List.of(Condition.of("claimsLast3Years", "BETWEEN", "3"))),
                new UnderwritingRule("UW-MO-001", "REFER", "same code again",
                        List.of(Condition.of("driverAge", "LT", "21"))));
        RuleSetVersion draft = RuleSets.withRules(
                RuleSets.withSumInsured(base, new SumInsuredLimits(d("20000000.00"), d("10000000.00"))), rules);

        List<RuleSetViolation> violations = validator.validate(draft);

        assertThat(violations).extracting(RuleSetViolation::path)
                .containsExactlyInAnyOrder("sumInsured", "underwriting.rules[0].all[0].op",
                        "underwriting.rules[1].code");
        assertThat(violations).allSatisfy(v -> assertThat(v.message()).isNotBlank());
    }

    @Test
    @DisplayName("AC-02.5: missing required sections are each reported")
    void reportsMissingSections() {
        assertThat(paths(RuleSets.withoutSections(RuleSets.validMotor())))
                .containsExactlyInAnyOrder("sumInsured", "rating", "underwriting.rules", "lifecycle");
    }

    @Test
    @DisplayName("AC-02.5: negative decimals are reported on rating factors, sum insured and the cancellation fee")
    void reportsNegativeDecimals() {
        RatingFactors rating = new RatingFactors(
                Map.of("baseRate", d("-0.025"), "claimLoading", d("0.20"), "policyFee", d("150.00")),
                RuleSets.motorRating().bands(), RuleSets.motorRating().lookups());
        RuleSetVersion draft = RuleSets.withLifecycle(
                RuleSets.withSumInsured(RuleSets.withRating(RuleSets.validMotor(), rating),
                        new SumInsuredLimits(d("-1.00"), d("10000000.00"))),
                new Lifecycle(12, 15, 7, d("-250.00"), 30, 30));

        assertThat(paths(draft)).contains("rating.baseRate", "sumInsured.min", "lifecycle.cancellationFee");
    }

    @Test
    @DisplayName("AC-02.5: an unknown rating model is reported")
    void reportsUnknownRatingModel() {
        assertThat(paths(RuleSets.withModel(RuleSets.validMotor(), "PET"))).contains("ratingModel");
    }

    @Test
    @DisplayName("AC-02.5: band tables must be sorted, contiguous, inclusive and non-overlapping")
    void reportsBadBands() {
        Map<String, List<Band>> bands = Map.of(
                "vehicleAgeFactor", List.of(new Band(0, 3, d("1.00")), new Band(5, 7, d("1.10"))),
                "driverAgeFactor", List.of(new Band(18, 30, d("1.30")), new Band(25, 65, d("1.00")),
                        new Band(70, 66, d("1.15"))));
        RatingFactors rating = new RatingFactors(RuleSets.motorRating().scalars(), bands,
                RuleSets.motorRating().lookups());

        List<String> paths = paths(RuleSets.withRating(RuleSets.validMotor(), rating));

        assertThat(paths).contains("rating.vehicleAgeFactor[1]", "rating.driverAgeFactor[1]",
                "rating.driverAgeFactor[2]");
    }

    @Test
    @DisplayName("AC-02.5: required factor tables and keys are reported when absent")
    void reportsMissingFactors() {
        RatingFactors rating = new RatingFactors(
                Map.of("baseRate", d("0.025")),
                Map.of("vehicleAgeFactor", RuleSets.motorRating().bandTable("vehicleAgeFactor")),
                Map.of("useFactor", Map.of("PRIVATE", d("1.00"))));

        assertThat(paths(RuleSets.withRating(RuleSets.validMotor(), rating)))
                .contains("rating.claimLoading", "rating.policyFee", "rating.driverAgeFactor", "rating.ncbDiscount",
                        "rating.useFactor");
    }

    @Test
    @DisplayName("AC-02.5: underwriting rules need a product-prefixed code, a known outcome, a description and valid conditions")
    void reportsBadUnderwritingRules() {
        List<UnderwritingRule> rules = List.of(
                new UnderwritingRule("UW-TL-001", "DECLINE", "wrong prefix",
                        List.of(Condition.of("driverAge", "LT", "21"))),
                new UnderwritingRule("UW-MO-002", "WARN", "", List.of()),
                new UnderwritingRule("UW-MO-003", "REFER", "bad conditions", List.of(
                        Condition.of("colour", "EQ", "RED"),
                        Condition.of("driverAge", "GT"),
                        Condition.of("driverAge", "GT", "abc"),
                        Condition.of("vehicleUse", "IN"),
                        Condition.of("vehicleUse", "EQ"))));

        List<String> paths = paths(RuleSets.withRules(RuleSets.validMotor(), rules));

        assertThat(paths).contains("underwriting.rules[0].code", "underwriting.rules[1].outcome",
                "underwriting.rules[1].description", "underwriting.rules[1].all",
                "underwriting.rules[2].all[0].field", "underwriting.rules[2].all[1].value",
                "underwriting.rules[2].all[2].value", "underwriting.rules[2].all[3].values",
                "underwriting.rules[2].all[4].value");
    }

    @Test
    @DisplayName("AC-02.5: money with more than two decimal places and negative lifecycle days are reported")
    void reportsMoneyScaleAndLifecycleDays() {
        RatingFactors rating = new RatingFactors(
                Map.of("baseRate", d("0.025"), "claimLoading", d("0.20"), "policyFee", d("150.005")),
                RuleSets.motorRating().bands(), RuleSets.motorRating().lookups());
        RuleSetVersion draft = RuleSets.withLifecycle(
                RuleSets.withSumInsured(RuleSets.withRating(RuleSets.validMotor(), rating),
                        new SumInsuredLimits(d("100000.001"), d("10000000.00"))),
                new Lifecycle(0, -1, 7, d("250.00"), 30, 30));

        assertThat(paths(draft)).contains("rating.policyFee", "sumInsured.min", "lifecycle.termMonths",
                "lifecycle.gracePeriodDays");
    }

    @Test
    @DisplayName("AC-02.5: identity fields are checked: product code, name, version, effective date")
    void reportsIdentityProblems() {
        RuleSetVersion draft = new RuleSetVersion("motor", " ", 0, null, "MOTOR", "INR",
                RuleSets.validMotor().sumInsured(), RuleSets.motorRating(), RuleSets.motorRules(),
                RuleSets.lifecycle());

        List<String> paths = new ArrayList<>(paths(draft));

        assertThat(paths).contains("productCode", "productName", "version", "effectiveFrom");
    }
}
