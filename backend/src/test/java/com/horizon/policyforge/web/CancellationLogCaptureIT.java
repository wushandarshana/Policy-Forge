package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.decisionBody;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.support.ApplicationFixtures;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * NFR-03.1 for the refund preview and the cancellation: no captured log line holds a KYC value (full name, date of
 * birth, Aadhaar, PAN), an address line or health declaration text. Our packages and Spring MVC's web logger run at
 * DEBUG, so a type that prints PII from its string form, or a framework line that echoes a request or response body,
 * would show up. The policy is issued from a quote with a BMI and a health declaration (approved by an underwriter), so
 * the stored data holds exactly the values that must never reach a log.
 */
@Tag("NFR-03")
@SpringBootTest(properties = {
    "policyforge.test.context=cancellation-log-capture",
    "logging.level.com.horizon.policyforge=DEBUG",
    "logging.level.org.springframework.web=DEBUG",
})
@ExtendWith(OutputCaptureExtension.class)
class CancellationLogCaptureIT extends CancellationTestSupport {

    private static void assertNoPii(String logged, String... forbidden) {
        assertThat(logged).as("captured log text").isNotBlank();
        for (String value : forbidden) {
            assertThat(logged).as("log line must not contain a submitted or stored value").doesNotContain(value);
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

    private String[] forbidden(LocalDate today, String... extra) {
        List<String> values = new ArrayList<>(List.of(ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN,
                ApplicationFixtures.FULL_NAME, ApplicationFixtures.HEALTH_TEXT, ApplicationFixtures.LINE1,
                ApplicationFixtures.CITY, today.minusYears(35).toString(), "27.3189"));
        values.addAll(List.of(extra));
        return values.toArray(String[]::new);
    }

    @Test
    @DisplayName("NFR-03.1: previewing, cancelling and reading the policy back log IDs only, and no captured line contains an Aadhaar, a PAN, the full name, the date of birth, an address line or health declaration text")
    void previewAndCancelLogIdsOnly(CapturedOutput output) throws Exception {
        String number = issuedWithHealthInputs();
        LocalDate issueDay = LocalDate.now(clock);
        int offset = output.getAll().length();

        MvcResult preview = cancellations.preview("customer1", number, "2027-02-09");
        MvcResult cancel = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-02-09"));
        MvcResult detail = policies.detail("customer1", number);

        assertThat(preview.getResponse().getStatus()).as(preview.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(cancel.getResponse().getStatus()).as(cancel.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        assertThat(detail.getResponse().getContentAsString()).as("the responses do carry the data (the capture is real)")
                .contains(ApplicationFixtures.LINE1).contains("4473.29");
        String logged = output.getAll().substring(offset);
        assertThat(logged).as("the policy number is logged").contains(number);
        assertNoPii(logged, forbidden(issueDay));
    }

    @Test
    @DisplayName("NFR-03.1: rejected preview and cancel requests (unknown keys, a malformed date, a bad reason, a wrong content type, another customer's policy, a date outside the term, a second cancel) log none of the submitted values")
    void rejectedRequestsLogNoSubmittedValues(CapturedOutput output) throws Exception {
        String number = issuedWithHealthInputs();
        LocalDate issueDay = LocalDate.now(clock);
        int offset = output.getAll().length();

        Map<String, Object> unknownKey = new LinkedHashMap<>(cancelBody("NO_LONGER_NEEDED", null));
        unknownKey.put("SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA");
        MvcResult shape = cancellations.cancel("customer1", number, unknownKey);
        MvcResult badDate = cancellations.cancel("customer1", number,
                cancelBody("NO_LONGER_NEEDED", "SYNTHETIC_DATE_DELTA"));
        MvcResult badReason = cancellations.cancel("customer1", number, cancelBody("SYNTHETIC_REASON_EPSILON", null));
        MvcResult previewDate = cancellations.preview("customer1", number, "SYNTHETIC_DATE_ZETA");
        MvcResult wrongType = cancellations.cancelRaw("customer1", number, "text/plain", "SYNTHETIC_PLAIN_ETA");
        MvcResult otherCustomer = cancellations.cancel("customer2", number, cancelBody("NO_LONGER_NEEDED", null));
        MvcResult outsideTerm = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2099-01-01"));
        cancellations.cancelOk(number, "2027-02-09");
        MvcResult second = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", null));

        assertThat(shape.getResponse().getStatus()).isEqualTo(400);
        assertThat(badDate.getResponse().getStatus()).isEqualTo(400);
        assertThat(badReason.getResponse().getStatus()).isEqualTo(400);
        assertThat(previewDate.getResponse().getStatus()).isEqualTo(400);
        assertThat(wrongType.getResponse().getStatus()).isEqualTo(415);
        assertThat(otherCustomer.getResponse().getStatus()).isEqualTo(404);
        assertThat(outsideTerm.getResponse().getStatus()).isEqualTo(400);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        String logged = output.getAll().substring(offset);
        assertNoPii(logged, forbidden(issueDay, "SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA", "SYNTHETIC_DATE_DELTA",
                "SYNTHETIC_REASON_EPSILON", "SYNTHETIC_DATE_ZETA", "SYNTHETIC_PLAIN_ETA", "2099-01-01"));
    }
}
