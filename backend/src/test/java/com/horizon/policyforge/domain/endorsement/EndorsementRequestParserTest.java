package com.horizon.policyforge.domain.endorsement;

import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.domain.endorsement.EndorsementRequestParser.Parsed;
import com.horizon.policyforge.domain.endorsement.EndorsementRequestParser.ShapeViolation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * The request-shape rules of an endorsement (plan P-5, step 1), which run before the policy is looked up: unknown keys,
 * a missing or unknown type, missing payload keys, wrong JSON types, a malformed date or decimal, a non-integer share
 * and a blank or over-long name, line or city. Business rules (postcode format, relationship value, share total,
 * limits) are not shape rules: they belong to {@code EndorsementValidator}. A sub-criterion names each row (P-4): address
 * shapes AC-06.1, nominee shapes AC-06.5, sum-insured shapes AC-06.2.
 */
@Tag("AC-06")
class EndorsementRequestParserTest {

    private final EndorsementRequestParser parser = new EndorsementRequestParser();

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> goodAddress() {
        return map("line1", "1 A Street", "city", "Pune", "postcode", "411001");
    }

    private static Map<String, Object> goodNominee() {
        return map("name", "Ravi Nominee", "relationship", "SPOUSE", "sharePercent", 40);
    }

    private static List<String> fields(Parsed parsed) {
        return parsed.violations().stream().map(ShapeViolation::field).toList();
    }

    // ---- well-formed requests ----

    @Test
    @DisplayName("AC-06.1: a well-formed CHANGE_ADDRESS is read into a typed request, with the effective date when given")
    void readsAnAddressChange() {
        Parsed parsed = parser.parse(map("type", "CHANGE_ADDRESS", "address", goodAddress(), "effectiveDate", "2027-01-15"));

        assertThat(parsed.isValid()).isTrue();
        EndorsementRequest request = parsed.request();
        assertThat(request.type()).isEqualTo(EndorsementType.CHANGE_ADDRESS);
        assertThat(request.effectiveDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(request.address()).isEqualTo(new Address("1 A Street", "Pune", "411001"));
        assertThat(request.nominee()).isNull();
    }

    @Test
    @DisplayName("AC-06.1: an absent or null effective date is read as no date (the validator defaults it to today)")
    void absentDateIsNoDate() {
        Parsed absent = parser.parse(map("type", "CHANGE_ADDRESS", "address", goodAddress()));
        Parsed nulled = parser.parse(map("type", "CHANGE_ADDRESS", "address", goodAddress(), "effectiveDate", null));

        assertThat(absent.request().effectiveDate()).isNull();
        assertThat(nulled.request().effectiveDate()).isNull();
    }

    @Test
    @DisplayName("AC-06.5: a well-formed ADD_NOMINEE is read with its whole-number share, whatever integer type the JSON reader used")
    void readsANominee() {
        for (Object share : List.of(40, 40L, java.math.BigInteger.valueOf(40))) {
            Parsed parsed = parser.parse(map("type", "ADD_NOMINEE", "nominee",
                    map("name", " Ravi Nominee ", "relationship", "SPOUSE", "sharePercent", share)));

            assertThat(parsed.isValid()).as(share.getClass().getSimpleName()).isTrue();
            assertThat(parsed.request().nominee().sharePercent()).isEqualTo(40);
            assertThat(parsed.request().nominee().relationship()).isEqualTo("SPOUSE");
        }
    }

    @Test
    @DisplayName("AC-06.2: a well-formed CHANGE_SUM_INSURED is read with exact decimals, for the single key and for the household keys")
    void readsSumInsuredChanges() {
        EndorsementRequest single = parser.parse(map("type", "CHANGE_SUM_INSURED", "newSumInsured", "7000000.00"))
                .request();
        EndorsementRequest household = parser.parse(map("type", "CHANGE_SUM_INSURED",
                "newBuildingSumInsured", "7000000", "newContentsSumInsured", "1500000.5")).request();

        assertThat(single.newSumInsured()).isEqualByComparingTo(new BigDecimal("7000000.00"));
        assertThat(single.newBuildingSumInsured()).isNull();
        assertThat(household.newBuildingSumInsured()).isEqualByComparingTo(new BigDecimal("7000000"));
        assertThat(household.newContentsSumInsured()).isEqualByComparingTo(new BigDecimal("1500000.50"));
        assertThat(household.newSumInsured()).isNull();
    }

    // ---- unknown keys are named in the shared sanitised form (P-14) ----

    @Test
    @DisplayName("AC-06.1: an unknown key is named with its fixed prefix and the key sanitised: 64 characters at most, anything outside A-Z a-z 0-9 _ . - replaced by an underscore, and keys that sanitise alike named once")
    void unknownKeysAreNamedSanitised() {
        String longKey = "unknownKey" + "Q".repeat(4990);
        Map<String, Object> address = goodAddress();
        address.put(longKey, "x");
        address.put("<a b=\"c\" d='e'>", "x");
        Map<String, Object> nominee = goodNominee();
        nominee.put("n o", "x");
        nominee.put("n<o", "x");

        Parsed onAddress = parser.parse(map("type", "CHANGE_ADDRESS", "address", address, longKey, "x",
                "a b", "x", "a<b", "x"));
        Parsed onNominee = parser.parse(map("type", "ADD_NOMINEE", "nominee", nominee));

        assertThat(fields(onAddress)).containsExactlyInAnyOrder(longKey.substring(0, 64), "a_b",
                "address." + longKey.substring(0, 64), "address._a_b__c__d__e__");
        assertThat(fields(onNominee)).containsExactly("nominee.n_o");
        onAddress.violations().forEach(violation -> assertThat(violation.message())
                .isEqualTo("is not an accepted field"));
    }

    // ---- shape errors, one row each ----

    static Stream<Arguments> addressShapeErrors() {
        return Stream.of(
                Arguments.of("no type", map(), List.of("type")),
                Arguments.of("an unknown type", map("type", "NOPE"), List.of("type")),
                Arguments.of("a type that is not text", map("type", 7), List.of("type")),
                Arguments.of("no address", map("type", "CHANGE_ADDRESS"), List.of("address")),
                Arguments.of("an address that is not an object", map("type", "CHANGE_ADDRESS", "address", "1 A Street"),
                        List.of("address")),
                Arguments.of("a missing postcode", map("type", "CHANGE_ADDRESS", "address",
                        map("line1", "1 A", "city", "Pune")), List.of("address.postcode")),
                Arguments.of("a blank line1", map("type", "CHANGE_ADDRESS", "address",
                        map("line1", "   ", "city", "Pune", "postcode", "411001")), List.of("address.line1")),
                Arguments.of("a city that is not text", map("type", "CHANGE_ADDRESS", "address",
                        map("line1", "1 A", "city", 7, "postcode", "411001")), List.of("address.city")),
                Arguments.of("an over-long line1", map("type", "CHANGE_ADDRESS", "address",
                        map("line1", "x".repeat(201), "city", "Pune", "postcode", "411001")), List.of("address.line1")),
                Arguments.of("an over-long city", map("type", "CHANGE_ADDRESS", "address",
                        map("line1", "1 A", "city", "y".repeat(101), "postcode", "411001")), List.of("address.city")),
                Arguments.of("an unknown address key", map("type", "CHANGE_ADDRESS", "address",
                        map("line1", "1 A", "city", "Pune", "postcode", "411001", "country", "IN")),
                        List.of("address.country")),
                Arguments.of("an unknown top-level key", map("type", "CHANGE_ADDRESS", "address", goodAddress(),
                        "status", "CANCELLED"), List.of("status")),
                Arguments.of("another type's payload key", map("type", "CHANGE_ADDRESS", "address", goodAddress(),
                        "nominee", goodNominee()), List.of("nominee")),
                Arguments.of("a malformed date", map("type", "CHANGE_ADDRESS", "address", goodAddress(),
                        "effectiveDate", "01/02/2027"), List.of("effectiveDate")),
                Arguments.of("a date that is not text", map("type", "CHANGE_ADDRESS", "address", goodAddress(),
                        "effectiveDate", 20270201), List.of("effectiveDate")),
                Arguments.of("a date that does not exist", map("type", "CHANGE_ADDRESS", "address", goodAddress(),
                        "effectiveDate", "2027-02-30"), List.of("effectiveDate")));
    }

    @ParameterizedTest(name = "AC-06.1: shape error, {0}, names {2}")
    @MethodSource("addressShapeErrors")
    void addressShapeErrorsNameTheField(String label, Map<String, Object> body, List<String> expected) {
        Parsed parsed = parser.parse(body);

        assertThat(parsed.isValid()).isFalse();
        assertThat(parsed.request()).isNull();
        assertThat(fields(parsed)).containsExactlyInAnyOrderElementsOf(expected);
        parsed.violations().forEach(violation -> assertThat(violation.message()).as("a fixed message")
                .doesNotContain("1 A").doesNotContain("411001").doesNotContain("NOPE").doesNotContain("CANCELLED"));
    }

    static Stream<Arguments> nomineeShapeErrors() {
        return Stream.of(
                Arguments.of("no nominee", map("type", "ADD_NOMINEE"), List.of("nominee")),
                Arguments.of("a fractional share", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", "SPOUSE", "sharePercent", new BigDecimal("25.5"))),
                        List.of("nominee.sharePercent")),
                Arguments.of("a share given as text", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", "SPOUSE", "sharePercent", "25")),
                        List.of("nominee.sharePercent")),
                Arguments.of("a zero share", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", "SPOUSE", "sharePercent", 0)),
                        List.of("nominee.sharePercent")),
                Arguments.of("a negative share", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", "SPOUSE", "sharePercent", -5)),
                        List.of("nominee.sharePercent")),
                Arguments.of("a share too large for a whole number", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", "SPOUSE", "sharePercent", 99999999999L)),
                        List.of("nominee.sharePercent")),
                Arguments.of("a missing share", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", "SPOUSE")), List.of("nominee.sharePercent")),
                Arguments.of("an empty name", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "", "relationship", "SPOUSE", "sharePercent", 25)), List.of("nominee.name")),
                Arguments.of("an over-long name", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "n".repeat(101), "relationship", "SPOUSE", "sharePercent", 25)),
                        List.of("nominee.name")),
                Arguments.of("a relationship that is not text", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", 3, "sharePercent", 25)), List.of("nominee.relationship")),
                Arguments.of("a missing relationship", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "sharePercent", 25)), List.of("nominee.relationship")),
                Arguments.of("an unknown nominee key", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "Ravi", "relationship", "SPOUSE", "sharePercent", 25, "pan", "ABCDE1234F")),
                        List.of("nominee.pan")),
                Arguments.of("several problems at once", map("type", "ADD_NOMINEE", "nominee",
                        map("name", "", "relationship", 3, "sharePercent", "x")),
                        List.of("nominee.name", "nominee.relationship", "nominee.sharePercent")));
    }

    @ParameterizedTest(name = "AC-06.5: shape error, {0}, names {2}")
    @MethodSource("nomineeShapeErrors")
    void nomineeShapeErrorsNameTheField(String label, Map<String, Object> body, List<String> expected) {
        Parsed parsed = parser.parse(body);

        assertThat(parsed.isValid()).isFalse();
        assertThat(fields(parsed)).containsExactlyInAnyOrderElementsOf(expected);
        parsed.violations().forEach(violation -> assertThat(violation.message()).as("a fixed message")
                .doesNotContain("Ravi").doesNotContain("ABCDE1234F"));
    }

    @Test
    @DisplayName("AC-06.5: a share above 100 or a relationship outside the five values is not a shape error: the validator rules on it (NOMINEE_SHARE_EXCEEDED, NOMINEE_RELATIONSHIP_INVALID)")
    void businessValuesPassTheShapeCheck() {
        Parsed parsed = parser.parse(map("type", "ADD_NOMINEE", "nominee",
                map("name", "Ravi", "relationship", "NEIGHBOUR", "sharePercent", 150)));

        assertThat(parsed.isValid()).isTrue();
        assertThat(parsed.request().nominee().relationship()).isEqualTo("NEIGHBOUR");
        assertThat(parsed.request().nominee().sharePercent()).isEqualTo(150);
    }

    @Test
    @DisplayName("AC-06.1: a postcode that is not 6 digits is not a shape error: the validator rules on it (POSTCODE_INVALID)")
    void badPostcodePassesTheShapeCheck() {
        Parsed parsed = parser.parse(map("type", "CHANGE_ADDRESS", "address",
                map("line1", "1 A", "city", "Pune", "postcode", "41")));

        assertThat(parsed.isValid()).isTrue();
        assertThat(parsed.request().address().postcode()).isEqualTo("41");
    }

    static Stream<Arguments> sumInsuredShapeErrors() {
        return Stream.of(
                Arguments.of("no sum-insured key", map("type", "CHANGE_SUM_INSURED"), List.of("newSumInsured")),
                Arguments.of("a number instead of a decimal string", map("type", "CHANGE_SUM_INSURED",
                        "newSumInsured", 7000000), List.of("newSumInsured")),
                Arguments.of("three decimal places", map("type", "CHANGE_SUM_INSURED", "newSumInsured", "7000000.123"),
                        List.of("newSumInsured")),
                Arguments.of("text that is not a decimal", map("type", "CHANGE_SUM_INSURED", "newSumInsured", "7,000,000"),
                        List.of("newSumInsured")),
                Arguments.of("an exponent", map("type", "CHANGE_SUM_INSURED", "newSumInsured", "7e6"),
                        List.of("newSumInsured")),
                Arguments.of("a household component that is not a decimal", map("type", "CHANGE_SUM_INSURED",
                        "newBuildingSumInsured", "lots"), List.of("newBuildingSumInsured")),
                Arguments.of("an unknown key", map("type", "CHANGE_SUM_INSURED", "newSumInsured", "7000000.00",
                        "premium", "1.00"), List.of("premium")));
    }

    @ParameterizedTest(name = "AC-06.2: shape error, {0}, names {2}")
    @MethodSource("sumInsuredShapeErrors")
    void sumInsuredShapeErrorsNameTheField(String label, Map<String, Object> body, List<String> expected) {
        Parsed parsed = parser.parse(body);

        assertThat(parsed.isValid()).isFalse();
        assertThat(fields(parsed)).containsExactlyInAnyOrderElementsOf(expected);
        parsed.violations().forEach(violation -> assertThat(violation.message()).doesNotContain("7000000")
                .doesNotContain("lots"));
    }

    // ---- the product-specific key check, after the policy is known ----

    @Test
    @DisplayName("AC-06.2: TERM_LIFE and MOTOR take newSumInsured only, and HOUSEHOLD takes the building and contents keys only, with at least one of them")
    void sumInsuredKeysDependOnTheProduct() {
        EndorsementRequest single = parser.parse(map("type", "CHANGE_SUM_INSURED", "newSumInsured", "7000000.00"))
                .request();
        EndorsementRequest building = parser.parse(map("type", "CHANGE_SUM_INSURED", "newBuildingSumInsured",
                "7000000.00")).request();
        EndorsementRequest both = parser.parse(map("type", "CHANGE_SUM_INSURED", "newSumInsured", "7000000.00",
                "newBuildingSumInsured", "1.00")).request();

        assertThat(parser.checkProduct("TERM_LIFE", single)).isEmpty();
        assertThat(parser.checkProduct("MOTOR", single)).isEmpty();
        assertThat(parser.checkProduct("HOUSEHOLD", building)).isEmpty();
        assertThat(parser.checkProduct("TERM_LIFE", building).stream().map(ShapeViolation::field).toList())
                .containsExactlyInAnyOrder("newBuildingSumInsured", "newSumInsured");
        assertThat(parser.checkProduct("MOTOR", both).stream().map(ShapeViolation::field).toList())
                .containsExactly("newBuildingSumInsured");
        assertThat(parser.checkProduct("HOUSEHOLD", single).stream().map(ShapeViolation::field).toList())
                .contains("newSumInsured");
    }

    @Test
    @DisplayName("AC-06.1: the product check has nothing to say about an address change or a nominee")
    void productCheckIgnoresOtherTypes() {
        EndorsementRequest address = parser.parse(map("type", "CHANGE_ADDRESS", "address", goodAddress())).request();
        EndorsementRequest nominee = parser.parse(map("type", "ADD_NOMINEE", "nominee", goodNominee())).request();

        for (String product : List.of("TERM_LIFE", "MOTOR", "HOUSEHOLD")) {
            assertThat(parser.checkProduct(product, address)).isEmpty();
            assertThat(parser.checkProduct(product, nominee)).isEmpty();
        }
    }
}
