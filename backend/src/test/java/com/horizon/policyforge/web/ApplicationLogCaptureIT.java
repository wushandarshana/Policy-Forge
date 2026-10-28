package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationFixtures.addressOf;
import static com.horizon.policyforge.support.ApplicationFixtures.application;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * NFR-03.1 (P-7): no captured log line holds an Aadhaar, a PAN, health declaration text, a full name or a date of birth.
 * Our packages and Spring MVC's web logger run at DEBUG, so a type that prints PII from its string form, or a framework
 * line that echoes a request body, would show up. The capture covers the quote and a valid TERM_LIFE submission that is
 * referred because of a health declaration, and a rejected invalid-KYC submission (framework error logging can echo a
 * body).
 */
@Tag("NFR-03")
@SpringBootTest(properties = {
    "logging.level.com.horizon.policyforge=DEBUG",
    "logging.level.org.springframework.web=DEBUG",
})
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ApplicationLogCaptureIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MutableClock clock;

    private static void assertNoPii(String logged, String... forbidden) {
        assertThat(logged).as("captured log text").isNotBlank();
        for (String value : forbidden) {
            assertThat(logged).as("log line must not contain a submitted value").doesNotContain(value);
        }
    }

    @Test
    @DisplayName("NFR-03.1: a valid TERM_LIFE submission with Aadhaar XXXX-XXXX-1234, PAN ABCDE1234F and a health declaration is referred, and no captured log line contains any of them, the full name or the date of birth")
    void validSubmissionLogsIdsOnly(CapturedOutput output) throws Exception {
        clock.setDate(FixedClockConfig.TODAY);
        ApplicationApi api = new ApplicationApi(mvc, json);
        LocalDate dob = LocalDate.now(clock).minusYears(35);
        int offset = output.getAll().length();
        String quoteId = api.quoteId("TERM_LIFE",
                termLifeInputs(35, false, "5000000.00", "27.3189", List.of(ApplicationFixtures.HEALTH_TEXT)));

        JsonNode created = api.applyOk("customer1", quoteId, kyc(dob));

        assertThat(created.get("status").asText()).isEqualTo("REFERRED");
        String applicationId = created.get("applicationId").asText();
        String logged = output.getAll().substring(offset);
        assertThat(logged).as("the application ID is logged").contains(applicationId);
        assertNoPii(logged, ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN, ApplicationFixtures.HEALTH_TEXT,
                ApplicationFixtures.FULL_NAME, dob.toString(), "27.3189");
    }

    @Test
    @DisplayName("NFR-03.1: a rejected invalid-KYC submission logs none of the submitted values, not even the invalid ones")
    void rejectedSubmissionLogsNoSubmittedValues(CapturedOutput output) throws Exception {
        clock.setDate(FixedClockConfig.TODAY);
        ApplicationApi api = new ApplicationApi(mvc, json);
        LocalDate dob = LocalDate.now(clock).minusYears(35);
        int offset = output.getAll().length();
        String quoteId = api.quoteId("TERM_LIFE", termLifeInputs(35, false, "5000000.00", "24", List.of()));
        Map<String, Object> badKyc = kyc(dob);
        badKyc.put("fullName", "Rejected Person");
        badKyc.put("pan", "zzzzz9999z");
        badKyc.put("aadhaar", "9999-8888");
        addressOf(badKyc).put("postcode", "99");

        MvcResult result = api.apply("customer1", application(quoteId, badKyc));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String logged = output.getAll().substring(offset);
        assertNoPii(logged, "zzzzz9999z", "9999-8888", "Rejected Person", dob.toString(),
                ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN);
    }
}
