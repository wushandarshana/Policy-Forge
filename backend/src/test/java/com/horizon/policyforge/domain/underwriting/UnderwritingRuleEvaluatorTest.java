package com.horizon.policyforge.domain.underwriting;

import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.household;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.householdFacts;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.motor;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.motorFacts;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.termLife;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.termLifeFacts;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.ruleset.Condition;
import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import com.horizon.policyforge.domain.ruleset.UnderwritingRule;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-04.1 to AC-04.5 against the shipped v1 rule files and the TERM_LIFE v2 fixture, plus the evaluator contract. */
@Tag("AC-04")
class UnderwritingRuleEvaluatorTest {

    private final UnderwritingRuleEvaluator evaluator = new UnderwritingRuleEvaluator();

    @Test
    @Tag("AC-04.1")
    @DisplayName("AC-04.1: TERM_LIFE age 35, non-smoker, BMI 24, no declarations, SI 5,000,000.00 is AUTO_BIND with UW-OK")
    void cleanTermLifeIsAutoBind() {
        UnderwritingResult result = evaluator.evaluate(UnderwritingFixtures.termLifeV1(), termLifeFacts(termLife()));

        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.AUTO_BIND);
        assertThat(result.reasonCodes()).containsExactly("UW-OK");
        assertThat(result.reasons()).containsExactly(new ReasonCode("UW-OK", "No underwriting rule fired"));
        assertThat(result.ruleVersion()).isEqualTo(1);
    }

    @Test
    @Tag("AC-04.2")
    @DisplayName("AC-04.2: TERM_LIFE smoker with SI 12,000,000.00 is MANUAL_REVIEW with UW-TL-002")
    void smokerWithLargeSumInsuredIsReferred() {
        Map<String, Object> risk = with(termLife(), "smoker", true, "sumInsured", "12000000.00");

        UnderwritingResult result = evaluator.evaluate(UnderwritingFixtures.termLifeV1(), termLifeFacts(risk));

        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.MANUAL_REVIEW);
        assertThat(result.reasonCodes()).containsExactly("UW-TL-002");
        assertThat(result.reasons().get(0).description()).isEqualTo("Smoker with sum insured above 10,000,000");
    }

    @Test
    @Tag("AC-04.3")
    @DisplayName("AC-04.3: TERM_LIFE age 62 and BMI 37 is DECLINE with UW-TL-001 and UW-TL-004, decline beating refer")
    void declineBeatsReferAndAllFiredCodesAreListed() {
        Map<String, Object> risk = with(termLife(), "age", 62, "bmi", "37");

        UnderwritingResult result = evaluator.evaluate(UnderwritingFixtures.termLifeV1(), termLifeFacts(risk));

        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.DECLINE);
        assertThat(result.reasonCodes()).containsExactly("UW-TL-001", "UW-TL-004");
        assertThat(result.reasons()).extracting(ReasonCode::description)
                .containsExactly("Entry age above 60", "BMI above 35");
    }

    @Test
    @Tag("AC-04.4")
    @DisplayName("AC-04.4: MOTOR with 3 claims is DECLINE with UW-MO-001")
    void motorWithThreeClaimsIsDeclined() {
        Map<String, Object> risk = with(motor(), "claimsLast3Years", 3);

        UnderwritingResult result = evaluator.evaluate(UnderwritingFixtures.motorV1(), motorFacts(risk));

        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.DECLINE);
        assertThat(result.reasonCodes()).containsExactly("UW-MO-001");
    }

    @Test
    @Tag("AC-04.4")
    @DisplayName("AC-04.4: HOUSEHOLD with TIMBER construction is MANUAL_REVIEW with UW-HH-003")
    void timberHouseholdIsReferred() {
        Map<String, Object> risk = with(household(), "constructionType", "TIMBER");

        UnderwritingResult result = evaluator.evaluate(UnderwritingFixtures.householdV1(), householdFacts(risk));

        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.MANUAL_REVIEW);
        assertThat(result.reasonCodes()).containsExactly("UW-HH-003");
    }

    @Test
    @Tag("AC-04.5")
    @DisplayName("AC-04.5: with a TERM_LIFE v2 whose UW-TL-001 threshold is 65, age 62 is not DECLINE and the result references version 2")
    void ageSixtyTwoIsNotDeclinedUnderVersionTwo() {
        Map<String, Object> risk = with(termLife(), "age", 62);
        RuleSetVersion v2 = UnderwritingFixtures.termLifeV2AgeThreshold65();

        UnderwritingResult result = evaluator.evaluate(v2, termLifeFacts(risk));

        assertThat(result.outcome()).isNotEqualTo(UnderwritingOutcome.DECLINE);
        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.AUTO_BIND);
        assertThat(result.reasonCodes()).containsExactly("UW-OK");
        assertThat(result.ruleVersion()).isEqualTo(2);
        // The same risk under the shipped v1 is still a decline, so the version is what changed the decision.
        assertThat(evaluator.evaluate(UnderwritingFixtures.termLifeV1(), termLifeFacts(risk)).outcome())
                .isEqualTo(UnderwritingOutcome.DECLINE);
    }

    @Test
    @Tag("AC-04.6")
    @DisplayName("AC-04.6: reasons come back sorted by code whatever the order of the rules in the rule set")
    void reasonsAreSortedByCode() {
        RuleSetVersion v1 = UnderwritingFixtures.termLifeV1();
        RuleSetVersion shuffled = withRules(v1, v1.underwritingRules().reversed());
        Map<String, Object> risk = with(termLife(), "age", 62, "bmi", "46", "healthDeclarations", List.of("DIABETES"));

        UnderwritingResult result = evaluator.evaluate(shuffled, termLifeFacts(risk));

        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.DECLINE);
        assertThat(result.reasonCodes()).containsExactly("UW-TL-001", "UW-TL-003", "UW-TL-004", "UW-TL-005");
    }

    @Test
    @DisplayName("U-5: the derived sum insured and age are added to a copy of the risk inputs")
    void derivedFactsAreAddedToACopy() {
        Map<String, Object> risk = motor();

        Map<String, Object> facts = UnderwritingRuleEvaluator.withDerived(risk, new BigDecimal("800000.00"), 30);

        assertThat(facts).containsEntry("sumInsured", new BigDecimal("800000.00")).containsEntry("age", 30)
                .containsEntry("vehicleValue", "800000.00");
        assertThat(risk).doesNotContainKeys("sumInsured", "age");
    }

    @Test
    @DisplayName("U-6: a rule that names an absent field makes the whole evaluation fail instead of being skipped")
    void absentFieldFailsTheEvaluation() {
        Map<String, Object> risk = termLife();
        risk.remove("bmi");

        assertThatThrownBy(() -> evaluator.evaluate(UnderwritingFixtures.termLifeV1(), termLifeFacts(risk)))
                .isInstanceOf(UnderwritingEvaluationException.class)
                .hasMessageContaining("UW-TL-004")
                .hasMessageContaining("bmi");
    }

    @Test
    @DisplayName("U-6: every condition of a rule is evaluated, so an absent field raises even when an earlier condition is false")
    void laterConditionsAreEvaluatedToo() {
        // UW-HH-002 reads floodZone first and hasFloodDefences second; floodZone is LOW so the first condition is false.
        Map<String, Object> risk = household();
        risk.remove("hasFloodDefences");

        assertThatThrownBy(() -> evaluator.evaluate(UnderwritingFixtures.householdV1(), householdFacts(risk)))
                .isInstanceOf(UnderwritingEvaluationException.class)
                .hasMessageContaining("UW-HH-002")
                .hasMessageContaining("hasFloodDefences");
    }

    @Test
    @DisplayName("U-6: an unknown rule outcome, a rule without conditions and a missing rules section fail closed")
    void malformedRulesFailClosed() {
        RuleSetVersion v1 = UnderwritingFixtures.termLifeV1();
        Map<String, Object> facts = termLifeFacts(termLife());
        Condition ageAboveZero = Condition.of("age", "GT", "0");

        assertThatThrownBy(() -> evaluator.evaluate(withRules(v1, List.of(
                new UnderwritingRule("UW-TL-009", "WARN", "x", List.of(ageAboveZero)))), facts))
                .isInstanceOf(UnderwritingEvaluationException.class).hasMessageContaining("UW-TL-009");
        assertThatThrownBy(() -> evaluator.evaluate(withRules(v1, List.of(
                new UnderwritingRule("UW-TL-009", "REFER", "x", List.of()))), facts))
                .isInstanceOf(UnderwritingEvaluationException.class).hasMessageContaining("UW-TL-009");
        assertThatThrownBy(() -> evaluator.evaluate(withRules(v1, null), facts))
                .isInstanceOf(UnderwritingEvaluationException.class);
    }

    @Test
    @DisplayName("U-8: a rule set with no rules is AUTO_BIND with UW-OK and carries its version")
    void noRulesIsAutoBind() {
        RuleSetVersion v1 = UnderwritingFixtures.termLifeV1();

        UnderwritingResult result = evaluator.evaluate(withRules(v1, List.of()), termLifeFacts(termLife()));

        assertThat(result.outcome()).isEqualTo(UnderwritingOutcome.AUTO_BIND);
        assertThat(result.reasonCodes()).containsExactly("UW-OK");
        assertThat(result.ruleVersion()).isEqualTo(v1.version());
    }

    private static RuleSetVersion withRules(RuleSetVersion base, List<UnderwritingRule> rules) {
        return new RuleSetVersion(base.productCode(), base.productName(), base.version(), base.effectiveFrom(),
                base.ratingModel(), base.currency(), base.sumInsured(), base.rating(), rules, base.lifecycle());
    }
}
