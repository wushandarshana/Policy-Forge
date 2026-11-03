package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.EndorsementApi.householdBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.EndorsementRows;
import com.horizon.policyforge.support.PolicyRows;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Sum-insured endorsements re-rate with the policy's own rule version and pro-rate the difference (AC-06.2). Every
 * expected figure is the independent oracle's (premium_check.py): TERM_LIFE 6850.00 to 9490.00 effective 2027-05-01 is
 * 1330.85 (184 of 365 days), the reverse change is -1330.85, a change on the term start is 2640.00, MOTOR 15150.00 to
 * 17025.00 is 945.21, and HOUSEHOLD 11350.00 to 12700.00 (building 7,000,000.00) is 680.55 and to 14500.00 (contents
 * 2,000,000.00) is 1587.95. The clock stands at the issue date, 2026-11-01.
 */
@Tag("AC-06")
class EndorsementRatingIT extends EndorsementTestSupport {

    private static void assertMoney(Object stored, String expected) {
        assertThat((BigDecimal) stored).isEqualByComparingTo(expected);
        assertThat(((BigDecimal) stored).scale()).as("final amounts are stored at scale 2").isEqualTo(2);
    }

    @Test
    @DisplayName("AC-06.2: on a TERM_LIFE policy (6850.00, 2026-11-01 to 2027-10-31), raising the sum insured from 5,000,000.00 to 7,000,000.00 effective 2027-05-01 stores new premium 9490.00 and adjustment 1330.85, and version 2 has sum insured 7000000.00 and premium 9490.00")
    void termLifeSumInsuredIncreaseIsReRatedAndProRated() throws Exception {
        String number = issued("TERM_LIFE");

        JsonNode created = endorsements.endorseOk(number, sumInsuredBody("7000000.00", "2027-05-01"));

        JsonNode endorsement = created.get("endorsement");
        assertThat(endorsement.get("type").asText()).isEqualTo("CHANGE_SUM_INSURED");
        assertThat(endorsement.get("newAnnualPremium").asText()).isEqualTo("9490.00");
        assertThat(endorsement.get("adjustment").asText()).isEqualTo("1330.85");
        assertThat(endorsement.get("changes").get("newSumInsured").asText()).isEqualTo("7000000.00");
        assertThat(created.get("policy").get("sumInsured").asText()).isEqualTo("7000000.00");
        assertThat(created.get("policy").get("annualPremium").asText()).isEqualTo("9490.00");
        List<Map<String, Object>> rows = EndorsementRows.endorsements(jdbc, number);
        assertThat(rows).hasSize(1);
        assertMoney(rows.get(0).get("NEW_ANNUAL_PREMIUM"), "9490.00");
        assertMoney(rows.get(0).get("ADJUSTMENT"), "1330.85");
        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        assertMoney(versions.get(1).get("SUM_INSURED"), "7000000.00");
        assertMoney(versions.get(1).get("ANNUAL_PREMIUM"), "9490.00");
        assertThat(versions.get(1).get("EFFECTIVE_DATE").toString()).isEqualTo("2027-05-01");
        assertThat(((Number) versions.get(1).get("RULE_VERSION")).intValue()).as("the policy's own rule version")
                .isEqualTo(((Number) versions.get(0).get("RULE_VERSION")).intValue());
        assertMoney(versions.get(0).get("ANNUAL_PREMIUM"), "6850.00");
        assertMoney(PolicyRows.terms(jdbc, number).get(0).get("PREMIUM"), "6850.00");
        JsonNode riskInputs = detail(number).get("versions").get(1).get("riskInputs");
        assertThat(riskInputs.get("sumInsured").asText()).isEqualTo("7000000.00");
        assertThat(riskInputs.get("age").asInt()).as("the other risk inputs are carried over").isEqualTo(35);
        assertThat(riskInputs.has("bmi")).as("health inputs are never returned").isFalse();
    }

    @Test
    @DisplayName("AC-06.2: the reverse change from 7,000,000.00 back to 5,000,000.00 effective 2027-05-01 gives a credit of -1330.85, and an endorsement effective on the term start gives 2640.00")
    void reverseChangeIsACreditAndTermStartIsTheFullDifference() throws Exception {
        String number = issued("TERM_LIFE");
        endorsements.endorseOk(number, sumInsuredBody("7000000.00", "2027-05-01"));

        JsonNode back = endorsements.endorseOk(number, sumInsuredBody("5000000.00", "2027-05-01"));

        assertThat(back.get("endorsement").get("newAnnualPremium").asText()).isEqualTo("6850.00");
        assertThat(back.get("endorsement").get("adjustment").asText()).isEqualTo("-1330.85");
        assertThat(back.get("endorsement").get("beforeVersionNo").asInt()).isEqualTo(2);
        assertThat(back.get("endorsement").get("afterVersionNo").asInt()).isEqualTo(3);
        assertMoney(EndorsementRows.endorsements(jdbc, number).get(1).get("ADJUSTMENT"), "-1330.85");

        String second = issued("TERM_LIFE");
        JsonNode full = endorsements.endorseOk(second, sumInsuredBody("7000000.00", TERM_START));
        assertThat(full.get("endorsement").get("adjustment").asText()).isEqualTo("2640.00");
        assertThat(full.get("endorsement").get("newAnnualPremium").asText()).isEqualTo("9490.00");
    }

    @Test
    @DisplayName("AC-06.2: on a MOTOR policy the change replaces vehicleValue: 800000.00 to 900000.00 re-rates 15150.00 to 17025.00 with adjustment 945.21 effective 2027-05-01")
    void motorVehicleValueIsReplaced() throws Exception {
        String number = issued("MOTOR");

        JsonNode created = endorsements.endorseOk(number, sumInsuredBody("900000.00", "2027-05-01"));

        assertThat(created.get("endorsement").get("newAnnualPremium").asText()).isEqualTo("17025.00");
        assertThat(created.get("endorsement").get("adjustment").asText()).isEqualTo("945.21");
        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        assertMoney(versions.get(0).get("ANNUAL_PREMIUM"), "15150.00");
        assertMoney(versions.get(1).get("ANNUAL_PREMIUM"), "17025.00");
        assertMoney(versions.get(1).get("SUM_INSURED"), "900000.00");
        JsonNode riskInputs = detail(number).get("versions").get(1).get("riskInputs");
        assertThat(riskInputs.get("vehicleValue").asText()).isEqualTo("900000.00");
        assertThat(riskInputs.get("driverAge").asInt()).isEqualTo(30);
    }

    @Test
    @DisplayName("AC-06.2: on a HOUSEHOLD policy the change replaces the supplied components only: building 7,000,000.00 re-rates 11350.00 to 12700.00 (adjustment 680.55), and contents 2,000,000.00 alone re-rates to 14500.00 (adjustment 1587.95)")
    void householdComponentsAreReplaced() throws Exception {
        String buildingPolicy = issued("HOUSEHOLD");
        String contentsPolicy = issued("HOUSEHOLD");

        JsonNode building = endorsements.endorseOk(buildingPolicy, householdBody("7000000.00", null, "2027-05-01"));
        JsonNode contents = endorsements.endorseOk(contentsPolicy, householdBody(null, "2000000.00", "2027-05-01"));

        assertThat(building.get("endorsement").get("newAnnualPremium").asText()).isEqualTo("12700.00");
        assertThat(building.get("endorsement").get("adjustment").asText()).isEqualTo("680.55");
        assertThat(building.get("endorsement").get("changes").get("newBuildingSumInsured").asText())
                .isEqualTo("7000000.00");
        assertThat(building.get("endorsement").get("changes").has("newContentsSumInsured")).isFalse();
        assertThat(contents.get("endorsement").get("newAnnualPremium").asText()).isEqualTo("14500.00");
        assertThat(contents.get("endorsement").get("adjustment").asText()).isEqualTo("1587.95");
        assertMoney(PolicyRows.versions(jdbc, buildingPolicy).get(1).get("SUM_INSURED"), "8000000.00");
        assertMoney(PolicyRows.versions(jdbc, contentsPolicy).get(1).get("SUM_INSURED"), "8000000.00");
        JsonNode riskInputs = detail(buildingPolicy).get("versions").get(1).get("riskInputs");
        assertThat(riskInputs.get("buildingSumInsured").asText()).isEqualTo("7000000.00");
        assertThat(riskInputs.get("contentsSumInsured").asText()).as("the other component is kept")
                .isEqualTo("1000000.00");
    }

    @Test
    @DisplayName("AC-06.2: a new sum insured outside the rule version's limits gets 400 SUM_INSURED_OUT_OF_RANGE and one equal to the current value gets SUM_INSURED_UNCHANGED, both naming the field, and nothing is written")
    void sumInsuredMustBeInRangeAndChange() throws Exception {
        String termLife = issued("TERM_LIFE");
        String motor = issued("MOTOR");
        EndorsementRows.Snapshot before = snapshot(termLife);

        MvcResult tooHigh = endorsements.endorse("customer1", termLife, sumInsuredBody("50000001.00", null));
        MvcResult tooLow = endorsements.endorse("customer1", termLife, sumInsuredBody("499999.99", null));
        MvcResult unchanged = endorsements.endorse("customer1", termLife, sumInsuredBody("5000000", null));
        MvcResult motorTooHigh = endorsements.endorse("customer1", motor, sumInsuredBody("10000001.00", null));
        MvcResult motorUnchanged = endorsements.endorse("customer1", motor, sumInsuredBody("800000.00", null));

        for (MvcResult outOfRange : List.of(tooHigh, tooLow, motorTooHigh)) {
            JsonNode problem = problem(outOfRange, 400);
            assertThat(problem.get("code").asText()).isEqualTo("SUM_INSURED_OUT_OF_RANGE");
            assertThat(errorFields(problem)).containsExactly("newSumInsured");
        }
        for (MvcResult same : List.of(unchanged, motorUnchanged)) {
            JsonNode problem = problem(same, 400);
            assertThat(problem.get("code").asText()).isEqualTo("SUM_INSURED_UNCHANGED");
            assertThat(errorFields(problem)).containsExactly("newSumInsured");
        }
        assertThat(snapshot(termLife)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-06.2: a HOUSEHOLD change that supplies the current value of every component gets SUM_INSURED_UNCHANGED, and a combined total over the maximum gets SUM_INSURED_OUT_OF_RANGE")
    void householdChecksUseTheComponentsAndTheirTotal() throws Exception {
        String number = issued("HOUSEHOLD");
        EndorsementRows.Snapshot before = snapshot(number);

        MvcResult unchanged = endorsements.endorse("customer1", number,
                householdBody("6000000.00", "1000000.00", null));
        MvcResult tooMuch = endorsements.endorse("customer1", number, householdBody("49500000.00", null, null));

        JsonNode same = problem(unchanged, 400);
        assertThat(same.get("code").asText()).isEqualTo("SUM_INSURED_UNCHANGED");
        assertThat(errorFields(same)).containsExactly("newBuildingSumInsured");
        JsonNode range = problem(tooMuch, 400);
        assertThat(range.get("code").asText()).isEqualTo("SUM_INSURED_OUT_OF_RANGE");
        assertThat(errorFields(range)).containsExactly("newBuildingSumInsured");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-06.2: the policy list and detail show the current version's sum insured and premium and the endorsement count after a sum-insured change")
    void headersFollowTheCurrentVersion() throws Exception {
        String number = issued("TERM_LIFE");
        endorsements.endorseOk(number, sumInsuredBody("7000000.00", "2027-05-01"));
        endorsements.endorseOk(number, newAddress());

        JsonNode listed = null;
        for (JsonNode item : policies.bodyOf(policies.mine("customer1"))) {
            if (number.equals(item.get("policyNumber").asText())) {
                listed = item;
            }
        }

        assertThat(listed).as("the policy is in the caller's list").isNotNull();
        assertThat(listed.get("status").asText()).isEqualTo("ENDORSED");
        assertThat(listed.get("sumInsured").asText()).isEqualTo("7000000.00");
        assertThat(listed.get("annualPremium").asText()).isEqualTo("9490.00");
        assertThat(listed.get("endorsementCount").asInt()).isEqualTo(2);
        assertThat(listed.get("nextPremiumDueDate").asText()).isEqualTo("2027-11-01");
        JsonNode header = detail(number).get("policy");
        assertThat(header.get("sumInsured").asText()).isEqualTo("7000000.00");
        assertThat(header.get("annualPremium").asText()).isEqualTo("9490.00");
        assertThat(header.get("endorsementCount").asInt()).isEqualTo(2);
        assertThat(header.get("currentVersionNo").asInt()).isEqualTo(3);
    }

    private static Map<String, Object> newAddress() {
        return com.horizon.policyforge.support.EndorsementApi.newAddressBody("2027-05-01");
    }
}
