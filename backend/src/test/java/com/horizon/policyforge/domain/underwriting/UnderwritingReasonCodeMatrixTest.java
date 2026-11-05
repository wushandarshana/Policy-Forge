package com.horizon.policyforge.domain.underwriting;

import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.household;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.motor;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.termLife;
import static com.horizon.policyforge.domain.underwriting.UnderwritingFixtures.with;
import static com.horizon.policyforge.domain.underwriting.UnderwritingOutcome.DECLINE;
import static com.horizon.policyforge.domain.underwriting.UnderwritingOutcome.MANUAL_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.horizon.policyforge.domain.ruleset.RuleSetVersion;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AC-04.6: the reason-code matrix, one row per v1 rule (13 rows). Each row triggers only its own rule where the rule
 * set allows it. UW-TL-005 cannot fire without UW-TL-004 (BMI above 45 is also above 35, KD-004), and UW-MO-002 is
 * defence in depth that a v1 quote never reaches (J8), so it is tested at domain level only.
 */
@Tag("AC-04")
@Tag("AC-04.6")
class UnderwritingReasonCodeMatrixTest {

    private static final RuleSetVersion TERM_LIFE = UnderwritingFixtures.termLifeV1();
    private static final RuleSetVersion MOTOR = UnderwritingFixtures.motorV1();
    private static final RuleSetVersion HOUSEHOLD = UnderwritingFixtures.householdV1();

    private final UnderwritingRuleEvaluator evaluator = new UnderwritingRuleEvaluator();

    @ParameterizedTest(name = "AC-04.6: {0} ({1}) gives {2} with {3}")
    @MethodSource("rows")
    void eachRuleFiresAsSpecified(String rule, String product, UnderwritingOutcome expected, List<String> codes,
            RuleSetVersion ruleSet, Map<String, Object> risk,
            Function<Map<String, Object>, Map<String, Object>> facts) {
        UnderwritingResult result = evaluator.evaluate(ruleSet, facts.apply(risk));

        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.reasonCodes()).containsExactlyElementsOf(codes);
        assertThat(result.reasonCodes()).contains(rule);
        assertThat(result.ruleVersion()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("AC-04.6: the matrix has one row for each of the 13 rules of the three v1 files")
    void matrixCoversEveryV1Rule() {
        List<String> shipped = Stream.of(TERM_LIFE, MOTOR, HOUSEHOLD)
                .flatMap(ruleSet -> ruleSet.underwritingRules().stream())
                .map(rule -> rule.code()).sorted().toList();
        List<String> rowRules = rows().map(row -> (String) row.get()[0]).sorted().toList();

        assertThat(shipped).hasSize(13);
        assertThat(rowRules).containsExactlyElementsOf(shipped);
    }

    static Stream<Arguments> rows() {
        Function<Map<String, Object>, Map<String, Object>> tl = UnderwritingFixtures::termLifeFacts;
        Function<Map<String, Object>, Map<String, Object>> mo = UnderwritingFixtures::motorFacts;
        Function<Map<String, Object>, Map<String, Object>> hh = UnderwritingFixtures::householdFacts;
        return Stream.of(
                arguments("UW-TL-001", "TERM_LIFE", DECLINE, List.of("UW-TL-001"), TERM_LIFE,
                        with(termLife(), "age", 62), tl),
                arguments("UW-TL-002", "TERM_LIFE", MANUAL_REVIEW, List.of("UW-TL-002"), TERM_LIFE,
                        with(termLife(), "smoker", true, "sumInsured", "12000000.00"), tl),
                arguments("UW-TL-003", "TERM_LIFE", MANUAL_REVIEW, List.of("UW-TL-003"), TERM_LIFE,
                        with(termLife(), "healthDeclarations", List.of("DIABETES")), tl),
                arguments("UW-TL-004", "TERM_LIFE", MANUAL_REVIEW, List.of("UW-TL-004"), TERM_LIFE,
                        with(termLife(), "bmi", "37"), tl),
                arguments("UW-TL-005", "TERM_LIFE", DECLINE, List.of("UW-TL-004", "UW-TL-005"), TERM_LIFE,
                        with(termLife(), "bmi", "46"), tl),
                arguments("UW-MO-001", "MOTOR", DECLINE, List.of("UW-MO-001"), MOTOR,
                        with(motor(), "claimsLast3Years", 3), mo),
                arguments("UW-MO-002", "MOTOR", DECLINE, List.of("UW-MO-002"), MOTOR,
                        with(motor(), "vehicleAgeYears", 16), mo),
                arguments("UW-MO-003", "MOTOR", MANUAL_REVIEW, List.of("UW-MO-003"), MOTOR,
                        with(motor(), "driverAge", 20), mo),
                arguments("UW-MO-004", "MOTOR", MANUAL_REVIEW, List.of("UW-MO-004"), MOTOR,
                        with(motor(), "vehicleUse", "COMMERCIAL", "vehicleValue", "6000000.00"), mo),
                arguments("UW-HH-001", "HOUSEHOLD", DECLINE, List.of("UW-HH-001"), HOUSEHOLD,
                        with(household(), "propertyAgeYears", 101), hh),
                arguments("UW-HH-002", "HOUSEHOLD", MANUAL_REVIEW, List.of("UW-HH-002"), HOUSEHOLD,
                        with(household(), "floodZone", "HIGH", "hasFloodDefences", false), hh),
                arguments("UW-HH-003", "HOUSEHOLD", MANUAL_REVIEW, List.of("UW-HH-003"), HOUSEHOLD,
                        with(household(), "constructionType", "TIMBER"), hh),
                arguments("UW-HH-004", "HOUSEHOLD", MANUAL_REVIEW, List.of("UW-HH-004"), HOUSEHOLD,
                        with(household(), "buildingSumInsured", "30000000.00", "contentsSumInsured",
                                "1000000.00"), hh));
    }
}
