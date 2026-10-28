# TDD Discipline

PolicyForge is built **test-first by agents**. The generator agent may not write production code for an AC until a
failing test tagged with that AC is committed.

## The cycle

| Step | What the agent does | Commit message prefix | Must be true at commit |
|---|---|---|---|
| **Red** | Writes the smallest test(s) that express one AC from the spec, using the spec's concrete numbers | `test(red): AC-NN …` | Compiles (stubs allowed); the new test **fails** for the right reason |
| **Green** | Writes the minimum production code to pass | `feat(green): AC-NN …` | All tests pass |
| **Refactor** | Removes duplication and improves names and structure, with no behaviour change | `refactor: …` | All tests pass; ArchUnit passes; coverage not reduced |

The harness evaluator rejects a sprint whose history shows a `feat(green)` for an AC without a preceding
`test(red)` for the same AC. `/ac-coverage` reports ACs that have no tagged test.

## Conventions

- **Tagging:** JUnit tests carry `@Tag("AC-04")` and `@DisplayName("AC-04.3: decline beats refer …")`. Vitest and
  Playwright test titles start with `AC-NN.M:`.
- **Domain first:** pure domain tests (no Spring context) come first because they are fast and exact. Service and web
  slice tests follow.
- **Fixed clock:** every time-dependent test injects `Clock.fixed(...)`. `LocalDate.now()` without a clock is banned.
- **Money assertions:** compare with `isEqualByComparingTo` **and** assert `scale() == 2` for final amounts.
- **Parameterised matrices:** the state machine (AC-10.1) and the reason-code matrix (AC-04.6) use
  `@ParameterizedTest` + `@CsvSource` / `@MethodSource`, one row per rule or cell.

## Worked example: AC-04 reason-code matrix

**Spec (underwriting_spec.md):** "AC-04.6 For each v1 rule, a parameterised test case triggers only that rule and asserts
the expected outcome and single reason code." Also AC-04.3: decline beats refer, and all fired codes are listed.

### 1. Red: `test(red): AC-04 reason-code matrix for TERM_LIFE v1`

```java
@Tag("AC-04")
class UnderwritingRuleEvaluatorTest {

    private final RuleSet termLifeV1 = RuleSetFixtures.termLifeV1();
    private final UnderwritingRuleEvaluator evaluator = new UnderwritingRuleEvaluator();

    @ParameterizedTest(name = "AC-04.6: {0} → {1}")
    @MethodSource("singleRuleCases")
    void eachRuleFiresAlone(String code, UnderwritingOutcome expected, Map<String, Object> inputs) {
        UnderwritingResult result = evaluator.evaluate(termLifeV1, RiskProfile.of(inputs));
        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.reasonCodes()).containsExactly(code);
    }

    static Stream<Arguments> singleRuleCases() {
        Map<String, Object> clean = Map.of("age", 35, "smoker", false, "sumInsured", "5000000.00",
                "termYears", 20, "bmi", "24", "healthDeclarations", List.of());
        return Stream.of(
            arguments("UW-TL-001", DECLINE,       with(clean, "age", 62)),
            arguments("UW-TL-002", MANUAL_REVIEW, with(clean, "smoker", true, "sumInsured", "12000000.00")),
            arguments("UW-TL-003", MANUAL_REVIEW, with(clean, "healthDeclarations", List.of("DIABETES"))),
            arguments("UW-TL-004", MANUAL_REVIEW, with(clean, "bmi", "37")));
    }

    @Test @DisplayName("AC-04.3: decline beats refer and all fired codes are listed")
    void declineBeatsRefer() {
        var result = evaluator.evaluate(termLifeV1, RiskProfile.of(Map.of(/* age 62, bmi 37 … */)));
        assertThat(result.outcome()).isEqualTo(DECLINE);
        assertThat(result.reasonCodes()).containsExactly("UW-TL-001", "UW-TL-004");
    }
}
```

Run: `./mvnw test -Dtest=UnderwritingRuleEvaluatorTest`. It fails with `UnsupportedOperationException` from the
stubbed `evaluate`. This is the right reason. Commit.

> UW-TL-005 (BMI > 45 → DECLINE) can't fire alone, because BMI > 45 also fires UW-TL-004. The red step exposed this,
> and the matrix row asserts `containsExactly("UW-TL-004","UW-TL-005")` with DECLINE. The spec wording was then
> clarified ("triggers only that rule *where the rule set allows*"). This shows **spec is truth**: the spec is fixed
> deliberately, not silently worked around in code.

### 2. Green: `feat(green): AC-04 evaluate rules with decline>refer precedence`

The minimal implementation:
1. Iterate the rules, and evaluate each condition list with `ConditionEvaluator` (BigDecimal compare for numerics).
2. Collect fired rules, sorted by code.
3. Outcome = DECLINE if any fired rule is DECLINE, else MANUAL_REVIEW if any is REFER, else AUTO_BIND with `UW-OK`.

All tests are green. Commit.

### 3. Refactor: `refactor: extract ConditionEvaluator operators into enum strategy`

Replace the `switch` on operator strings with an `Operator` enum that holds a `BiPredicate`. The tests are still
green, ArchUnit `DomainPurityArchTest` is still green, and coverage holds. Commit.

## Evidence

Each sprint's PR record in `docs/prs/` lists the red, green and refactor commit triples per AC. Read the history with
`git log --oneline --grep "AC-04"`.
