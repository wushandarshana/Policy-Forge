package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.repository.PolicyVersionEntity;
import com.horizon.policyforge.repository.PolicyVersionRepository;
import com.horizon.policyforge.support.EndorsementRows;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC-07.7 (Sprint 4 security review, re-price versus pay): a CHANGE_SUM_INSURED endorsement that re-prices an unpaid
 * renewal offer races the payment of that same offer. Both appended policy version n + 1 and both save the policy, so
 * the policy's optimistic lock and the unique version key let exactly one win. The loser gets 409
 * POLICY_UPDATE_CONFLICT and writes nothing, so no paid term ever carries a premium that its own renewal version does
 * not carry.
 *
 * <p>The race is made deterministic by test configuration only. A wrapper around the policy version repository holds
 * the thread of one named request just before it inserts policy version n + 1, after it has read the policy and the
 * offer. The other request then runs to completion and commits, and the held one is released and must lose, on the
 * unique version key. The hold cannot sit later, at the policy save as {@code RenewalRaceIT} holds it: the held request
 * would already have inserted its own uncommitted version n + 1, and the other request's insert of the same key would
 * wait on it until the database lock timeout and fail, so the other request could never win. Production code has no
 * hook.
 *
 * <p>The numbers are the spec's: the AC-04.1 TERM_LIFE policy (6850.00, sum insured 5,000,000, term 2026-11-01 to
 * 2027-10-31), offered on 2027-10-01 at 6850.00. Both requests run on 2027-10-05: the payment, and CHANGE_SUM_INSURED
 * 7,000,000 effective 2027-10-05, which re-prices the offer to 9490.00 with a current-term adjustment of 195.29
 * (27 of 365 days of 2640.00), cross-checked with premium_check.py.
 */
@Tag("AC-07")
@Tag("AC-07.7")
@Import(RenewalRepriceRaceIT.HoldVersionInsert.class)
class RenewalRepriceRaceIT extends RenewalTestSupport {

    static final String LOSER_THREAD = "reprice-race-loser";
    static final String RACE_DAY = "2027-10-05";

    /** Holds the policy version insert of the loser thread, for one named policy, until released. */
    @TestConfiguration
    static class HoldVersionInsert {

        static final AtomicReference<String> HOLD_FOR = new AtomicReference<>();
        static volatile CountDownLatch reached = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);

        private static void holdIfLoser(String policyNumber) throws InterruptedException {
            if (policyNumber != null && policyNumber.equals(HOLD_FOR.get())
                    && LOSER_THREAD.equals(Thread.currentThread().getName())) {
                reached.countDown();
                if (!release.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the held request was never released");
                }
            }
        }

        @Bean
        static BeanPostProcessor holdVersionInsert() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (!"policyVersionRepository".equals(beanName) || !(bean instanceof PolicyVersionRepository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                if (method.getName().equals("save") && args != null
                                        && args[0] instanceof PolicyVersionEntity version) {
                                    holdIfLoser(version.getPolicyNumber());
                                }
                                try {
                                    return method.invoke(bean, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                }
            };
        }
    }

    @AfterEach
    void disarm() {
        HoldVersionInsert.HOLD_FOR.set(null);
        HoldVersionInsert.release.countDown();
    }

    private static ExecutorService loserThread() {
        return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, LOSER_THREAD));
    }

    private void arm(String policyNumber) {
        HoldVersionInsert.reached = new CountDownLatch(1);
        HoldVersionInsert.release = new CountDownLatch(1);
        HoldVersionInsert.HOLD_FOR.set(policyNumber);
    }

    private static String money(Object value) {
        return ((BigDecimal) value).toPlainString();
    }

    /** The AC-04.1 policy with the AC-07.1 offer pending at 6850.00, on the race day. */
    private String policyWithAPendingOffer() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");
        renewals.jobOk("2027-10-01");
        Map<String, Object> offer = term2(number);
        assertThat(money(offer.get("PREMIUM"))).isEqualTo("6850.00");
        assertThat(offer.get("PAID")).isEqualTo(false);
        at(RACE_DAY);
        return number;
    }

    private Map<String, Object> term2(String number) {
        List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
        assertThat(terms).hasSize(2);
        return terms.get(1);
    }

    private int term2RuleVersion(String number) {
        return jdbc.queryForObject("select rule_version from policy_term where policy_number = ? and term_no = 2",
                Integer.class, number);
    }

    private void assertTerm2Dates(String number) {
        Map<String, Object> term = term2(number);
        assertThat(term.get("START_DATE").toString()).isEqualTo("2027-11-01");
        assertThat(term.get("END_DATE").toString()).isEqualTo("2028-10-31");
        assertThat(term2RuleVersion(number)).as("the term keeps its rule version").isEqualTo(1);
    }

    /** Every paid term's premium equals the premium of the one version that starts with that term. */
    private void assertEveryPaidTermMatchesItsVersion(String number) {
        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        for (Map<String, Object> term : PolicyRows.terms(jdbc, number)) {
            if (!Boolean.TRUE.equals(term.get("PAID"))) {
                continue;
            }
            List<Map<String, Object>> startingWithTheTerm = versions.stream()
                    .filter(version -> version.get("EFFECTIVE_DATE").toString().equals(term.get("START_DATE").toString()))
                    .toList();
            assertThat(startingWithTheTerm).as("the version its payment appended, for term " + term.get("TERM_NO"))
                    .hasSize(1);
            assertThat(money(startingWithTheTerm.get(0).get("ANNUAL_PREMIUM")))
                    .as("paid term " + term.get("TERM_NO") + " was never re-priced")
                    .isEqualTo(money(term.get("PREMIUM")));
        }
    }

    @Test
    @DisplayName("AC-07.7: the payment wins the race and the held re-pricing endorsement gets 409 POLICY_UPDATE_CONFLICT and writes nothing, so term 2 is paid at 6850.00, the policy is RENEWED with version 2 at 6850.00 and sum insured 5000000.00, and there is no endorsement and no ENDORSED transition")
    void thePaymentWinsAndTheEndorsementWritesNothing() throws Exception {
        String number = policyWithAPendingOffer();
        arm(number);
        ExecutorService pool = loserThread();
        try {
            Future<MvcResult> loser = pool.submit(
                    () -> endorsements.endorse("customer1", number, sumInsuredBody("7000000", RACE_DAY)));
            assertThat(HoldVersionInsert.reached.await(20, TimeUnit.SECONDS))
                    .as("the endorsement read the policy and the offer and reached its version insert").isTrue();

            JsonNode paid = renewals.payOk(number);
            HoldVersionInsert.release.countDown();
            MvcResult lost = loser.get(30, TimeUnit.SECONDS);

            assertThat(paid.get("policy").get("status").asText()).isEqualTo("RENEWED");
            JsonNode problem = problem(lost, 409);
            assertThat(problem.get("code").asText()).isEqualTo("POLICY_UPDATE_CONFLICT");
            assertThat(problem.get("detail").asText()).as("a fixed detail that never echoes input")
                    .doesNotContain(number).doesNotContain("7000000");
            assertThat(EndorsementRows.endorsements(jdbc, number)).as("the endorsement wrote no row").isEmpty();
            Map<String, Object> term2 = term2(number);
            assertThat(term2.get("PAID")).isEqualTo(true);
            assertThat(money(term2.get("PREMIUM"))).as("term 2 is paid at the offered premium").isEqualTo("6850.00");
            assertTerm2Dates(number);
            assertThat(RenewalRows.status(jdbc, number)).isEqualTo("RENEWED");
            List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
            assertThat(versions).as("issued and renewed").hasSize(2);
            assertThat(money(versions.get(1).get("ANNUAL_PREMIUM"))).isEqualTo("6850.00");
            assertThat(money(versions.get(1).get("SUM_INSURED"))).isEqualTo("5000000.00");
            List<Object> toStatuses = PolicyRows.transitions(jdbc, number).stream().map(t -> t.get("TO_STATUS")).toList();
            assertThat(toStatuses).containsExactly("ACTIVE", "RENEWED");
            assertThat(toStatuses).as("no ENDORSED transition").doesNotContain("ENDORSED");
            assertEveryPaidTermMatchesItsVersion(number);
        } finally {
            HoldVersionInsert.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-07.7: the endorsement wins the race and the held payment gets 409 POLICY_UPDATE_CONFLICT and writes nothing, so term 2 is unpaid at 9490.00 with adjustment 195.29, the policy is ENDORSED with version 2 at sum insured 7000000.00 and premium 9490.00, there is no RENEWAL_PAID transition, and a fresh payment on 2027-10-06 renews at 9490.00 as version 3")
    void theEndorsementWinsAndThePaymentWritesNothing() throws Exception {
        String number = policyWithAPendingOffer();
        arm(number);
        ExecutorService pool = loserThread();
        try {
            Future<MvcResult> loser = pool.submit(() -> renewals.pay("customer1", number, new LinkedHashMap<>()));
            assertThat(HoldVersionInsert.reached.await(20, TimeUnit.SECONDS))
                    .as("the payment read the policy and reached its version insert").isTrue();

            JsonNode endorsed = endorsements.endorseOk(number, sumInsuredBody("7000000", RACE_DAY));
            HoldVersionInsert.release.countDown();
            MvcResult lost = loser.get(30, TimeUnit.SECONDS);

            assertThat(endorsed.get("policy").get("status").asText()).isEqualTo("ENDORSED");
            JsonNode problem = problem(lost, 409);
            assertThat(problem.get("code").asText()).isEqualTo("POLICY_UPDATE_CONFLICT");
            assertThat(problem.get("detail").asText()).as("a fixed detail that never echoes input")
                    .doesNotContain(number);
            Map<String, Object> term2 = term2(number);
            assertThat(term2.get("PAID")).as("the payment wrote nothing").isEqualTo(false);
            assertThat(money(term2.get("PREMIUM"))).as("the offer is re-priced").isEqualTo("9490.00");
            assertTerm2Dates(number);
            assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ENDORSED");
            List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
            assertThat(versions).as("issued and endorsed").hasSize(2);
            assertThat(money(versions.get(1).get("SUM_INSURED"))).isEqualTo("7000000.00");
            assertThat(money(versions.get(1).get("ANNUAL_PREMIUM"))).isEqualTo("9490.00");
            List<Map<String, Object>> endorsementRows = EndorsementRows.endorsements(jdbc, number);
            assertThat(endorsementRows).hasSize(1);
            assertThat(money(endorsementRows.get(0).get("ADJUSTMENT"))).as("27 of 365 days of the 2640.00 difference")
                    .isEqualTo("195.29");
            List<Object> reasons = PolicyRows.transitions(jdbc, number).stream().map(t -> t.get("REASON")).toList();
            assertThat(reasons).as("no RENEWAL_PAID transition").doesNotContain("RENEWAL_PAID");
            assertEveryPaidTermMatchesItsVersion(number);

            at("2027-10-06");
            JsonNode renewed = renewals.payOk(number);

            assertThat(renewed.get("term").get("premium").asText()).isEqualTo("9490.00");
            assertThat(renewed.get("policy").get("status").asText()).isEqualTo("RENEWED");
            List<Map<String, Object>> after = PolicyRows.versions(jdbc, number);
            assertThat(after).as("issued, endorsed, renewed").hasSize(3);
            assertThat(money(after.get(2).get("ANNUAL_PREMIUM"))).isEqualTo("9490.00");
            assertThat(money(after.get(2).get("SUM_INSURED"))).isEqualTo("7000000.00");
            assertThat(money(term2(number).get("PREMIUM"))).as("version 3 equals term 2's premium")
                    .isEqualTo(money(after.get(2).get("ANNUAL_PREMIUM")));
            assertThat(term2(number).get("PAID")).isEqualTo(true);
            assertEveryPaidTermMatchesItsVersion(number);
        } finally {
            HoldVersionInsert.release.countDown();
            pool.shutdownNow();
        }
    }
}
