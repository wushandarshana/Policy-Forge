package com.horizon.policyforge.domain.endorsement;

import static com.horizon.policyforge.domain.endorsement.EndorsementFixtures.TODAY;
import static com.horizon.policyforge.domain.endorsement.EndorsementFixtures.address;
import static com.horizon.policyforge.domain.endorsement.EndorsementFixtures.household;
import static com.horizon.policyforge.domain.endorsement.EndorsementFixtures.money;
import static com.horizon.policyforge.domain.endorsement.EndorsementFixtures.motor;
import static com.horizon.policyforge.domain.endorsement.EndorsementFixtures.nominee;
import static com.horizon.policyforge.domain.endorsement.EndorsementFixtures.termLife;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.policy.InvalidPolicyStateException;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The endorsement validation matrix E1 to E11 of the endorsement-validator skill. Each row is named with the nearest
 * sub-criterion (plan P-4): E1 AC-10.2, E2 and E3 AC-06.6, E4 AC-06.4, E5 to E7 AC-06.5, E8 and E9 AC-06.2,
 * E10 and E11 AC-06.1.
 */
@Tag("AC-06")
@Tag("AC-10")
class EndorsementValidatorTest {

    /** Marker for a row that expects InvalidPolicyStateException instead of violations. */
    private static final String STATE = "STATE";

    private final EndorsementValidator validator = new EndorsementValidator();

    private static Arguments row(String name, PolicySnapshot policy, EndorsementRequest request, String... expected) {
        return Arguments.of(name, policy, request, List.of(expected));
    }

    static Stream<Arguments> matrix() {
        LocalDate lastEndorsed = LocalDate.of(2027, 3, 1);
        return Stream.of(
                row("AC-10.2: E1 CANCELLED policy raises InvalidPolicyStateException",
                        termLife(PolicyStatus.CANCELLED, null, List.of()),
                        EndorsementRequest.changeAddress(null, address("411001")), STATE),
                row("AC-10.2: E1 LAPSED policy raises InvalidPolicyStateException",
                        termLife(PolicyStatus.LAPSED, null, List.of()),
                        EndorsementRequest.changeAddress(null, address("411001")), STATE),
                row("AC-06.6: E2 effective date after the term end is EFFECTIVE_DATE_OUT_OF_TERM",
                        termLife(), EndorsementRequest.changeAddress(LocalDate.of(2027, 11, 1), address("411001")),
                        "effectiveDate:EFFECTIVE_DATE_OUT_OF_TERM"),
                row("AC-06.6: E3 effective date before the latest endorsement is EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT",
                        termLife(PolicyStatus.ENDORSED, lastEndorsed, List.of()),
                        EndorsementRequest.changeAddress(LocalDate.of(2027, 2, 28), address("411001")),
                        "effectiveDate:EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT"),
                row("AC-06.4: E4 ADD_NOMINEE on a MOTOR policy is NOMINEE_NOT_SUPPORTED",
                        motor(), EndorsementRequest.addNominee(null, nominee("SPOUSE", 50)),
                        "nominee:NOMINEE_NOT_SUPPORTED"),
                row("AC-06.5: E5 a fourth nominee is NOMINEE_LIMIT_REACHED",
                        termLife(PolicyStatus.ENDORSED, null,
                                List.of(nominee("SPOUSE", 20), nominee("CHILD", 20), nominee("PARENT", 20))),
                        EndorsementRequest.addNominee(null, nominee("SIBLING", 10)),
                        "nominee:NOMINEE_LIMIT_REACHED"),
                row("AC-06.5: E6 total share above 100 is NOMINEE_SHARE_EXCEEDED",
                        termLife(), EndorsementRequest.addNominee(null, nominee("CHILD", 50)),
                        "nominee.sharePercent:NOMINEE_SHARE_EXCEEDED"),
                row("AC-06.5: E7 relationship outside the five values is NOMINEE_RELATIONSHIP_INVALID",
                        termLife(), EndorsementRequest.addNominee(null, nominee("FRIEND", 10)),
                        "nominee.relationship:NOMINEE_RELATIONSHIP_INVALID"),
                row("AC-06.2: E8 new sum insured outside the rule version limits is SUM_INSURED_OUT_OF_RANGE",
                        termLife(), EndorsementRequest.changeSumInsured(null, money("400000.00")),
                        "newSumInsured:SUM_INSURED_OUT_OF_RANGE"),
                row("AC-06.2: E9 new sum insured equal to the current is SUM_INSURED_UNCHANGED",
                        termLife(), EndorsementRequest.changeSumInsured(null, money("5000000.00")),
                        "newSumInsured:SUM_INSURED_UNCHANGED"),
                row("AC-06.1: E10 postcode that is not 6 digits is POSTCODE_INVALID",
                        termLife(), EndorsementRequest.changeAddress(null, address("41100")),
                        "address.postcode:POSTCODE_INVALID"),
                row("AC-06.1: E11 a valid request passes",
                        termLife(), EndorsementRequest.changeAddress(null, address("411001"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    @Tag("AC-06.1")
    @Tag("AC-06.2")
    @Tag("AC-06.4")
    @Tag("AC-06.5")
    @Tag("AC-06.6")
    @Tag("AC-10.2")
    void validationMatrix(String name, PolicySnapshot policy, EndorsementRequest request, List<String> expected) {
        if (expected.equals(List.of(STATE))) {
            assertThatThrownBy(() -> validator.validate(policy, request, TODAY))
                    .isInstanceOf(InvalidPolicyStateException.class)
                    .satisfies(e -> {
                        InvalidPolicyStateException ex = (InvalidPolicyStateException) e;
                        assertThat(ex.from()).isEqualTo(policy.status());
                        assertThat(ex.to()).isEqualTo(PolicyStatus.ENDORSED);
                    });
            return;
        }

        EndorsementValidation result = validator.validate(policy, request, TODAY);

        assertThat(result.violations()).extracting(v -> v.field() + ":" + v.code()).containsExactlyElementsOf(expected);
        assertThat(result.isValid()).isEqualTo(expected.isEmpty());
        if (expected.isEmpty()) {
            assertThat(result.command()).isNotNull();
            assertThat(result.command().type()).isEqualTo(request.type());
        } else {
            assertThat(result.command()).isNull();
            assertThat(result.firstCode()).isEqualTo(expected.get(0).substring(expected.get(0).indexOf(':') + 1));
        }
    }

    @Test
    @Tag("AC-06.5")
    @DisplayName("AC-06.5: with nominees totalling 80% a 30% nominee is NOMINEE_SHARE_EXCEEDED and a 20% nominee (total 100) is accepted")
    void nomineeShareBoundary() {
        PolicySnapshot policy = termLife(PolicyStatus.ENDORSED, null,
                List.of(nominee("SPOUSE", 50), nominee("CHILD", 30)));

        EndorsementValidation over = validator.validate(policy, EndorsementRequest.addNominee(null, nominee("PARENT", 30)), TODAY);
        EndorsementValidation exact = validator.validate(policy, EndorsementRequest.addNominee(null, nominee("PARENT", 20)), TODAY);

        assertThat(over.violations()).extracting(EndorsementViolation::code).containsExactly("NOMINEE_SHARE_EXCEEDED");
        assertThat(exact.isValid()).isTrue();
        assertThat(exact.command().nominee().sharePercent()).isEqualTo(20);
    }

    @Test
    @Tag("AC-06.5")
    @DisplayName("AC-06.5: with three nominees a fourth is NOMINEE_LIMIT_REACHED, with two a third is accepted")
    void nomineeCountBoundary() {
        PolicySnapshot three = termLife(PolicyStatus.ENDORSED, null,
                List.of(nominee("SPOUSE", 30), nominee("CHILD", 30), nominee("PARENT", 30)));
        PolicySnapshot two = termLife(PolicyStatus.ENDORSED, null,
                List.of(nominee("SPOUSE", 30), nominee("CHILD", 30)));
        EndorsementRequest fourth = EndorsementRequest.addNominee(null, nominee("SIBLING", 10));

        assertThat(validator.validate(three, fourth, TODAY).violations())
                .extracting(EndorsementViolation::code).containsExactly("NOMINEE_LIMIT_REACHED");
        assertThat(validator.validate(two, fourth, TODAY).isValid()).isTrue();
    }

    @Test
    @Tag("AC-06.5")
    @DisplayName("AC-06.5: every one of the five relationships is accepted")
    void allRelationshipsAccepted() {
        for (String relationship : List.of("SPOUSE", "CHILD", "PARENT", "SIBLING", "OTHER")) {
            assertThat(validator.validate(termLife(), EndorsementRequest.addNominee(null, nominee(relationship, 10)), TODAY)
                    .isValid()).as(relationship).isTrue();
        }
    }

    @Test
    @Tag("AC-06.6")
    @DisplayName("AC-06.6: the effective date may be the term start, the term end and the latest endorsement's date")
    void effectiveDateWindowBounds() {
        PolicySnapshot policy = termLife(PolicyStatus.ENDORSED, LocalDate.of(2027, 3, 1), List.of());

        assertThat(validator.validate(policy, EndorsementRequest.changeAddress(LocalDate.of(2027, 3, 1), address("411001")), TODAY).isValid()).isTrue();
        assertThat(validator.validate(policy, EndorsementRequest.changeAddress(LocalDate.of(2027, 10, 31), address("411001")), TODAY).isValid()).isTrue();
        assertThat(validator.validate(termLife(), EndorsementRequest.changeAddress(LocalDate.of(2026, 11, 1), address("411001")), TODAY).isValid()).isTrue();
        assertThat(validator.validate(termLife(), EndorsementRequest.changeAddress(LocalDate.of(2026, 10, 31), address("411001")), TODAY)
                .firstCode()).isEqualTo("EFFECTIVE_DATE_OUT_OF_TERM");
    }

    @Test
    @Tag("AC-06.6")
    @DisplayName("AC-06.6: a missing effective date defaults to today, which is checked against the term")
    void missingEffectiveDateDefaultsToToday() {
        EndorsementValidation inTerm = validator.validate(termLife(), EndorsementRequest.changeAddress(null, address("411001")), TODAY);
        EndorsementValidation outOfTerm = validator.validate(termLife(), EndorsementRequest.changeAddress(null, address("411001")),
                LocalDate.of(2027, 11, 5));

        assertThat(inTerm.command().effectiveDate()).isEqualTo(TODAY);
        assertThat(outOfTerm.firstCode()).isEqualTo("EFFECTIVE_DATE_OUT_OF_TERM");
    }

    @Test
    @Tag("AC-06.6")
    @DisplayName("AC-06.6: E2 to E10 are all collected in E-order, with the first as the leading code")
    void violationsAreCollected() {
        PolicySnapshot policy = termLife(PolicyStatus.ENDORSED, LocalDate.of(2027, 3, 1), List.of());
        EndorsementRequest request = EndorsementRequest.changeAddress(LocalDate.of(2027, 11, 15), address("12"));

        EndorsementValidation result = validator.validate(policy, request, TODAY);

        assertThat(result.violations()).extracting(v -> v.field() + ":" + v.code()).containsExactly(
                "effectiveDate:EFFECTIVE_DATE_OUT_OF_TERM", "address.postcode:POSTCODE_INVALID");
        assertThat(result.firstCode()).isEqualTo("EFFECTIVE_DATE_OUT_OF_TERM");

        EndorsementValidation nominee = validator.validate(
                termLife(PolicyStatus.ENDORSED, LocalDate.of(2027, 3, 1),
                        List.of(nominee("SPOUSE", 40), nominee("CHILD", 40), nominee("PARENT", 20))),
                EndorsementRequest.addNominee(LocalDate.of(2027, 2, 1), nominee("FRIEND", 10)), TODAY);
        assertThat(nominee.violations()).extracting(EndorsementViolation::code).containsExactly(
                "EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT", "NOMINEE_LIMIT_REACHED", "NOMINEE_SHARE_EXCEEDED",
                "NOMINEE_RELATIONSHIP_INVALID");
    }

    @Test
    @Tag("AC-10.2")
    @DisplayName("AC-10.2: E1 short-circuits, so a CANCELLED policy with other problems still raises only the state error")
    void stateErrorShortCircuits() {
        PolicySnapshot cancelled = termLife(PolicyStatus.CANCELLED, null, List.of());
        EndorsementRequest bad = EndorsementRequest.changeAddress(LocalDate.of(2030, 1, 1), address("1"));

        assertThatThrownBy(() -> validator.validate(cancelled, bad, TODAY))
                .isInstanceOf(InvalidPolicyStateException.class);
    }

    @Test
    @Tag("AC-10.2")
    @DisplayName("AC-10.2: ACTIVE, ENDORSED and RENEWED policies are in force and can be endorsed")
    void inForceStatesPass() {
        for (PolicyStatus status : List.of(PolicyStatus.ACTIVE, PolicyStatus.ENDORSED, PolicyStatus.RENEWED)) {
            assertThat(validator.validate(termLife(status, null, List.of()),
                    EndorsementRequest.changeAddress(null, address("411001")), TODAY).isValid()).as(status.name()).isTrue();
        }
    }

    @Test
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: a valid address change returns a normalised command with trimmed text and the resolved date")
    void validAddressIsNormalised() {
        Address raw = new Address("  12 Test Lane ", " Pune  ", "411001");

        EndorsementCommand command = validator.validate(termLife(), EndorsementRequest.changeAddress(null, raw), TODAY).command();

        assertThat(command.type()).isEqualTo(EndorsementType.CHANGE_ADDRESS);
        assertThat(command.effectiveDate()).isEqualTo(TODAY);
        assertThat(command.address().line1()).isEqualTo("12 Test Lane");
        assertThat(command.address().city()).isEqualTo("Pune");
        assertThat(command.address().postcode()).isEqualTo("411001");
        assertThat(command.newSumInsured()).isEmpty();
        assertThat(command.nominee()).isNull();
    }

    @Test
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: postcodes with letters, spaces or 7 digits are POSTCODE_INVALID and a null postcode is too")
    void postcodeMustBeSixDigits() {
        for (String bad : new String[] {"41100A", "411 01", "4110011", "", "४११००१"}) {
            assertThat(validator.validate(termLife(), EndorsementRequest.changeAddress(null, address(bad)), TODAY)
                    .firstCode()).as("[" + bad + "]").isEqualTo("POSTCODE_INVALID");
        }
        assertThat(validator.validate(termLife(), EndorsementRequest.changeAddress(null, new Address("1 Lane", "Pune", null)), TODAY)
                .firstCode()).isEqualTo("POSTCODE_INVALID");
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: sum-insured bounds are inclusive and a valid TERM_LIFE change returns the new value at scale 2")
    void sumInsuredBounds() {
        assertThat(validator.validate(termLife(), EndorsementRequest.changeSumInsured(null, money("500000")), TODAY).isValid()).isTrue();
        assertThat(validator.validate(termLife(), EndorsementRequest.changeSumInsured(null, money("50000000.00")), TODAY).isValid()).isTrue();
        assertThat(validator.validate(termLife(), EndorsementRequest.changeSumInsured(null, money("50000000.01")), TODAY)
                .firstCode()).isEqualTo("SUM_INSURED_OUT_OF_RANGE");

        EndorsementCommand command = validator
                .validate(termLife(), EndorsementRequest.changeSumInsured(null, money("7000000")), TODAY).command();
        assertThat(command.newSumInsured()).containsOnlyKeys("sumInsured");
        assertThat(command.newSumInsured().get("sumInsured")).isEqualTo(money("7000000.00"));
        assertThat(command.newSumInsured().get("sumInsured").scale()).isEqualTo(2);
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: a MOTOR change replaces vehicleValue and is checked against the MOTOR limits")
    void motorSumInsured() {
        EndorsementValidation ok = validator.validate(motor(), EndorsementRequest.changeSumInsured(null, money("900000.00")), TODAY);
        EndorsementValidation same = validator.validate(motor(), EndorsementRequest.changeSumInsured(null, money("800000.00")), TODAY);
        EndorsementValidation low = validator.validate(motor(), EndorsementRequest.changeSumInsured(null, money("99999.99")), TODAY);

        assertThat(ok.command().newSumInsured()).containsOnlyKeys("vehicleValue");
        assertThat(ok.command().newSumInsured().get("vehicleValue")).isEqualTo(money("900000.00"));
        assertThat(same.firstCode()).isEqualTo("SUM_INSURED_UNCHANGED");
        assertThat(low.firstCode()).isEqualTo("SUM_INSURED_OUT_OF_RANGE");
    }

    @Test
    @Tag("AC-06.2")
    @DisplayName("AC-06.2: HOUSEHOLD checks the building plus contents total, and unchanged only when every supplied component is equal")
    void householdSumInsured() {
        EndorsementValidation ok = validator.validate(household(),
                EndorsementRequest.changeHouseholdSumInsured(null, money("7000000.00"), null), TODAY);
        EndorsementValidation bothSame = validator.validate(household(),
                EndorsementRequest.changeHouseholdSumInsured(null, money("6000000.00"), money("1000000.00")), TODAY);
        EndorsementValidation oneChanged = validator.validate(household(),
                EndorsementRequest.changeHouseholdSumInsured(null, money("6000000.00"), money("1500000.00")), TODAY);
        EndorsementValidation tooLow = validator.validate(household(),
                EndorsementRequest.changeHouseholdSumInsured(null, money("100000.00"), money("50000.00")), TODAY);
        EndorsementValidation tooHigh = validator.validate(household(),
                EndorsementRequest.changeHouseholdSumInsured(null, money("49000000.00"), null), TODAY);

        assertThat(ok.isValid()).isTrue();
        assertThat(ok.command().newSumInsured()).containsOnlyKeys("buildingSumInsured");
        assertThat(bothSame.violations()).extracting(EndorsementViolation::code).containsExactly("SUM_INSURED_UNCHANGED");
        assertThat(oneChanged.isValid()).isTrue();
        assertThat(oneChanged.command().newSumInsured()).containsOnlyKeys("buildingSumInsured", "contentsSumInsured");
        assertThat(tooLow.firstCode()).isEqualTo("SUM_INSURED_OUT_OF_RANGE");
        assertThat(tooHigh.firstCode()).isEqualTo("SUM_INSURED_OUT_OF_RANGE");
        assertThat(tooHigh.violations().get(0).field()).isEqualTo("newBuildingSumInsured");
    }

    @Test
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: messages are fixed text and never contain the submitted values")
    void messagesNeverEchoInput() {
        Nominee secret = new Nominee("Zed Secretname", "FRIEND", 77);
        EndorsementValidation nominee = validator.validate(termLife(), EndorsementRequest.addNominee(null, secret), TODAY);
        EndorsementValidation address = validator.validate(termLife(),
                EndorsementRequest.changeAddress(LocalDate.of(2031, 5, 5), new Address("9 Secret Road", "Hidden", "99")), TODAY);

        assertThat(nominee.violations()).isNotEmpty();
        assertThat(address.violations()).hasSize(2);
        List<String> messages = Stream.concat(nominee.violations().stream(), address.violations().stream())
                .map(EndorsementViolation::message).toList();
        assertThat(messages).allSatisfy(m -> assertThat(m).isNotBlank()
                .doesNotContain("Zed").doesNotContain("FRIEND").doesNotContain("77").doesNotContain("Secret")
                .doesNotContain("Hidden").doesNotContain("99").doesNotContain("2031"));
    }

    @Test
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: string forms of the request, command, address and nominee show no personal data")
    void stringFormsShowNoPii() {
        Nominee n = new Nominee("Zed Secretname", "SPOUSE", 10);
        Address a = new Address("9 Secret Road", "Hidden", "411001");

        assertThat(n.toString()).doesNotContain("Zed");
        assertThat(a.toString()).doesNotContain("Secret").doesNotContain("Hidden");
        assertThat(EndorsementRequest.addNominee(null, n).toString()).doesNotContain("Zed");
        assertThat(new EndorsementCommand(EndorsementType.ADD_NOMINEE, TODAY, null, n, Map.of()).toString())
                .doesNotContain("Zed");
    }

    @Test
    @Tag("AC-06.1")
    @DisplayName("AC-06.1: EndorsementType re-rates only for CHANGE_SUM_INSURED")
    void onlySumInsuredReRates() {
        assertThat(EndorsementType.CHANGE_ADDRESS.reRates()).isFalse();
        assertThat(EndorsementType.ADD_NOMINEE.reRates()).isFalse();
        assertThat(EndorsementType.CHANGE_SUM_INSURED.reRates()).isTrue();
        assertThat(EndorsementType.values()).hasSize(3);
    }
}
