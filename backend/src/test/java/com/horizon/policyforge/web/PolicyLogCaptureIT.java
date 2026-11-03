package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.decisionBody;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static com.horizon.policyforge.support.PolicyApi.issueBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.PolicyApi;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * NFR-03.1 for issuance and the policy views (P-7): no captured log line holds an Aadhaar, a PAN, health declaration
 * text, a full name, a date of birth or an address line. Our packages and Spring MVC's web logger run at DEBUG, so a type
 * that prints PII from its string form, or a framework line that echoes a request or response body, would show up. The
 * policy is issued from a quote with a BMI and a health declaration (approved by an underwriter), and the policy detail
 * response carries the insured address, so each of those would be caught.
 */
@Tag("NFR-03")
@SpringBootTest(properties = {
    "logging.level.com.horizon.policyforge=DEBUG",
    "logging.level.org.springframework.web=DEBUG",
})
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class PolicyLogCaptureIT {

    private static final LocalDate ISSUE_DAY = LocalDate.parse("2026-11-01");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MutableClock clock;

    private ApplicationApi api;
    private PolicyApi policies;

    @BeforeEach
    void setUp() {
        clock.setDate(ISSUE_DAY);
        api = new ApplicationApi(mvc, json);
        policies = new PolicyApi(mvc, json);
    }

    @AfterEach
    void resetClock() {
        clock.setDate(FixedClockConfig.TODAY);
    }

    private static void assertNoPii(String logged, String... forbidden) {
        assertThat(logged).as("captured log text").isNotBlank();
        for (String value : forbidden) {
            assertThat(logged).as("log line must not contain a submitted value").doesNotContain(value);
        }
    }

    /** An APPROVED application whose quote holds a BMI and a health declaration, approved by underwriter1. */
    private String approvedWithHealthInputs() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE",
                termLifeInputs(35, false, "5000000.00", "27.3189", List.of(ApplicationFixtures.HEALTH_TEXT)));
        String applicationId = api.applyOk("customer1", quoteId, ApplicationFixtures.kyc(ISSUE_DAY.minusYears(35)))
                .get("applicationId").asText();
        MvcResult decision = api.decide("underwriter1", applicationId,
                decisionBody("APPROVE", "UW-MAN-001", "Acceptable after medical review"));
        assertThat(decision.getResponse().getStatus()).isEqualTo(200);
        return applicationId;
    }

    @Test
    @DisplayName("NFR-03.1: issuing a policy and reading it back (list, owner detail, admin detail) log IDs only, and no captured line contains an Aadhaar, a PAN, a health declaration, the full name, the date of birth or the address")
    void issueAndReadLogIdsOnly(CapturedOutput output) throws Exception {
        String applicationId = approvedWithHealthInputs();
        int offset = output.getAll().length();

        JsonNode issued = policies.issueOk("customer1", applicationId, "2026-11-01");
        String number = issued.get("policyNumber").asText();
        MvcResult mine = policies.mine("customer1");
        MvcResult detail = policies.detail("customer1", number);
        MvcResult adminDetail = policies.detail("admin1", number);

        assertThat(mine.getResponse().getStatus()).isEqualTo(200);
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        assertThat(adminDetail.getResponse().getStatus()).isEqualTo(200);
        assertThat(detail.getResponse().getContentAsString()).as("the response does carry the address (the capture is real)")
                .contains(ApplicationFixtures.LINE1);
        String logged = output.getAll().substring(offset);
        assertThat(logged).as("the policy number is logged").contains(number);
        assertThat(logged).as("the application ID is logged").contains(applicationId);
        assertNoPii(logged, ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN, ApplicationFixtures.HEALTH_TEXT,
                ApplicationFixtures.FULL_NAME, ISSUE_DAY.minusYears(35).toString(), "27.3189",
                ApplicationFixtures.LINE1, ApplicationFixtures.CITY);
    }

    @Test
    @DisplayName("NFR-03.1: rejected issue requests (bad date, unknown key, wrong state, another customer's application, wrong content type) log none of the submitted values")
    void rejectedIssuesLogNoSubmittedValues(CapturedOutput output) throws Exception {
        String approved = api.approvedApplication("customer1", ISSUE_DAY);
        String referred = api.referredApplication("customer1", ISSUE_DAY);
        int offset = output.getAll().length();

        Map<String, Object> badDate = new LinkedHashMap<>();
        badDate.put("effectiveDate", "SYNTHETIC-DATE-ALPHA");
        MvcResult format = policies.issue("customer1", approved, badDate);
        MvcResult outOfRange = policies.issue("customer1", approved, issueBody("2031-01-01"));
        Map<String, Object> unknownKey = new LinkedHashMap<>();
        unknownKey.put("SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA");
        MvcResult unknown = policies.issue("customer1", approved, unknownKey);
        MvcResult wrongState = policies.issue("customer1", referred, issueBody(null));
        MvcResult otherCustomer = policies.issue("customer2", approved, issueBody(null));
        MvcResult wrongType = policies.issueRaw("customer1", approved, "text/plain", "SYNTHETIC_PLAIN_DELTA");
        MvcResult notFound = policies.detail("customer2", "PF-TL-2026-000001");

        assertThat(format.getResponse().getStatus()).isEqualTo(400);
        assertThat(outOfRange.getResponse().getStatus()).isEqualTo(400);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(400);
        assertThat(wrongState.getResponse().getStatus()).isEqualTo(409);
        assertThat(otherCustomer.getResponse().getStatus()).isEqualTo(404);
        assertThat(wrongType.getResponse().getStatus()).isEqualTo(415);
        assertThat(notFound.getResponse().getStatus()).isEqualTo(404);
        String logged = output.getAll().substring(offset);
        assertNoPii(logged, "SYNTHETIC-DATE-ALPHA", "SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA",
                "SYNTHETIC_PLAIN_DELTA", ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN,
                ApplicationFixtures.FULL_NAME, ApplicationFixtures.LINE1);
    }
}
