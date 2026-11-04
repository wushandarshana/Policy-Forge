package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
 * AC-07.5 and plan P-8: a payment that races a lapse of the same policy loses or wins on the policy's optimistic lock,
 * and the loser writes nothing. The race is made deterministic by test configuration only: a wrapper around the policy
 * repository holds the thread of one named request just before it saves the policy, after it has read everything and
 * (for a payment) written its term, version and transition rows. The other request then runs to completion and commits.
 * When the held request is released its save hits the changed version, so it is the loser. Production code has no hook.
 */
@Tag("AC-07")
@Import(RenewalRaceIT.HoldPolicySave.class)
class RenewalRaceIT extends RenewalTestSupport {

    static final String LOSER_THREAD = "renewal-race-loser";

    /** Holds the policy save of the loser thread, for one named policy, until released. */
    @TestConfiguration
    static class HoldPolicySave {

        static final AtomicReference<String> HOLD_FOR = new AtomicReference<>();
        static volatile CountDownLatch reached = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);

        @Bean
        static BeanPostProcessor holdPolicySave() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (!"policyRepository".equals(beanName) || !(bean instanceof PolicyRepository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                if (method.getName().equals("saveAndFlush") && args != null
                                        && args[0] instanceof PolicyEntity policy
                                        && policy.getPolicyNumber().equals(HOLD_FOR.get())
                                        && LOSER_THREAD.equals(Thread.currentThread().getName())) {
                                    reached.countDown();
                                    if (!release.await(20, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("the held request was never released");
                                    }
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
        HoldPolicySave.HOLD_FOR.set(null);
        HoldPolicySave.release.countDown();
    }

    private static ExecutorService loserThread() {
        return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, LOSER_THREAD));
    }

    private void arm(String policyNumber) {
        HoldPolicySave.reached = new CountDownLatch(1);
        HoldPolicySave.release = new CountDownLatch(1);
        HoldPolicySave.HOLD_FOR.set(policyNumber);
    }

    @Test
    @DisplayName("AC-07.5: a payment that races the lapse and loses gets 409 POLICY_UPDATE_CONFLICT and writes nothing, so the policy is LAPSED with its term unpaid, no version 2 and no RENEWAL_PAID transition")
    void aPaymentLosingToTheLapseWritesNothing() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-30");
        arm(number);
        ExecutorService pool = loserThread();
        try {
            Future<MvcResult> loser = pool.submit(() -> renewals.pay("customer1", number, new LinkedHashMap<>()));
            assertThat(HoldPolicySave.reached.await(20, TimeUnit.SECONDS))
                    .as("the payment read the policy and reached its save").isTrue();

            JsonNode lapse = renewals.jobOk("2027-12-01");
            HoldPolicySave.release.countDown();
            MvcResult lost = loser.get(30, TimeUnit.SECONDS);

            assertThat(texts(lapse.get("lapsed"))).contains(number);
            JsonNode problem = problem(lost, 409);
            assertThat(problem.get("code").asText()).isEqualTo("POLICY_UPDATE_CONFLICT");
            assertThat(problem.get("detail").asText()).doesNotContain(number);
            assertThat(RenewalRows.status(jdbc, number)).isEqualTo("LAPSED");
            assertThat(PolicyRows.terms(jdbc, number).get(1).get("PAID")).as("the payment left term 2 unpaid")
                    .isEqualTo(false);
            assertThat(PolicyRows.versions(jdbc, number)).as("no version 2").hasSize(1);
            List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
            assertThat(transitions.stream().map(t -> t.get("TO_STATUS")).toList()).containsExactly("ACTIVE", "LAPSED");
            assertThat(transitions.stream().map(t -> t.get("REASON")).toList()).doesNotContain("RENEWAL_PAID");
        } finally {
            HoldPolicySave.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-07.5: a lapse that races a payment and loses skips the policy and writes nothing, so the policy is RENEWED with no GRACE_PERIOD_EXPIRED transition and is not listed in lapsed")
    void aLapseLosingToThePaymentWritesNothing() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-11-30");
        arm(number);
        ExecutorService pool = loserThread();
        try {
            Future<MvcResult> loser = pool.submit(() -> renewals.job("admin1", "2027-12-01", Map.of()));
            assertThat(HoldPolicySave.reached.await(20, TimeUnit.SECONDS))
                    .as("the cycle read the policy and reached its lapse save").isTrue();

            JsonNode paid = renewals.payOk(number);
            HoldPolicySave.release.countDown();
            MvcResult run = loser.get(30, TimeUnit.SECONDS);

            assertThat(paid.get("policy").get("status").asText()).isEqualTo("RENEWED");
            assertThat(run.getResponse().getStatus()).as(run.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(texts(renewals.bodyOf(run).get("lapsed"))).doesNotContain(number);
            assertThat(RenewalRows.status(jdbc, number)).isEqualTo("RENEWED");
            List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
            assertThat(transitions.stream().map(t -> t.get("TO_STATUS")).toList()).containsExactly("ACTIVE", "RENEWED");
            assertThat(transitions.stream().map(t -> t.get("REASON")).toList()).doesNotContain("GRACE_PERIOD_EXPIRED");
        } finally {
            HoldPolicySave.release.countDown();
            pool.shutdownNow();
        }
    }
}
