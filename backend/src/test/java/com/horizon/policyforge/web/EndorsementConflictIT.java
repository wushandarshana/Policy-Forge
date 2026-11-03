package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.EndorsementApi.addressBody;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.repository.EndorsementRepository;
import com.horizon.policyforge.support.EndorsementRows;
import com.horizon.policyforge.support.PolicyRows;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Concurrent endorsements of one policy (AC-06.6): the sequence numbers and the version numbers never repeat, and the
 * request that loses a race gets 409 POLICY_UPDATE_CONFLICT with nothing written.
 *
 * <p>The first test makes the race deterministic. A test-only wrapper around the endorsement repository holds the
 * request of one named thread just before its endorsement insert, after it has read the policy and computed sequence 1.
 * A second request then runs to completion and commits sequence 1. When the held request is released its insert hits
 * the unique key, so it is the loser. The wrapper is test configuration only, so production code has no hook.
 */
@Tag("AC-06")
@Import(EndorsementConflictIT.HoldEndorsementInsert.class)
class EndorsementConflictIT extends EndorsementTestSupport {

    static final String LOSER_THREAD = "endorsement-conflict-loser";

    /** Holds the endorsement insert of the loser thread until released. */
    @TestConfiguration
    static class HoldEndorsementInsert {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static volatile CountDownLatch reached = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);

        @Bean
        static BeanPostProcessor holdEndorsementInsert() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    // The factory bean of the repository passes through here too: only the repository itself is wrapped.
                    if (!"endorsementRepository".equals(beanName) || !(bean instanceof EndorsementRepository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                if (ARMED.get() && method.getName().equals("save")
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
        HoldEndorsementInsert.ARMED.set(false);
        HoldEndorsementInsert.release.countDown();
    }

    @Test
    @DisplayName("AC-06.6: when two endorsements race for sequence 1, the winner is stored and the loser gets 409 POLICY_UPDATE_CONFLICT with a fixed detail and nothing written, so no sequence or version number repeats")
    void theLoserOfARaceGetsAConflict() throws Exception {
        String number = issued("TERM_LIFE");
        HoldEndorsementInsert.reached = new CountDownLatch(1);
        HoldEndorsementInsert.release = new CountDownLatch(1);
        HoldEndorsementInsert.ARMED.set(true);
        ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, LOSER_THREAD));
        try {
            Future<MvcResult> loser = pool.submit(() -> endorsements.endorse("customer1", number,
                    addressBody("1 Loser Road", "Pune", "411001", null)));
            assertThat(HoldEndorsementInsert.reached.await(20, TimeUnit.SECONDS))
                    .as("the loser read the policy and reached its endorsement insert").isTrue();

            JsonNode winner = endorsements.endorseOk(number, addressBody("2 Winner Road", "Pune", "411001", null));
            HoldEndorsementInsert.release.countDown();
            MvcResult lost = loser.get(30, TimeUnit.SECONDS);

            assertThat(winner.get("endorsement").get("sequenceNo").asInt()).isEqualTo(1);
            JsonNode problem = problem(lost, 409);
            assertThat(problem.get("code").asText()).isEqualTo("POLICY_UPDATE_CONFLICT");
            assertThat(problem.get("detail").asText()).as("a fixed detail that never echoes input")
                    .doesNotContain("Loser").doesNotContain(number);
            List<Map<String, Object>> rows = EndorsementRows.endorsements(jdbc, number);
            assertThat(rows).as("only the winner is stored").hasSize(1);
            assertThat(((Number) rows.get(0).get("AFTER_VERSION_NO")).intValue()).isEqualTo(2);
            assertThat(PolicyRows.versions(jdbc, number)).hasSize(2);
            assertThat(PolicyRows.versions(jdbc, number).get(1)).containsEntry("ADDRESS_LINE1", "2 Winner Road");
            assertThat(PolicyRows.transitions(jdbc, number)).as("one transition for the one endorsement").hasSize(2);
            assertThat(((Number) PolicyRows.policy(jdbc, number).get("CURRENT_VERSION_NO")).intValue()).isEqualTo(2);
        } finally {
            HoldEndorsementInsert.ARMED.set(false);
            HoldEndorsementInsert.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-06.6: eight endorsements sent at the same moment to one policy are each 201 or 409 POLICY_UPDATE_CONFLICT, and the stored sequence numbers and version numbers are unique and contiguous")
    void concurrentEndorsementsNeverRepeatANumber() throws Exception {
        String number = issued("TERM_LIFE");
        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return endorsements.endorse("customer1", number, newAddressBody(null));
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            int created = 0;
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(60, TimeUnit.SECONDS);
                int status = result.getResponse().getStatus();
                assertThat(status).as(result.getResponse().getContentAsString()).isIn(201, 409);
                if (status == 201) {
                    created++;
                } else {
                    assertThat(endorsements.bodyOf(result).get("code").asText()).isEqualTo("POLICY_UPDATE_CONFLICT");
                }
            }

            List<Map<String, Object>> rows = EndorsementRows.endorsements(jdbc, number);
            assertThat(created).as("at least one request wins").isGreaterThanOrEqualTo(1);
            assertThat(rows).as("one endorsement per 201").hasSize(created);
            Set<Integer> sequences = new HashSet<>();
            Set<Integer> afterVersions = new HashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                int sequence = ((Number) rows.get(i).get("SEQUENCE_NO")).intValue();
                int after = ((Number) rows.get(i).get("AFTER_VERSION_NO")).intValue();
                sequences.add(sequence);
                afterVersions.add(after);
                assertThat(sequence).as("contiguous sequence numbers").isEqualTo(i + 1);
                assertThat(after).as("contiguous version numbers").isEqualTo(i + 2);
                assertThat(((Number) rows.get(i).get("BEFORE_VERSION_NO")).intValue()).isEqualTo(i + 1);
            }
            assertThat(sequences).hasSize(created);
            assertThat(afterVersions).hasSize(created);
            assertThat(PolicyRows.versions(jdbc, number)).as("version 1 plus one per endorsement").hasSize(created + 1);
            assertThat(PolicyRows.transitions(jdbc, number)).as("issuance plus one per endorsement")
                    .hasSize(created + 1);
            assertThat(((Number) PolicyRows.policy(jdbc, number).get("CURRENT_VERSION_NO")).intValue())
                    .isEqualTo(created + 1);
        } finally {
            pool.shutdownNow();
        }
    }
}
