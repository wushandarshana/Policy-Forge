package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.repository.PolicyRepository;
import com.horizon.policyforge.support.CancellationRows;
import com.horizon.policyforge.support.PolicyRows;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Concurrent cancels of one policy (AC-08.4, plan P-8): one request wins and writes the single refund record and the
 * CANCELLED transition, every other one is a 409 and writes nothing.
 *
 * <p>The first test makes the race deterministic by test configuration only. A wrapper around the policy repository
 * holds the thread of one named request right after it has read the policy (still in force), before it writes anything.
 * A second request then cancels to completion and commits. When the held request is released it goes on from its stale
 * read, so its refund insert meets the unique key (and its policy update would meet the optimistic lock): it is the
 * loser. Production code has no hook for this.
 */
@Tag("AC-08")
@Import(CancellationConflictIT.HoldAfterPolicyRead.class)
class CancellationConflictIT extends CancellationTestSupport {

    static final String LOSER_THREAD = "cancellation-conflict-loser";

    /** Holds the loser thread once, right after its policy read, until released. */
    @TestConfiguration
    static class HoldAfterPolicyRead {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static volatile CountDownLatch reached = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);

        @Bean
        static BeanPostProcessor holdAfterPolicyRead() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (!"policyRepository".equals(beanName) || !(bean instanceof PolicyRepository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                Object result;
                                try {
                                    result = method.invoke(bean, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                                if (method.getName().equals("findByPolicyNumber")
                                        && LOSER_THREAD.equals(Thread.currentThread().getName())
                                        && ARMED.compareAndSet(true, false)) {
                                    reached.countDown();
                                    if (!release.await(20, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("the held request was never released");
                                    }
                                }
                                return result;
                            });
                }
            };
        }
    }

    @AfterEach
    void disarm() {
        HoldAfterPolicyRead.ARMED.set(false);
        HoldAfterPolicyRead.release.countDown();
    }

    @Test
    @DisplayName("AC-08.4: when two cancels race and the second reads the policy before the first commits, the first gets 201 and the loser gets 409 POLICY_UPDATE_CONFLICT with a fixed detail, so exactly one refund record and one CANCELLED transition exist")
    void theLoserOfARaceGetsAConflict() throws Exception {
        String number = issued("TERM_LIFE");
        HoldAfterPolicyRead.reached = new CountDownLatch(1);
        HoldAfterPolicyRead.release = new CountDownLatch(1);
        HoldAfterPolicyRead.ARMED.set(true);
        ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, LOSER_THREAD));
        try {
            Future<MvcResult> loser = pool.submit(
                    () -> cancellations.cancel("customer1", number, cancelBody("OTHER", "2027-03-01")));
            assertThat(HoldAfterPolicyRead.reached.await(20, TimeUnit.SECONDS))
                    .as("the loser read the policy while it was still in force").isTrue();

            JsonNode winner = cancellations.cancelOk(number, "2027-02-09");
            HoldAfterPolicyRead.release.countDown();
            MvcResult lost = loser.get(30, TimeUnit.SECONDS);

            assertThat(winner.get("refund").get("amount").asText()).isEqualTo("4473.29");
            JsonNode problem = problem(lost, 409);
            assertThat(problem.get("code").asText()).isEqualTo("POLICY_UPDATE_CONFLICT");
            assertThat(problem.get("detail").asText()).as("a fixed detail that never echoes input")
                    .doesNotContain(number).doesNotContain("2027-03-01");
            Map<String, Object> row = onlyRefundRow(number);
            assertThat(row).as("only the winner's refund is stored").containsEntry("UNEXPIRED_DAYS", 265)
                    .containsEntry("ACTOR_ID", "customer1");
            assertThat(CancellationRows.refunds(jdbc, number)).hasSize(1);
            List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
            assertThat(transitions.stream().map(t -> t.get("TO_STATUS")).toList()).containsExactly("ACTIVE", "CANCELLED");
            assertThat(transitions.get(1)).as("the winner's reason, not the loser's").containsEntry("REASON",
                    "NO_LONGER_NEEDED");
            assertThat(PolicyRows.policy(jdbc, number)).containsEntry("STATUS", "CANCELLED");
        } finally {
            HoldAfterPolicyRead.ARMED.set(false);
            HoldAfterPolicyRead.release.countDown();
            pool.shutdownNow();
        }
    }

    @ParameterizedTest(name = "AC-08.4: {0} cancels sent at the same moment to one policy give exactly one 201 and the rest 409 (POLICY_UPDATE_CONFLICT or INVALID_POLICY_STATE), with one refund record and one CANCELLED transition")
    @ValueSource(ints = {2, 8})
    void concurrentCancelsWriteOneRecord(int requests) throws Exception {
        String number = issued("TERM_LIFE");
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-02-09"));
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            int created = 0;
            int conflicts = 0;
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(60, TimeUnit.SECONDS);
                int status = result.getResponse().getStatus();
                assertThat(status).as(result.getResponse().getContentAsString()).isIn(201, 409);
                if (status == 201) {
                    created++;
                } else {
                    conflicts++;
                    assertThat(cancellations.bodyOf(result).get("code").asText())
                            .isIn("POLICY_UPDATE_CONFLICT", "INVALID_POLICY_STATE");
                }
            }

            assertThat(created).as("exactly one cancel succeeds").isEqualTo(1);
            assertThat(conflicts).isEqualTo(requests - 1);
            assertThat(CancellationRows.refunds(jdbc, number)).as("one refund record").hasSize(1);
            assertThat(PolicyRows.transitions(jdbc, number).stream().map(t -> t.get("TO_STATUS")).toList())
                    .containsExactly("ACTIVE", "CANCELLED");
            assertThat(PolicyRows.policy(jdbc, number)).containsEntry("STATUS", "CANCELLED");
        } finally {
            pool.shutdownNow();
        }
    }
}
