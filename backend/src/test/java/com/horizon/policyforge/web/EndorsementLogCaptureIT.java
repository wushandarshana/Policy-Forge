package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.decisionBody;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static com.horizon.policyforge.support.EndorsementApi.NEW_LINE1;
import static com.horizon.policyforge.support.EndorsementApi.NOMINEE_NAME;
import static com.horizon.policyforge.support.EndorsementApi.addressBody;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static com.horizon.policyforge.support.EndorsementApi.nomineeBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.support.ApplicationFixtures;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * NFR-03.1 for endorsements: no captured log line holds a nominee name, an address line, an Aadhaar, a PAN, health
 * declaration text, the full name or the date of birth. Our packages and Spring MVC's web logger run at DEBUG, so a type
 * that prints PII from its string form, or a framework line that echoes a request or response body, would show up. The
 * policy is issued from a quote with a BMI and a health declaration (approved by an underwriter), and the endorsement
 * requests and the policy detail carry addresses and a nominee name, so each of those would be caught.
 */
@Tag("NFR-03")
@SpringBootTest(properties = {
    "logging.level.com.horizon.policyforge=DEBUG",
    "logging.level.org.springframework.web=DEBUG",
})
@ExtendWith(OutputCaptureExtension.class)
class EndorsementLogCaptureIT extends EndorsementTestSupport {

    private static void assertNoPii(String logged, String... forbidden) {
        assertThat(logged).as("captured log text").isNotBlank();
        for (String value : forbidden) {
            assertThat(logged).as("log line must not contain a submitted value").doesNotContain(value);
        }
    }

    /** An ACTIVE policy whose quote holds a BMI and a health declaration, approved by underwriter1. */
    private String issuedWithHealthInputs() throws Exception {
        LocalDate today = LocalDate.now(clock);
        String quoteId = applications.quoteId("TERM_LIFE",
                termLifeInputs(35, false, "5000000.00", "27.3189", List.of(ApplicationFixtures.HEALTH_TEXT)));
        String applicationId = applications.applyOk("customer1", quoteId,
                ApplicationFixtures.kyc(today.minusYears(35))).get("applicationId").asText();
        MvcResult decision = applications.decide("underwriter1", applicationId,
                decisionBody("APPROVE", "UW-MAN-001", "Acceptable after medical review"));
        assertThat(decision.getResponse().getStatus()).isEqualTo(200);
        return policies.issueOk("customer1", applicationId, today.toString()).get("policyNumber").asText();
    }

    @Test
    @DisplayName("NFR-03.1: endorsing with all three types, previewing and reading the policy back log IDs only, and no captured line contains the nominee name, an address line, an Aadhaar, a PAN, a health declaration, the full name or the date of birth")
    void endorseAndPreviewLogIdsOnly(CapturedOutput output) throws Exception {
        String number = issuedWithHealthInputs();
        int offset = output.getAll().length();

        MvcResult address = endorsements.endorse("customer1", number, newAddressBody(null));
        MvcResult nominee = endorsements.endorse("customer1", number, nomineeBody(NOMINEE_NAME, "SPOUSE", 40, null));
        MvcResult sumInsured = endorsements.endorse("customer1", number, sumInsuredBody("7000000.00", null));
        MvcResult preview = endorsements.preview("customer1", number, sumInsuredBody("6000000.00", null));
        MvcResult detail = policies.detail("customer1", number);
        MvcResult adminDetail = policies.detail("admin1", number);

        assertThat(address.getResponse().getStatus()).as(address.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(nominee.getResponse().getStatus()).as(nominee.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(sumInsured.getResponse().getStatus()).as(sumInsured.getResponse().getContentAsString())
                .isEqualTo(201);
        assertThat(preview.getResponse().getStatus()).as(preview.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        assertThat(adminDetail.getResponse().getStatus()).isEqualTo(200);
        assertThat(detail.getResponse().getContentAsString()).as("the responses do carry the data (the capture is real)")
                .contains(NOMINEE_NAME).contains(NEW_LINE1);
        String logged = output.getAll().substring(offset);
        assertThat(logged).as("the policy number is logged").contains(number);
        assertNoPii(logged, NOMINEE_NAME, NEW_LINE1, "Mumbai", ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN,
                ApplicationFixtures.HEALTH_TEXT, ApplicationFixtures.FULL_NAME,
                LocalDate.now(clock).minusYears(35).toString(), "27.3189", ApplicationFixtures.LINE1);
    }

    @Test
    @DisplayName("NFR-03.1: rejected endorsement and preview requests (shape errors, rule violations, a refused state, another customer's policy, a wrong content type) log none of the submitted values")
    void rejectedRequestsLogNoSubmittedValues(CapturedOutput output) throws Exception {
        String number = issuedWithHealthInputs();
        String cancelled = issuedWithHealthInputs();
        states.move(cancelled, PolicyStatus.CANCELLED, "TEST_SETUP");
        int offset = output.getAll().length();

        Map<String, Object> unknownKey = new LinkedHashMap<>(newAddressBody(null));
        unknownKey.put("SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA");
        MvcResult shape = endorsements.endorse("customer1", number, unknownKey);
        MvcResult badPostcode = endorsements.endorse("customer1", number,
                addressBody("SYNTHETIC_LINE_ALPHA", "SYNTHETIC_CITY_ALPHA", "SYNTHETIC", null));
        MvcResult badRelationship = endorsements.preview("customer1", number,
                nomineeBody("SYNTHETIC_NAME_DELTA", "SYNTHETIC_RELATION", 40, null));
        MvcResult tooMuch = endorsements.endorse("customer1", number,
                nomineeBody("SYNTHETIC_NAME_EPSILON", "SPOUSE", 101, null));
        MvcResult refusedState = endorsements.endorse("customer1", cancelled,
                nomineeBody("SYNTHETIC_NAME_ZETA", "SPOUSE", 10, null));
        MvcResult otherCustomer = endorsements.endorse("customer2", number,
                nomineeBody("SYNTHETIC_NAME_ETA", "SPOUSE", 10, null));
        MvcResult wrongType = endorsements.endorseRaw("customer1", number, "text/plain", "SYNTHETIC_PLAIN_THETA");

        assertThat(shape.getResponse().getStatus()).isEqualTo(400);
        assertThat(badPostcode.getResponse().getStatus()).isEqualTo(400);
        assertThat(badRelationship.getResponse().getStatus()).isEqualTo(400);
        assertThat(tooMuch.getResponse().getStatus()).isEqualTo(400);
        assertThat(refusedState.getResponse().getStatus()).isEqualTo(409);
        assertThat(otherCustomer.getResponse().getStatus()).isEqualTo(404);
        assertThat(wrongType.getResponse().getStatus()).isEqualTo(415);
        String logged = output.getAll().substring(offset);
        assertNoPii(logged, "SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA", "SYNTHETIC_LINE_ALPHA",
                "SYNTHETIC_CITY_ALPHA", "SYNTHETIC_NAME_DELTA", "SYNTHETIC_RELATION", "SYNTHETIC_NAME_EPSILON",
                "SYNTHETIC_NAME_ZETA", "SYNTHETIC_NAME_ETA", "SYNTHETIC_PLAIN_THETA", ApplicationFixtures.AADHAAR,
                ApplicationFixtures.PAN, ApplicationFixtures.FULL_NAME, ApplicationFixtures.LINE1);
        JsonNode problem = endorsements.bodyOf(refusedState);
        assertThat(problem.get("code").asText()).isEqualTo("INVALID_POLICY_STATE");
    }
}
