package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.repository.PolicyVersionRepository;
import com.horizon.policyforge.support.EndorsementRows;
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
 * AC-06.3: the endorsement, its version and the policy change are written atomically. The failure is injected by test
 * configuration only: a {@code @Primary} wrapper around the policy version repository throws when it is asked to save,
 * which is after the endorsement insert has reached the database and before the version insert. Production code has no
 * hook for this. After the failure no endorsement and no version 2 exist and the policy is untouched.
 */
@Tag("AC-06")
@Import(EndorsementAtomicityIT.FailingVersionWrite.class)
class EndorsementAtomicityIT extends EndorsementTestSupport {

    /** Replaces the policy version repository with one that fails its next save while armed. */
    @TestConfiguration
    static class FailingVersionWrite {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        /** How many endorsement rows the failing transaction could see at the moment it failed. */
        static final AtomicLong ENDORSEMENT_ROWS_SEEN = new AtomicLong(-1);

        @Bean
        @Primary
        PolicyVersionRepository failingPolicyVersionRepository(
                @Qualifier("policyVersionRepository") PolicyVersionRepository real, EntityManager entityManager) {
            return (PolicyVersionRepository) Proxy.newProxyInstance(PolicyVersionRepository.class.getClassLoader(),
                    new Class<?>[] {PolicyVersionRepository.class}, (proxy, method, args) -> {
                        if (ARMED.get() && method.getName().equals("save")) {
                            Number seen = (Number) entityManager
                                    .createNativeQuery("select count(*) from endorsement").getSingleResult();
                            ENDORSEMENT_ROWS_SEEN.set(seen.longValue());
                            throw new IllegalStateException("injected failure at the version write");
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
        FailingVersionWrite.ARMED.set(false);
    }

    @Test
    @DisplayName("AC-06.3: a failure after the endorsement insert and before the version insert leaves no endorsement and no version 2, and the status, current version and transitions are unchanged; the same request then succeeds")
    void aFailureAfterTheEndorsementInsertRollsEverythingBack() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);
        FailingVersionWrite.ENDORSEMENT_ROWS_SEEN.set(-1);

        FailingVersionWrite.ARMED.set(true);
        MvcResult failed = endorsements.endorse("customer1", number, newAddressBody(null));
        FailingVersionWrite.ARMED.set(false);

        assertThat(FailingVersionWrite.ENDORSEMENT_ROWS_SEEN.get())
                .as("the endorsement insert had reached the database when the version write failed").isEqualTo(1);
        assertThat(failed.getResponse().getStatus()).isEqualTo(500);
        assertThat(EndorsementRows.endorsements(jdbc, number)).as("no endorsement row").isEmpty();
        assertThat(PolicyRows.versions(jdbc, number)).as("no version 2").hasSize(1);
        assertThat(PolicyRows.policy(jdbc, number)).containsEntry("STATUS", "ACTIVE");
        assertThat(PolicyRows.transitions(jdbc, number)).as("the transition count is unchanged").hasSize(1);
        assertThat(snapshot(number)).as("every count, the status and the current version are unchanged")
                .isEqualTo(before);

        JsonNode retried = endorsements.endorseOk(number, newAddressBody(null));
        assertThat(retried.get("endorsement").get("sequenceNo").asInt()).as("the rolled-back number is reused")
                .isEqualTo(1);
        assertThat(retried.get("endorsement").get("afterVersionNo").asInt()).isEqualTo(2);
    }
}
