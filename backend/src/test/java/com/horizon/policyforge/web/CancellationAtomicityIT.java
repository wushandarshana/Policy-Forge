package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.CancellationApi.cancelBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.repository.PolicyStatusTransitionEntity;
import com.horizon.policyforge.repository.PolicyStatusTransitionRepository;
import com.horizon.policyforge.support.CancellationRows;
import com.horizon.policyforge.support.PolicyRows;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC-08.4: the refund record, the policy's move to CANCELLED and the transition row are written atomically. The failure
 * is injected by test configuration only: a {@code @Primary} wrapper around the transition repository throws when it is
 * asked to save the CANCELLED transition, which is after the refund insert has reached the database (and after the
 * policy row was updated) and before the transition insert. Production code has no hook for this. After the failure no
 * refund row exists, and the status, the current version and the transition count are unchanged.
 */
@Tag("AC-08")
@Import(CancellationAtomicityIT.FailingTransitionWrite.class)
class CancellationAtomicityIT extends CancellationTestSupport {

    /** Replaces the transition repository with one that fails the CANCELLED save while armed. */
    @TestConfiguration
    static class FailingTransitionWrite {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        /** How many refund rows the failing transaction could see at the moment it failed. */
        static final AtomicLong REFUND_ROWS_SEEN = new AtomicLong(-1);

        @Bean
        @Primary
        PolicyStatusTransitionRepository failingTransitionRepository(
                @Qualifier("policyStatusTransitionRepository") PolicyStatusTransitionRepository real,
                EntityManager entityManager) {
            return (PolicyStatusTransitionRepository) Proxy.newProxyInstance(
                    PolicyStatusTransitionRepository.class.getClassLoader(),
                    new Class<?>[] {PolicyStatusTransitionRepository.class}, (proxy, method, args) -> {
                        if (ARMED.get() && method.getName().equals("save")
                                && args[0] instanceof PolicyStatusTransitionEntity transition
                                && transition.getToStatus() == PolicyStatus.CANCELLED) {
                            Number seen = (Number) entityManager.createNativeQuery("select count(*) from refund_record")
                                    .getSingleResult();
                            REFUND_ROWS_SEEN.set(seen.longValue());
                            throw new IllegalStateException("injected failure at the transition write");
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    @AfterEach
    void disarm() {
        FailingTransitionWrite.ARMED.set(false);
    }

    @Test
    @DisplayName("AC-08.4: a failure at the transition write, after the refund insert reached the database, leaves no refund row, and the status, the current version and the transition count are unchanged; the same request then succeeds")
    void aFailureAfterTheRefundInsertRollsEverythingBack() throws Exception {
        String number = issued("TERM_LIFE");
        CancellationRows.Snapshot before = snapshot(number);
        int refundRowsBefore = CancellationRows.totalRefunds(jdbc);
        FailingTransitionWrite.REFUND_ROWS_SEEN.set(-1);

        FailingTransitionWrite.ARMED.set(true);
        MvcResult failed = cancellations.cancel("customer1", number, cancelBody("NO_LONGER_NEEDED", "2027-02-09"));
        FailingTransitionWrite.ARMED.set(false);

        assertThat(FailingTransitionWrite.REFUND_ROWS_SEEN.get())
                .as("the refund insert had reached the database when the transition write failed")
                .isEqualTo(refundRowsBefore + 1L);
        assertThat(failed.getResponse().getStatus()).isEqualTo(500);
        assertThat(failed.getResponse().getContentAsString()).as("a fixed problem, no internals")
                .doesNotContain("injected").doesNotContain(number);
        assertThat(CancellationRows.refunds(jdbc, number)).as("no refund row").isEmpty();
        assertThat(CancellationRows.totalRefunds(jdbc)).isEqualTo(refundRowsBefore);
        assertThat(PolicyRows.policy(jdbc, number)).containsEntry("STATUS", "ACTIVE");
        assertThat(PolicyRows.transitions(jdbc, number)).as("the transition count is unchanged").hasSize(1);
        assertThat(snapshot(number)).as("every count, the status and the current version are unchanged")
                .isEqualTo(before);
        assertThat(detail(number).get("refunds")).isEmpty();

        JsonNode retried = cancellations.cancelOk(number, "2027-02-09");
        assertThat(retried.get("refund").get("amount").asText()).isEqualTo("4473.29");
        assertThat(retried.get("policy").get("status").asText()).isEqualTo("CANCELLED");
        assertThat(CancellationRows.refunds(jdbc, number)).hasSize(1);
    }
}
