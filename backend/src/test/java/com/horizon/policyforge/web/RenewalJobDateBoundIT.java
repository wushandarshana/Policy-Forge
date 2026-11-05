package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalApi;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Bounded admin job date (AC-07.8, security review MAJOR-2). With {@code policyforge.jobs.renewal-cycle.any-date} off
 * (its default, set explicitly here because the test configuration turns it on for the other tests), a given
 * {@code date} must satisfy {@code today - 30 <= date <= today}, or the answer is 400 naming {@code date} and nothing
 * runs: no term, no transition and no audit entry. An absent date means today. This class has its own application
 * context (its own marker property), so the dates it runs never meet the other renewal tests' policies.
 */
@Tag("AC-07")
@Tag("AC-07.8")
@SpringBootTest(properties = {"policyforge.test.context=renewal-job-bound",
        "policyforge.jobs.renewal-cycle.any-date=false"})
class RenewalJobDateBoundIT extends RenewalTestSupport {

    /** Every table the job could write: terms, transitions and audit entries, across all policies. */
    private record Counts(int terms, int transitions, int auditEntries) {
    }

    private Counts counts() {
        return new Counts(jdbc.queryForObject("select count(*) from policy_term", Integer.class),
                jdbc.queryForObject("select count(*) from policy_status_transition", Integer.class),
                jdbc.queryForObject("select count(*) from audit_entry", Integer.class));
    }

    private void assertRefusedNamingDate(MvcResult result, String date) throws Exception {
        JsonNode problem = problem(result, 400);
        assertThat(errorFields(problem)).as(date).containsExactly("date");
        assertThat(result.getResponse().getContentAsString()).as("the detail never echoes the date")
                .doesNotContain(date);
    }

    @Test
    @DisplayName("AC-07.8: today 2027-10-01 and any-date off: date=2207-10-01 is 400 naming date, and no term, transition or audit entry is written, so a policy that the date would lapse stays in force without an offer")
    void aFarFutureDateIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");
        Counts before = counts();

        MvcResult result = renewals.job("admin1", "2207-10-01", Map.of());

        assertRefusedNamingDate(result, "2207-10-01");
        assertThat(counts()).as("nothing ran").isEqualTo(before);
        assertThat(PolicyRows.policy(jdbc, number).get("STATUS")).isEqualTo("ACTIVE");
        assertThat(PolicyRows.terms(jdbc, number)).hasSize(1);
    }

    @Test
    @DisplayName("AC-07.8: today 2027-10-01 and any-date off: date=2027-08-31 (today - 31) and date=2027-10-02 (tomorrow) are 400 naming date and write nothing")
    void justOutsideTheWindowIsRefused() throws Exception {
        at("2027-10-01");
        Counts before = counts();

        assertRefusedNamingDate(renewals.job("admin1", "2027-08-31", Map.of()), "2027-08-31");
        assertRefusedNamingDate(renewals.job("admin1", "2027-10-02", Map.of()), "2027-10-02");

        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-07.8: date=+999999999-12-31 is 400 naming date and writes nothing")
    void theLargestDateIsRefused() throws Exception {
        at("2027-10-01");
        Counts before = counts();

        MvcResult result = mvc.perform(post(RenewalApi.JOB_PATH).param("date", "+999999999-12-31")
                .with(httpBasic("admin1", ApplicationApi.PASSWORD)).contentType("application/json").content("{}"))
                .andReturn();

        JsonNode problem = problem(result, 400);
        assertThat(errorFields(problem)).containsExactly("date");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("999999999");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-07.8: today 2027-10-01 and any-date off: date=2027-09-01 (today - 30) and date=2027-10-01 (today) are accepted, and today's run offers the renewal")
    void theWindowEndsAreAccepted() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");

        JsonNode earliest = renewals.jobOk("2027-09-01");
        JsonNode today = renewals.jobOk("2027-10-01");

        assertThat(earliest.get("runDate").asText()).isEqualTo("2027-09-01");
        assertThat(today.get("runDate").asText()).isEqualTo("2027-10-01");
        assertThat(PolicyRows.terms(jdbc, number)).as("the 2027-10-01 run offered term 2").hasSize(2);
    }

    @Test
    @DisplayName("AC-07.8: an absent date means today (2027-10-01) and is accepted")
    void anAbsentDateIsToday() throws Exception {
        at("2027-10-01");

        assertThat(renewals.jobOk(null).get("runDate").asText()).isEqualTo("2027-10-01");
    }
}
