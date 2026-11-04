package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.repository.PolicyStatusTransitionEntity;
import com.horizon.policyforge.repository.PolicyStatusTransitionRepository;
import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.repository.PolicyTermRepository;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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

/**
 * Plan P-7: each in-force policy is processed in its own transaction, offer then lapse, so one failure neither undoes
 * the others nor keeps half of its own work. The failure is injected by test configuration only: wrappers around the
 * term and the transition repositories throw when asked to save for one named policy. Production code has no hook.
 */
@Tag("AC-07")
@Import(RenewalIsolationIT.FailingWrites.class)
class RenewalIsolationIT extends RenewalTestSupport {

    /** Wrappers that fail one named policy's term save, or its transition save, while armed. */
    @TestConfiguration
    static class FailingWrites {

        static final AtomicReference<String> FAIL_TERM_FOR = new AtomicReference<>();
        static final AtomicReference<String> FAIL_TRANSITION_FOR = new AtomicReference<>();

        @Bean
        static BeanPostProcessor failingRenewalWrites() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if ("policyTermRepository".equals(beanName) && bean instanceof PolicyTermRepository) {
                        return wrap(bean, FAIL_TERM_FOR, args -> args[0] instanceof PolicyTermEntity term
                                ? term.getPolicyNumber() : null);
                    }
                    if ("policyStatusTransitionRepository".equals(beanName)
                            && bean instanceof PolicyStatusTransitionRepository) {
                        return wrap(bean, FAIL_TRANSITION_FOR, args -> args[0] instanceof PolicyStatusTransitionEntity t
                                ? t.getPolicyNumber() : null);
                    }
                    return bean;
                }
            };
        }

        private static Object wrap(Object bean, AtomicReference<String> armedFor,
                java.util.function.Function<Object[], String> policyOf) {
            return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(),
                    (proxy, method, args) -> {
                        if (method.getName().equals("save") && args != null
                                && policyOf.apply(args) != null && policyOf.apply(args).equals(armedFor.get())) {
                            throw new IllegalStateException("injected failure for one policy");
                        }
                        try {
                            return method.invoke(bean, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    @AfterEach
    void disarm() {
        FailingWrites.FAIL_TERM_FOR.set(null);
        FailingWrites.FAIL_TRANSITION_FOR.set(null);
    }

    @Test
    @DisplayName("AC-07.1: when the offer of one policy fails, the run still answers 200, the other policies are offered, the failed one is unlisted with nothing written, and the next run offers it")
    void oneFailedOfferDoesNotStopTheOthers() throws Exception {
        String first = issued("TERM_LIFE");
        String failing = issued("TERM_LIFE");
        String last = issued("TERM_LIFE");
        FailingWrites.FAIL_TERM_FOR.set(failing);
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, failing);

        JsonNode run = renewals.jobOk("2027-10-01");

        assertThat(texts(run.get("offersCreated"))).contains(first, last).doesNotContain(failing);
        assertThat(PolicyRows.terms(jdbc, first)).hasSize(2);
        assertThat(PolicyRows.terms(jdbc, last)).hasSize(2);
        RenewalRows.Snapshot after = RenewalRows.snapshot(jdbc, failing);
        assertThat(after.terms()).as("the failed policy got no term").isEqualTo(before.terms());
        assertThat(after.status()).isEqualTo("ACTIVE");

        FailingWrites.FAIL_TERM_FOR.set(null);
        JsonNode rerun = renewals.jobOk("2027-10-01");

        assertThat(texts(rerun.get("offersCreated"))).contains(failing).doesNotContain(first, last);
        assertThat(PolicyRows.terms(jdbc, failing)).hasSize(2);
    }

    @Test
    @DisplayName("AC-07.4: when the lapse fails after the offer was written in the same run, that policy's offer is rolled back too, and the other policy is still offered and lapsed")
    void aFailedLapseKeepsNothingOfItsOwnRun() throws Exception {
        String ok = issued("TERM_LIFE");
        String failing = issued("TERM_LIFE");
        FailingWrites.FAIL_TRANSITION_FOR.set(failing);

        JsonNode run = renewals.jobOk("2027-12-05");

        assertThat(texts(run.get("offersCreated"))).contains(ok).doesNotContain(failing);
        assertThat(texts(run.get("lapsed"))).contains(ok).doesNotContain(failing);
        assertThat(RenewalRows.status(jdbc, ok)).isEqualTo("LAPSED");
        assertThat(PolicyRows.terms(jdbc, failing)).as("the offer of the failed policy was rolled back").hasSize(1);
        assertThat(RenewalRows.status(jdbc, failing)).isEqualTo("ACTIVE");
        assertThat(PolicyRows.transitions(jdbc, failing)).hasSize(1);

        FailingWrites.FAIL_TRANSITION_FOR.set(null);
        JsonNode rerun = renewals.jobOk("2027-12-05");

        assertThat(texts(rerun.get("lapsed"))).contains(failing);
        assertThat(RenewalRows.status(jdbc, failing)).isEqualTo("LAPSED");
    }
}
