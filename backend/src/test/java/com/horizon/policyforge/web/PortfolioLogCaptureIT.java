package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.horizon.policyforge.support.DashboardApi;
import com.horizon.policyforge.support.FixedClockConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * NFR-03.1 for the dashboard and the portfolio seed: no captured log line holds a seed KYC value (full name, date of
 * birth, Aadhaar, PAN) or an address line. This class has its own application context, so the seed runs while the
 * capture is on, and the capture covers the startup as well as the requests. Our packages and Spring MVC's web logger
 * run at DEBUG, so a type that prints PII from its string form would show up.
 */
@Tag("NFR-03")
@SpringBootTest(properties = {
    "policyforge.test.context=portfolio-log-capture",
    "logging.level.com.horizon.policyforge=DEBUG",
    "logging.level.org.springframework.web=DEBUG",
})
@ExtendWith(OutputCaptureExtension.class)
class PortfolioLogCaptureIT extends DashboardTestSupport {

    private static final LocalDate T = FixedClockConfig.TODAY;

    /** The values the seed holds: its names, Aadhaar and PAN markers, address line and dates of birth. */
    private static List<String> seedValues() {
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            values.add("XXXX-XXXX-010" + i);
            values.add("ABCDE010" + i + "F");
            values.add("Seed Holder S" + i);
        }
        values.add("Seed Residency Road");
        values.add(T.minusYears(35).toString());
        values.add(T.minusYears(30).toString());
        values.add(T.minusYears(40).toString());
        return values;
    }

    private static void assertNoPii(String logged, List<String> forbidden) {
        assertThat(logged).as("captured log text").isNotBlank();
        for (String value : forbidden) {
            assertThat(logged).as("a log line must not contain a seed or submitted value").doesNotContain(value);
        }
    }

    @Test
    @DisplayName("NFR-03.1: the portfolio seed logs IDs only, so no captured startup line contains a seed full name, date of birth, Aadhaar, PAN or address line, while the policy numbers are logged")
    void theSeedLogsIdsOnly(CapturedOutput output) {
        String s1 = jdbc.queryForObject("select p.policy_number from policy p join application a"
                + " on a.application_id = p.application_id where a.kyc_aadhaar = 'XXXX-XXXX-0101'", String.class);
        String detail = jdbc.queryForObject("select kyc_full_name from application where kyc_aadhaar = 'XXXX-XXXX-0101'",
                String.class);

        assertThat(detail).as("the seed really holds the value the capture must not show").isEqualTo("Seed Holder S1");
        assertThat(output.getAll()).as("the seed's policy number is logged").contains(s1);
        assertNoPii(output.getAll(), seedValues());
    }

    @Test
    @DisplayName("NFR-03.1: reading the dashboard (as admin1, refused as customer1, anonymous, and with a malformed asOf) logs no seed value and none of the submitted text")
    void theDashboardLogsNoPersonalData(CapturedOutput output) throws Exception {
        int offset = output.getAll().length();

        MvcResult ok = dashboards.get("admin1", "2027-10-15");
        MvcResult malformed = dashboards.get("admin1", "SYNTHETIC_ASOF_ALPHA");
        MvcResult refused = dashboards.get("customer1", null);
        MvcResult anonymous = dashboards.get(null, null);
        MvcResult wrongPassword = mvc.perform(get(DashboardApi.PATH)
                .with(httpBasic("admin1", "SYNTHETIC_PASSWORD_BETA"))).andReturn();

        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
        assertThat(refused.getResponse().getStatus()).isEqualTo(403);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(ok.getResponse().getContentAsString()).as("the capture is real: the body carries policy numbers")
                .contains("PF-");
        List<String> forbidden = new ArrayList<>(seedValues());
        forbidden.addAll(List.of("SYNTHETIC_ASOF_ALPHA", "SYNTHETIC_PASSWORD_BETA"));
        assertNoPii(output.getAll().substring(offset), forbidden);
    }
}
