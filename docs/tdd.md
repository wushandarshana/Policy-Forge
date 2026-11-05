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

### 1. Red: `cd638af test(red): AC-04.1-04.6 underwriting rule evaluator, condition operators and reason-code matrix (stubs only)`

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

### 2. Green: `831edd4 feat(green): AC-04.1-04.6 UnderwritingRuleEvaluator and ConditionEvaluator with BigDecimal comparison, fail-closed conditions and decline>refer precedence`

The minimal implementation:
1. Iterate the rules, and evaluate each condition list with `ConditionEvaluator` (BigDecimal compare for numerics).
2. Collect fired rules, sorted by code.
3. Outcome = DECLINE if any fired rule is DECLINE, else MANUAL_REVIEW if any is REFER, else AUTO_BIND with `UW-OK`.

All tests are green. Commit.

### 3. Refactor

The AC-04 green step needed no clean-up: `ConditionEvaluator` was already a `switch` over an `Operator` enum (EQ, GT,
IN, NOT_EMPTY, …), with every branch covered. The refactor rule is "refactor only when the green code has duplication or poor names, and never
change behaviour". So this cycle has no refactor commit, rather than an artificial one. A real refactor step from the
same sprint is `53a2944 refactor: simplify the submit branch of CaseActionForm`, made after the Workbench page's
green commit `ac9e7ce`. Its Vitest tests (AC-09.x) stayed green and the change has no effect on behaviour.

> The code excerpts above are condensed for reading. The real test is
> `backend/src/test/java/com/horizon/policyforge/domain/underwriting/UnderwritingRuleEvaluatorTest.java`; see
> `git show cd638af` and `git show 831edd4`.

## Evidence

Every AC's failing test was committed before its implementation. Read the history with
`git log --oneline --grep "^test(red)"` and `git log --oneline --grep "(green)"`. The test output of each red and green
step is archived next to the sprint contract.

| Sprint | Red → green (examples; the full tables are in the PR records) | Logs |
|---|---|---|
| S1 catalog and quote | Tests and code were committed per task by the harness, before the commit policy split them. The red then green order is proven by the logs | `sprint-contracts/S1-feat-catalog-quote/evidence/TASK-*-{red,green}.log` |
| S2 application and underwriting | `cd638af` → `831edd4` (AC-04), `447f27d` → `3647ad5` (AC-03, AC-09), `4632739` → `3001b7b`, `a985d4a` → `1a77eb4`, `8cb4242` → `b941f95`; refactor `53a2944` | [PR-008](prs/PR-008.md), `sprint-contracts/S2-…/evidence/` |
| S3 issuance and endorsement | `26c2f23` → `33d8c73` (AC-10 matrix), `a05f186` → `b44053e` (AC-01.12, AC-03.9), `48e21e7` → `53a2f9b` (AC-05), `834bc25` → `f5f8171` (AC-06), `433c03f` → `fb4fb78`; review fix `e892374` → `70206d6` | [PR-009](prs/PR-009.md), `sprint-contracts/S3-…/evidence/` |
| S4 renewal and cancellation | `c93edb1` → `8b4d00a` (domain), `db692c8` → `249e684` (AC-07), `47333cf` → `bf7debc` (AC-08), `3abe2f6` → `07df7e8` (AC-11), `f61f067` → `b29c511`; review fix `6921161` → `39fae46` | [PR-010](prs/PR-010.md), `sprint-contracts/S4-…/evidence/` |
| S5 CI hardening | `1e26f7f` → `ed11eb6` (NFR-08.1), `9e9b20c` → `10fae5e` (NFR-09.4), `6d26416` → `59420aa` (NFR-09.5), `54896b1` → `693b60a` (NFR-09.1–09.3); review fix `b43bdac` → `e21e813` | [PR-011](prs/PR-011.md), `sprint-contracts/S5-…/evidence/` |

Characterisation tests that pass on unchanged code (AC-07.7, AC-08.5 in Sprint 5) are committed as `test:` only after
temporary mutations made them fail on a named assertion. The mutation proofs are in the TASK-001 red log (KD-022).
