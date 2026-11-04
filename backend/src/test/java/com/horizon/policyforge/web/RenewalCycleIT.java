package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationApi.texts;
import static com.horizon.policyforge.support.RenewalApi.JOB_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.service.RenewalScheduler;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The renewal cycle (AC-07.1, AC-07.2, AC-07.4, AC-07.6): the admin job and the scheduler offer a renewal term to every
 * in-force policy that is within its offer window, idempotently per date, and lapse a policy whose renewal is still
 * unpaid after its grace period. Every run date is given explicitly, so the test is deterministic. The shared test
 * context also holds the policies of the other renewal tests, so lists are checked for the test's own policy number.
 */
@Tag("AC-07")
class RenewalCycleIT extends RenewalTestSupport {

    @Autowired
    private RenewalScheduler scheduler;

    @Autowired
    private ApplicationContext context;

    private static void assertMoney(Object actual, String expected) {
        assertThat(((BigDecimal) actual).toPlainString()).isEqualTo(expected);
    }

    private List<String> offersOf(JsonNode result) {
        return texts(result.get("offersCreated"));
    }

    private List<String> lapsedOf(JsonNode result) {
        return texts(result.get("lapsed"));
    }

    @Test
    @DisplayName("AC-07.1: on 2027-10-01 the cycle offers an ACTIVE TERM_LIFE policy (2026-11-01..2027-10-31) a renewal term 2027-11-01..2028-10-31, unpaid, 6850.00 at rule version 1, lists it in offersCreated and appends no transition")
    void theOfferIsCreatedThirtyDaysBeforeExpiry() throws Exception {
        String number = issued("TERM_LIFE");
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        JsonNode result = renewals.jobOk("2027-10-01");

        assertThat(fieldNames(result)).as("the C1 key set, and nothing else").isEqualTo(JOB_FIELDS);
        assertThat(result.get("runDate").asText()).isEqualTo("2027-10-01");
        assertThat(offersOf(result)).contains(number).isSorted();
        assertThat(lapsedOf(result)).doesNotContain(number).isSorted();
        List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
        assertThat(terms).hasSize(2);
        Map<String, Object> offer = terms.get(1);
        assertThat(((Number) offer.get("TERM_NO")).intValue()).isEqualTo(2);
        assertThat(String.valueOf(offer.get("START_DATE"))).isEqualTo("2027-11-01");
        assertThat(String.valueOf(offer.get("END_DATE"))).isEqualTo("2028-10-31");
        assertMoney(offer.get("PREMIUM"), "6850.00");
        assertThat(offer.get("PAID")).isEqualTo(false);
        JsonNode termViews = detail(number).get("terms");
        assertThat(termViews.get(1).path("ruleVersion").asInt()).as("the offer records the rule version").isEqualTo(1);
        assertThat(termViews.get(0).path("ruleVersion").asInt()).isEqualTo(1);
        RenewalRows.Snapshot after = RenewalRows.snapshot(jdbc, number);
        assertThat(after.terms()).isEqualTo(before.terms() + 1);
        assertThat(after.transitions()).as("an offer appends no transition").isEqualTo(before.transitions());
        assertThat(after.versions()).as("an offer appends no version").isEqualTo(before.versions());
        assertThat(after.status()).isEqualTo("ACTIVE");
        assertThat(after.currentVersionNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-07.1: a run on 2027-09-30, the day before the offer window opens, creates nothing for the policy")
    void noOfferBeforeTheWindowOpens() throws Exception {
        String number = issued("TERM_LIFE");
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        JsonNode result = renewals.jobOk("2027-09-30");

        assertThat(offersOf(result)).doesNotContain(number);
        assertThat(lapsedOf(result)).doesNotContain(number);
        assertThat(PolicyRows.terms(jdbc, number)).hasSize(1);
        RenewalRows.Snapshot after = RenewalRows.snapshot(jdbc, number);
        assertThat(after).isEqualTo(new RenewalRows.Snapshot(before.terms(), before.paidTerms(), before.versions(),
                before.transitions(), before.auditEntries() + 1, before.status(), before.currentVersionNo()));
    }

    @Test
    @DisplayName("AC-07.1: a MOTOR and a HOUSEHOLD policy are offered 2027-11-01..2028-10-31 at the premium PremiumCalculator gives for their stored inputs, 15150.00 and 11350.00, at rule version 1")
    void everyProductIsOffered() throws Exception {
        String motor = issued("MOTOR");
        String household = issued("HOUSEHOLD");

        JsonNode result = renewals.jobOk("2027-10-01");

        assertThat(offersOf(result)).contains(motor, household);
        Map<String, Object> motorOffer = PolicyRows.terms(jdbc, motor).get(1);
        Map<String, Object> householdOffer = PolicyRows.terms(jdbc, household).get(1);
        assertMoney(motorOffer.get("PREMIUM"), "15150.00");
        assertMoney(householdOffer.get("PREMIUM"), "11350.00");
        for (Map<String, Object> offer : List.of(motorOffer, householdOffer)) {
            assertThat(String.valueOf(offer.get("START_DATE"))).isEqualTo("2027-11-01");
            assertThat(String.valueOf(offer.get("END_DATE"))).isEqualTo("2028-10-31");
            assertThat(offer.get("PAID")).isEqualTo(false);
        }
        assertThat(detail(motor).get("terms").get(1).path("ruleVersion").asInt()).isEqualTo(1);
        assertThat(detail(household).get("terms").get(1).path("ruleVersion").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-07.1: a job with no date runs for today on the injected clock, and every run appends exactly one RENEWAL_CYCLE_RUN audit entry by the caller, even when nothing is offered")
    void theJobDefaultsToTodayAndIsAudited() throws Exception {
        String number = issued("TERM_LIFE");
        int auditBefore = RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN").size();
        at("2027-10-01");

        MvcResult defaulted = renewals.jobEmptyBody("admin1", null);
        JsonNode body = renewals.bodyOf(defaulted);

        assertThat(defaulted.getResponse().getStatus()).as(defaulted.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body.get("runDate").asText()).isEqualTo("2027-10-01");
        assertThat(offersOf(body)).contains(number);
        List<Map<String, Object>> audit = RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN");
        assertThat(audit).as("one entry for the run").hasSize(auditBefore + 1);
        Map<String, Object> entry = audit.get(audit.size() - 1);
        assertThat(entry).containsEntry("ACTOR_ID", "admin1").containsEntry("ENTITY_TYPE", "RENEWAL_CYCLE")
                .containsEntry("ENTITY_ID", "2027-10-01");

        renewals.jobOk("2027-10-01");
        renewals.jobOk("2020-01-01");
        assertThat(RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN")).as("one more entry per run, whatever it did")
                .hasSize(auditBefore + 3);
    }

    @Test
    @DisplayName("AC-07.1: the scheduler invoked directly on a fixed clock of 2027-10-01 runs the cycle for that date, creates the offer and audits the run as system:renewal-scheduler")
    void theSchedulerRunsTheCycleForTodayOnTheClock() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");

        scheduler.runScheduled();

        assertThat(PolicyRows.terms(jdbc, number)).as("the offer exists").hasSize(2);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ACTIVE");
        List<Map<String, Object>> audit = RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN");
        assertThat(audit).isNotEmpty();
        Map<String, Object> entry = audit.get(audit.size() - 1);
        assertThat(entry).containsEntry("ACTOR_ID", "system:renewal-scheduler")
                .containsEntry("ENTITY_TYPE", "RENEWAL_CYCLE").containsEntry("ENTITY_ID", "2027-10-01");
    }

    @Test
    @DisplayName("AC-07.1: test configuration turns the scheduled trigger off, so a run at 23:00 can never touch an integration-test database")
    void theTriggerIsOffInTestConfiguration() {
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }

    @Test
    @DisplayName("AC-07.2: a second run for 2027-10-01 creates no term and returns empty lists, so the offer is idempotent")
    void aSecondRunForTheSameDateCreatesNothing() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        RenewalRows.Snapshot afterFirst = RenewalRows.snapshot(jdbc, number);

        JsonNode second = renewals.jobOk("2027-10-01");

        assertThat(second.get("offersCreated")).isEmpty();
        assertThat(second.get("lapsed")).isEmpty();
        RenewalRows.Snapshot afterSecond = RenewalRows.snapshot(jdbc, number);
        assertThat(afterSecond.terms()).isEqualTo(2);
        assertThat(afterSecond).isEqualTo(new RenewalRows.Snapshot(afterFirst.terms(), afterFirst.paidTerms(),
                afterFirst.versions(), afterFirst.transitions(), afterFirst.auditEntries() + 1, afterFirst.status(),
                afterFirst.currentVersionNo()));
    }

    @Test
    @DisplayName("AC-07.2: four runs for the same date sent at the same moment leave exactly one term 2 and list the policy in offersCreated exactly once in all")
    void concurrentRunsLeaveOneOffer() throws Exception {
        String number = issued("TERM_LIFE");
        int runs = 4;
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < runs; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return renewals.job("admin1", "2027-10-01", Map.of());
                }));
            }
            start.countDown();
            int listed = 0;
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(60, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                        .isEqualTo(200);
                listed += (int) offersOf(renewals.bodyOf(result)).stream().filter(number::equals).count();
            }
            assertThat(listed).as("the offer is reported by exactly one of the runs").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(PolicyRows.terms(jdbc, number)).as("exactly one term 2").hasSize(2);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ACTIVE");
        assertThat(PolicyRows.transitions(jdbc, number)).hasSize(1);
    }

    @Test
    @DisplayName("AC-07.4: after the offer, the 2027-11-30 run (termEnd + 30) leaves the policy in force and unlisted, the 2027-12-01 run makes it LAPSED with reason GRACE_PERIOD_EXPIRED by system:renewal-scheduler and lists it, and a rerun adds no second transition")
    void thePolicyLapsesTheDayAfterTheGracePeriod() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");

        JsonNode onTheLastDay = renewals.jobOk("2027-11-30");

        assertThat(lapsedOf(onTheLastDay)).doesNotContain(number);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ACTIVE");
        assertThat(PolicyRows.transitions(jdbc, number)).hasSize(1);

        JsonNode afterGrace = renewals.jobOk("2027-12-01");

        assertThat(lapsedOf(afterGrace)).contains(number);
        assertThat(offersOf(afterGrace)).doesNotContain(number);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("LAPSED");
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions).hasSize(2);
        Map<String, Object> lapse = transitions.get(1);
        assertThat(lapse).containsEntry("FROM_STATUS", "ACTIVE").containsEntry("TO_STATUS", "LAPSED")
                .containsEntry("REASON", "GRACE_PERIOD_EXPIRED").containsEntry("ACTOR_ID", "system:renewal-scheduler");
        assertThat(((Number) PolicyRows.policy(jdbc, number).get("CURRENT_VERSION_NO")).intValue()).isEqualTo(1);
        assertThat(PolicyRows.terms(jdbc, number)).as("the lapse writes no term").hasSize(2);

        JsonNode rerun = renewals.jobOk("2027-12-01");

        assertThat(lapsedOf(rerun)).doesNotContain(number);
        assertThat(PolicyRows.transitions(jdbc, number)).as("no second transition").hasSize(2);
    }

    @Test
    @DisplayName("AC-07.4: a policy whose offer window was missed is offered and lapsed in the same run, with both lists naming it, and its offered term stays unpaid")
    void aMissedWindowStillLapsesInTheSameRun() throws Exception {
        String number = issued("TERM_LIFE");

        JsonNode result = renewals.jobOk("2027-12-05");

        assertThat(offersOf(result)).contains(number);
        assertThat(lapsedOf(result)).contains(number);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("LAPSED");
        List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
        assertThat(terms).hasSize(2);
        assertThat(terms.get(1).get("PAID")).isEqualTo(false);
        assertThat(PolicyRows.transitions(jdbc, number).get(1)).containsEntry("REASON", "GRACE_PERIOD_EXPIRED");
    }

    @Test
    @DisplayName("AC-07.6: MOTOR and HOUSEHOLD (grace 15) are still in force after the 2027-11-15 run and LAPSED after the 2027-11-16 run, and TERM_LIFE (grace 30) is in force after 2027-11-30 and LAPSED after 2027-12-01")
    void graceDiffersByProduct() throws Exception {
        String motor = issued("MOTOR");
        String household = issued("HOUSEHOLD");
        String termLife = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");

        JsonNode day15 = renewals.jobOk("2027-11-15");

        assertThat(lapsedOf(day15)).doesNotContain(motor, household, termLife);
        for (String number : List.of(motor, household, termLife)) {
            assertThat(RenewalRows.status(jdbc, number)).as(number + " on termEnd + 15").isEqualTo("ACTIVE");
        }

        JsonNode day16 = renewals.jobOk("2027-11-16");

        assertThat(lapsedOf(day16)).contains(motor, household).doesNotContain(termLife);
        assertThat(RenewalRows.status(jdbc, motor)).isEqualTo("LAPSED");
        assertThat(RenewalRows.status(jdbc, household)).isEqualTo("LAPSED");
        assertThat(RenewalRows.status(jdbc, termLife)).as("TERM_LIFE on termEnd + 16").isEqualTo("ACTIVE");

        JsonNode day30 = renewals.jobOk("2027-11-30");

        assertThat(lapsedOf(day30)).doesNotContain(termLife);
        assertThat(RenewalRows.status(jdbc, termLife)).as("TERM_LIFE on termEnd + 30").isEqualTo("ACTIVE");

        JsonNode day31 = renewals.jobOk("2027-12-01");

        assertThat(lapsedOf(day31)).contains(termLife);
        assertThat(RenewalRows.status(jdbc, termLife)).as("TERM_LIFE on termEnd + 31").isEqualTo("LAPSED");
        for (String number : List.of(motor, household, termLife)) {
            Map<String, Object> lapse = PolicyRows.transitions(jdbc, number).get(1);
            assertThat(lapse).containsEntry("REASON", "GRACE_PERIOD_EXPIRED")
                    .containsEntry("ACTOR_ID", "system:renewal-scheduler");
        }
    }

    @Test
    @DisplayName("AC-07.4: a policy that is no longer in force (LAPSED) is skipped by later runs: no offer, no second lapse")
    void aLapsedPolicyIsNotProcessedAgain() throws Exception {
        String number = issued("MOTOR");
        renewals.jobOk("2027-10-01");
        renewals.jobOk("2027-11-16");
        RenewalRows.Snapshot lapsed = RenewalRows.snapshot(jdbc, number);
        assertThat(lapsed.status()).isEqualTo("LAPSED");

        JsonNode later = renewals.jobOk("2028-09-01");

        assertThat(offersOf(later)).doesNotContain(number);
        assertThat(lapsedOf(later)).doesNotContain(number);
        RenewalRows.Snapshot after = RenewalRows.snapshot(jdbc, number);
        assertThat(after.terms()).isEqualTo(lapsed.terms());
        assertThat(after.transitions()).isEqualTo(lapsed.transitions());
    }
}
