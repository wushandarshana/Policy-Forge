package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.repository.PolicyEntity;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyTermRepository;
import com.horizon.policyforge.support.CancellationRows;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
 * Security review MINOR-2: creating a renewal offer takes the policy's optimistic lock, so an offer that races a cancel
 * of the same policy conflicts with it instead of leaving an unpaid term on a CANCELLED policy. The race is made
 * deterministic by test configuration only: a wrapper holds the thread of one named request at a chosen save (the
 * cycle just before it saves the offer term, or the cancel just before it saves the policy), after it has read the
 * policy. The other request runs to completion and commits, then the held one is released and must lose. Production
 * code has no hook.
 */
@Tag("AC-07")
@Tag("AC-07.2")
@Import(RenewalOfferRaceIT.HoldSaves.class)
class RenewalOfferRaceIT extends CancellationTestSupport {

    static final String LOSER_THREAD = "offer-race-loser";

    /** Holds the loser thread's offer-term save or policy save, for one named policy, until released. */
    @TestConfiguration
    static class HoldSaves {

        static final AtomicReference<String> HOLD_FOR = new AtomicReference<>();
        static volatile CountDownLatch reached = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);

        private static boolean held(Object entity) {
            String number = entity instanceof PolicyTermEntity term ? term.getPolicyNumber()
                    : entity instanceof PolicyEntity policy ? policy.getPolicyNumber() : null;
            return number != null && number.equals(HOLD_FOR.get())
                    && LOSER_THREAD.equals(Thread.currentThread().getName());
        }

        @Bean
        static BeanPostProcessor holdSaves() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    boolean termRepository = "policyTermRepository".equals(beanName)
                            && bean instanceof PolicyTermRepository;
                    boolean policyRepository = "policyRepository".equals(beanName) && bean instanceof PolicyRepository;
                    if (!termRepository && !policyRepository) {
                        return bean;
                    }
                    String holdOn = termRepository ? "save" : "saveAndFlush";
                    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                if (method.getName().equals(holdOn) && args != null && held(args[0])) {
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
        HoldSaves.HOLD_FOR.set(null);
        HoldSaves.release.countDown();
    }

    private static ExecutorService loserThread() {
        return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, LOSER_THREAD));
    }

    private void arm(String policyNumber) {
        HoldSaves.reached = new CountDownLatch(1);
        HoldSaves.release = new CountDownLatch(1);
        HoldSaves.HOLD_FOR.set(policyNumber);
    }

    @Test
    @DisplayName("AC-07.2: an offer that races a cancel and loses is skipped and writes no term, so the policy is CANCELLED with only its paid term 1 and is not listed in offersCreated")
    void anOfferLosingToACancelWritesNoTerm() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");
        arm(number);
        ExecutorService pool = loserThread();
        try {
            Future<MvcResult> loser = pool.submit(() -> renewals.job("admin1", "2027-10-01", Map.of()));
            assertThat(HoldSaves.reached.await(20, TimeUnit.SECONDS))
                    .as("the cycle read the policy and reached its offer save").isTrue();

            JsonNode cancelled = cancellations.cancelOk(number, null);
            HoldSaves.release.countDown();
            MvcResult run = loser.get(30, TimeUnit.SECONDS);

            assertThat(cancelled.get("policy").get("status").asText()).isEqualTo("CANCELLED");
            assertThat(run.getResponse().getStatus()).as(run.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(texts(renewals.bodyOf(run).get("offersCreated"))).doesNotContain(number);
            assertThat(RenewalRows.status(jdbc, number)).isEqualTo("CANCELLED");
            List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
            assertThat(terms).as("no orphan unpaid term on the CANCELLED policy").hasSize(1);
            assertThat(terms.get(0).get("PAID")).isEqualTo(true);
        } finally {
            HoldSaves.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-07.2: a cancel that races an offer and loses gets 409 POLICY_UPDATE_CONFLICT and writes nothing, so the policy stays ACTIVE with the unpaid offer and no refund record")
    void aCancelLosingToAnOfferWritesNothing() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");
        arm(number);
        ExecutorService pool = loserThread();
        try {
            Future<MvcResult> loser = pool.submit(() -> cancellations.cancel("customer1", number,
                    cancelBody("NO_LONGER_NEEDED", null)));
            assertThat(HoldSaves.reached.await(20, TimeUnit.SECONDS))
                    .as("the cancel read the policy and reached its policy save").isTrue();

            JsonNode run = renewals.jobOk("2027-10-01");
            HoldSaves.release.countDown();
            MvcResult lost = loser.get(30, TimeUnit.SECONDS);

            assertThat(texts(run.get("offersCreated"))).contains(number);
            JsonNode problem = problem(lost, 409);
            assertThat(problem.get("code").asText()).isEqualTo("POLICY_UPDATE_CONFLICT");
            assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ACTIVE");
            assertThat(CancellationRows.refunds(jdbc, number)).isEmpty();
            List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
            assertThat(terms).hasSize(2);
            assertThat(terms.get(1).get("PAID")).isEqualTo(false);
            assertThat(PolicyRows.transitions(jdbc, number).stream().map(t -> t.get("TO_STATUS")).toList())
                    .containsExactly("ACTIVE");
        } finally {
            HoldSaves.release.countDown();
            pool.shutdownNow();
        }
    }
}
