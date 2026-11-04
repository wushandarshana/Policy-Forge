package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.CancellationApi;
import com.horizon.policyforge.support.DashboardApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The renewal pipeline and the lapse forecast (AC-11.3, AC-11.4, P-11). A TERM_LIFE policy issued on 2026-11-01 runs to
 * 2027-10-31 and its renewal offer is 6850.00. The dashboard's {@code asOf} moves the view, so the tests find their own
 * policy by number and never assume what else the context holds (P-15). Grace for TERM_LIFE is 30 days.
 */
@Tag("AC-11")
class PortfolioPipelineIT extends DashboardTestSupport {

    private static final String TERM_END = "2027-10-31";

    private JsonNode item(String asOf, String number) throws Exception {
        return DashboardApi.pipelineItem(dashboards.ok(asOf), number);
    }

    @Test
    @DisplayName("AC-11.3: a policy whose term ends in 20 days is in the pipeline with its term end, a null premium and paid false before the cycle, 6850.00 and false after the offer, and paid true after payment")
    void aTermEndingInTwentyDaysIsInThePipeline() throws Exception {
        String number = issued("TERM_LIFE");

        JsonNode before = item("2027-10-11", number);
        assertThat(before).as("20 days before the term end").isNotNull();
        assertThat(before.get("termEnd").asText()).isEqualTo(TERM_END);
        assertThat(before.get("renewalPremium").isNull()).as("no offer yet").isTrue();
        assertThat(before.get("paid").asBoolean()).isFalse();

        assertThat(renewals.jobOk("2027-10-01").get("offersCreated").toString()).contains(number);
        JsonNode offered = item("2027-10-11", number);
        assertThat(offered.get("renewalPremium").asText()).isEqualTo("6850.00");
        assertThat(offered.get("paid").asBoolean()).isFalse();
        assertThat(offered.get("termEnd").asText()).as("the paid term in force on asOf").isEqualTo(TERM_END);

        at("2027-10-11");
        renewals.payOk(number);
        JsonNode paid = item("2027-10-11", number);
        assertThat(paid.get("renewalPremium").asText()).isEqualTo("6850.00");
        assertThat(paid.get("paid").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("AC-11.3: a policy whose term ends in 90 days is not in the pipeline, 60 days is the last day in (2027-09-01) and 61 days (2027-08-31) is out")
    void ninetyDaysIsOutAndTheBoundaryIsSixtyInSixtyOneOut() throws Exception {
        String number = issued("TERM_LIFE");

        assertThat(item("2027-08-02", number)).as("90 days").isNull();
        assertThat(item("2027-08-31", number)).as("61 days").isNull();
        assertThat(item("2027-09-01", number)).as("60 days").isNotNull();
        assertThat(item("2027-10-31", number)).as("the term end itself").isNotNull();
    }

    @Test
    @DisplayName("AC-11.3: a policy is in the pipeline only while its paid term is in force on asOf: not before the term starts and not after it ends")
    void onlyWhileThePaidTermIsInForce() throws Exception {
        String number = issued("TERM_LIFE");

        assertThat(item("2026-10-31", number)).as("the day before the term starts").isNull();
        assertThat(item("2026-11-01", number)).as("the first day, but 364 days from the end").isNull();
        assertThat(item("2027-11-01", number)).as("the day after the term ends").isNull();
    }

    @Test
    @DisplayName("AC-11.3: a cancelled policy is not in the pipeline, because only in-force policies are")
    void aCancelledPolicyIsNotInThePipeline() throws Exception {
        String number = issued("TERM_LIFE");
        assertThat(item("2027-10-11", number)).isNotNull();

        new CancellationApi(mvc, json).cancelOk(number, "2027-02-09");

        assertThat(item("2027-10-11", number)).isNull();
    }

    @Test
    @DisplayName("AC-11.4: a policy past its term end with an unpaid renewal, within grace, is in the lapse forecast and the count grows by one; it is absent on the term end itself")
    void aPolicyInGraceIsInTheForecast() throws Exception {
        // The first cycle of the context may lapse seed policies that are already past their grace; run it first so the
        // count taken next is stable.
        renewals.jobOk("2027-10-01");
        int before = dashboards.ok("2027-11-05").get("lapseForecast").get("count").asInt();
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");

        JsonNode forecast = dashboards.ok("2027-11-05");

        assertThat(DashboardApi.forecastNumbers(forecast)).contains(number);
        assertThat(forecast.get("lapseForecast").get("count").asInt()).isEqualTo(before + 1)
                .isEqualTo(forecast.get("lapseForecast").get("policies").size());
        assertThat(DashboardApi.forecastNumbers(dashboards.ok(TERM_END))).as("the term end is not before asOf")
                .doesNotContain(number);
        assertThat(DashboardApi.forecastNumbers(dashboards.ok("2027-11-01"))).as("the day after the term end")
                .contains(number);
        assertThat(DashboardApi.forecastNumbers(dashboards.ok("2027-09-01"))).as("before the term end")
                .doesNotContain(number);
    }

    @Test
    @DisplayName("AC-11.4: a policy leaves the lapse forecast once its renewal is paid")
    void paymentTakesAPolicyOutOfTheForecast() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        assertThat(DashboardApi.forecastNumbers(dashboards.ok("2027-11-05"))).contains(number);

        at("2027-11-05");
        renewals.payOk(number);

        assertThat(DashboardApi.forecastNumbers(dashboards.ok("2027-11-05"))).doesNotContain(number);
        assertThat(item("2027-11-05", number)).as("the renewal term is in force, 12 months from its end").isNull();
    }

    @Test
    @DisplayName("AC-11.4: a policy leaves the lapse forecast once it has lapsed (it is no longer in force), and the count follows")
    void aLapseTakesAPolicyOutOfTheForecast() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        assertThat(DashboardApi.forecastNumbers(dashboards.ok("2027-11-30"))).as("last day of grace").contains(number);

        JsonNode run = renewals.jobOk("2027-12-01");

        assertThat(run.get("lapsed").toString()).contains(number);
        JsonNode after = dashboards.ok("2027-12-01");
        assertThat(DashboardApi.forecastNumbers(after)).doesNotContain(number);
        assertThat(after.get("lapseForecast").get("count").asInt())
                .isEqualTo(after.get("lapseForecast").get("policies").size());
    }

    @Test
    @DisplayName("AC-11.4: a policy before its term end is absent from the forecast even with an offer pending")
    void aPolicyBeforeItsTermEndIsAbsent() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");

        assertThat(DashboardApi.forecastNumbers(dashboards.ok("2027-10-15"))).doesNotContain(number);
        assertThat(item("2027-10-15", number).get("renewalPremium").asText()).isEqualTo("6850.00");
    }
}
