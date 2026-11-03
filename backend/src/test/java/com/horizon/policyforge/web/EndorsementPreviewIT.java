package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.EndorsementApi.PREVIEW_FIELDS;
import static com.horizon.policyforge.support.EndorsementApi.addressBody;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static com.horizon.policyforge.support.EndorsementApi.nomineeBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.EndorsementRows;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The endorsement preview (plan G-1, contract C5): {@code POST /api/policies/{number}/endorsements/preview} takes the
 * body of an endorsement, runs the same checks in the same order and writes nothing. The figures are the AC-06.2
 * oracle's: 6850.00 to 9490.00 effective 2027-05-01 is 1330.85 over 184 of 365 days, and effective on the last day of
 * the term (2027-10-31) it is 7.23 over 1 day.
 */
@Tag("AC-06")
class EndorsementPreviewIT extends EndorsementTestSupport {

    @Test
    @DisplayName("AC-06.2: the preview of 5,000,000.00 to 7,000,000.00 effective 2027-05-01 returns 9490.00, 1330.85, 184 and 365 and writes nothing, and confirming the same request stores the same values")
    void previewMatchesTheConfirmedEndorsement() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);
        Map<String, Object> request = sumInsuredBody("7000000.00", "2027-05-01");

        MvcResult result = endorsements.preview("customer1", number, request);

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        JsonNode preview = endorsements.bodyOf(result);
        assertThat(fieldNames(preview)).as("the C5 key set, and nothing else").isEqualTo(PREVIEW_FIELDS);
        assertThat(preview.get("type").asText()).isEqualTo("CHANGE_SUM_INSURED");
        assertThat(preview.get("effectiveDate").asText()).isEqualTo("2027-05-01");
        assertThat(preview.get("currentAnnualPremium").asText()).isEqualTo("6850.00");
        assertThat(preview.get("newAnnualPremium").asText()).isEqualTo("9490.00");
        assertThat(preview.get("adjustment").asText()).isEqualTo("1330.85");
        assertThat(preview.get("unexpiredDays").asInt()).isEqualTo(184);
        assertThat(preview.get("termDays").asInt()).isEqualTo(365);
        assertThat(snapshot(number)).as("all row counts, the status and the version are unchanged").isEqualTo(before);
        assertThat(EndorsementRows.endorsements(jdbc, number)).isEmpty();

        JsonNode confirmed = endorsements.endorseOk(number, request);
        assertThat(confirmed.get("endorsement").get("newAnnualPremium").asText())
                .isEqualTo(preview.get("newAnnualPremium").asText());
        assertThat(confirmed.get("endorsement").get("adjustment").asText())
                .isEqualTo(preview.get("adjustment").asText());
        assertThat(confirmed.get("endorsement").get("effectiveDate").asText())
                .isEqualTo(preview.get("effectiveDate").asText());
    }

    @Test
    @DisplayName("AC-06.2: the preview of a change effective on the last day of the term is 7.23 over 1 day, and of a change on the term start is 2640.00 over 365 days")
    void previewAtTheEndsOfTheTerm() throws Exception {
        String number = issued("TERM_LIFE");

        JsonNode last = endorsements.previewOk(number, sumInsuredBody("7000000.00", TERM_END));
        JsonNode first = endorsements.previewOk(number, sumInsuredBody("7000000.00", null));

        assertThat(last.get("adjustment").asText()).isEqualTo("7.23");
        assertThat(last.get("unexpiredDays").asInt()).isEqualTo(1);
        assertThat(first.get("adjustment").asText()).isEqualTo("2640.00");
        assertThat(first.get("effectiveDate").asText()).as("the default is today").isEqualTo(TERM_START);
        assertThat(first.get("unexpiredDays").asInt()).isEqualTo(365);
        assertThat(first.get("termDays").asInt()).isEqualTo(365);
    }

    @Test
    @DisplayName("AC-06.2: the preview of a type that does not re-rate shows the current premium as the new premium and an adjustment of 0.00, with the day counts, and writes nothing")
    void previewOfOtherTypesDoesNotChangeThePremium() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);

        JsonNode address = endorsements.previewOk(number, newAddressBody("2027-05-01"));
        JsonNode nominee = endorsements.previewOk(number, nomineeBody("Nominee One", "SPOUSE", 40, null));

        for (JsonNode preview : new JsonNode[] {address, nominee}) {
            assertThat(fieldNames(preview)).isEqualTo(PREVIEW_FIELDS);
            assertThat(preview.get("currentAnnualPremium").asText()).isEqualTo("6850.00");
            assertThat(preview.get("newAnnualPremium").asText()).isEqualTo("6850.00");
            assertThat(preview.get("adjustment").asText()).isEqualTo("0.00");
            assertThat(preview.get("termDays").asInt()).isEqualTo(365);
        }
        assertThat(address.get("type").asText()).isEqualTo("CHANGE_ADDRESS");
        assertThat(address.get("unexpiredDays").asInt()).isEqualTo(184);
        assertThat(nominee.get("type").asText()).isEqualTo("ADD_NOMINEE");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-06.2: the preview runs the same checks as the endorsement: a shape error, a bad postcode, an out-of-range or unchanged sum insured and an exceeded nominee share get the same 400 as the endorsement, and nothing is written")
    void previewRunsTheSameChecks() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);
        Map<String, Object> badPostcode = addressBody("9 Hill Lane", "Nagpur", "4400", null);
        Map<String, Object> tooHigh = sumInsuredBody("50000001.00", null);
        Map<String, Object> unchanged = sumInsuredBody("5000000.00", null);
        Map<String, Object> tooMuch = nomineeBody("Nominee One", "SPOUSE", 101, null);
        Map<String, Object> shape = Map.of("type", "CHANGE_ADDRESS");

        for (Map<String, Object> body : List.of(badPostcode, tooHigh, unchanged, tooMuch, shape)) {
            MvcResult preview = endorsements.preview("customer1", number, body);
            MvcResult endorse = endorsements.endorse("customer1", number, body);

            JsonNode previewProblem = problem(preview, 400);
            JsonNode endorseProblem = problem(endorse, 400);
            assertThat(previewProblem.get("errors")).as(body.toString()).isEqualTo(endorseProblem.get("errors"));
            assertThat(previewProblem.has("code")).isEqualTo(endorseProblem.has("code"));
            if (previewProblem.has("code")) {
                assertThat(previewProblem.get("code")).isEqualTo(endorseProblem.get("code"));
            }
        }
        assertThat(errorFields(problem(endorsements.preview("customer1", number, tooMuch), 400)))
                .containsExactly("nominee.sharePercent");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-06.2: the preview of an unknown, malformed or another customer's policy is 404 with the fixed detail, and a shape error is 400 first")
    void previewHonoursOwnership() throws Exception {
        String number = issued("TERM_LIFE");

        MvcResult unknown = endorsements.preview("customer1", "PF-TL-2026-999999", newAddressBody(null));
        MvcResult malformed = endorsements.preview("customer1", "NOT-A-POLICY", newAddressBody(null));
        MvcResult otherCustomer = endorsements.preview("customer2", number, newAddressBody(null));
        MvcResult shape = endorsements.preview("customer2", number, Map.of());

        for (MvcResult notFound : List.of(unknown, malformed, otherCustomer)) {
            assertThat(problem(notFound, 404).get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        }
        assertThat(errorFields(problem(shape, 400))).containsExactly("type");
    }
}
