package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.decisionBody;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
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
 * NFR-03.1 for the renewal cycle and the renewal payment: no captured log line holds a KYC value (full name, date of
 * birth, Aadhaar, PAN), an address line or health declaration text. Our packages and Spring MVC's web logger run at
 * DEBUG, so a type that prints PII from its string form, or a framework line that echoes a request or response body,
 * would show up. The policy is issued from a quote with a BMI and a health declaration (approved by an underwriter), so
 * the stored risk inputs the cycle re-rates hold exactly the values that must never reach a log.
 */
@Tag("NFR-03")
@SpringBootTest(properties = {
    "policyforge.test.context=renewal-log-capture",
    "logging.level.com.horizon.policyforge=DEBUG",
    "logging.level.org.springframework.web=DEBUG",
})
@ExtendWith(OutputCaptureExtension.class)
class RenewalLogCaptureIT extends RenewalTestSupport {

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
    @DisplayName("NFR-03.1: running the cycle (offer and lapse), paying and reading the policy back log IDs only, and no captured line contains an Aadhaar, a PAN, the full name, the date of birth, an address line or health declaration text")
    void cycleAndPaymentLogIdsOnly(CapturedOutput output) throws Exception {
        String renewed = issuedWithHealthInputs();
        String lapsed = issuedWithHealthInputs();
        LocalDate issueDay = LocalDate.now(clock);
        int offset = output.getAll().length();

        renewals.jobOk("2027-10-01");
        at("2027-10-05");
        MvcResult paid = renewals.pay("customer1", renewed, new LinkedHashMap<>());
        MvcResult detail = policies.detail("customer1", renewed);
        at("2027-12-01");
        MvcResult lapse = renewals.job("admin1", "2027-12-01", Map.of());

        assertThat(paid.getResponse().getStatus()).as(paid.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        assertThat(lapse.getResponse().getStatus()).isEqualTo(200);
        assertThat(lapse.getResponse().getContentAsString()).contains(lapsed);
        assertThat(detail.getResponse().getContentAsString()).as("the responses do carry the data (the capture is real)")
                .contains(ApplicationFixtures.LINE1);
        String logged = output.getAll().substring(offset);
        assertThat(logged).as("the policy number is logged").contains(renewed);
        assertNoPii(logged, forbidden(issueDay));
    }

    @Test
    @DisplayName("NFR-03.1: rejected job and payment requests (unknown keys, a malformed date, a wrong content type, another customer's policy, a refused state) log none of the submitted values")
    void rejectedRequestsLogNoSubmittedValues(CapturedOutput output) throws Exception {
        String number = issuedWithHealthInputs();
        LocalDate issueDay = LocalDate.now(clock);
        int offset = output.getAll().length();

        Map<String, Object> unknownKey = new LinkedHashMap<>();
        unknownKey.put("SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA");
        MvcResult jobShape = renewals.job("admin1", "2027-10-01", unknownKey);
        MvcResult jobDate = renewals.job("admin1", "SYNTHETIC_DATE_DELTA", Map.of());
        MvcResult jobType = renewals.jobRaw("admin1", "2027-10-01", "text/plain", "SYNTHETIC_PLAIN_EPSILON");
        MvcResult payShape = renewals.pay("customer1", number, unknownKey);
        MvcResult payType = renewals.payRaw("customer1", number, "text/plain", "SYNTHETIC_PLAIN_ZETA");
        MvcResult otherCustomer = renewals.pay("customer2", number, Map.of());
        MvcResult nothingPending = renewals.pay("customer1", number, Map.of());

        assertThat(jobShape.getResponse().getStatus()).isEqualTo(400);
        assertThat(jobDate.getResponse().getStatus()).isEqualTo(400);
        assertThat(jobType.getResponse().getStatus()).isEqualTo(415);
        assertThat(payShape.getResponse().getStatus()).isEqualTo(400);
        assertThat(payType.getResponse().getStatus()).isEqualTo(415);
        assertThat(otherCustomer.getResponse().getStatus()).isEqualTo(404);
        assertThat(nothingPending.getResponse().getStatus()).isEqualTo(409);
        String logged = output.getAll().substring(offset);
        assertNoPii(logged, forbidden(issueDay, "SYNTHETIC_KEY_BETA", "SYNTHETIC_VALUE_GAMMA", "SYNTHETIC_DATE_DELTA",
                "SYNTHETIC_PLAIN_EPSILON", "SYNTHETIC_PLAIN_ZETA"));
    }
}
