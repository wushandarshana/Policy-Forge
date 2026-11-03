package com.horizon.policyforge.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.endorsement.Address;
import com.horizon.policyforge.domain.policy.InvalidPolicyStateException;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The policy aggregate's own rules: its status and current version change only through {@code applyTransition}, which
 * applies the domain state machine; a term changes only through {@code markPaid}; and no entity prints personal data.
 * Plain unit tests, no Spring context.
 */
@Tag("AC-10")
@Tag("NFR-02")
@Tag("NFR-03")
class PolicyEntityTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    private static PolicyEntity newPolicy() {
        return PolicyEntity.issue("PF-TL-2026-000001", "A-1", "TERM_LIFE", "customer1", NOW);
    }

    @Test
    @DisplayName("AC-10.4: a new policy is ACTIVE at version 1, and an allowed transition moves the status and the current version together")
    void issueThenEndorsedMovesStatusAndVersion() {
        PolicyEntity policy = newPolicy();

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(policy.getCurrentVersionNo()).isEqualTo(1);

        policy.applyTransition(PolicyStatus.ENDORSED, 2);
        policy.applyTransition(PolicyStatus.ENDORSED, 3);

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.ENDORSED);
        assertThat(policy.getCurrentVersionNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-10.2: a transition the state machine forbids from CANCELLED throws InvalidPolicyStateException and leaves the status and version unchanged")
    void forbiddenTransitionLeavesThePolicyUnchanged() {
        PolicyEntity policy = newPolicy();
        policy.applyTransition(PolicyStatus.CANCELLED, 1);

        assertThatThrownBy(() -> policy.applyTransition(PolicyStatus.ENDORSED, 2))
                .isInstanceOfSatisfying(InvalidPolicyStateException.class, invalid -> {
                    assertThat(invalid.from()).isEqualTo(PolicyStatus.CANCELLED);
                    assertThat(invalid.to()).isEqualTo(PolicyStatus.ENDORSED);
                });

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.CANCELLED);
        assertThat(policy.getCurrentVersionNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-10.3: a LAPSED policy cannot be moved to CANCELLED, and ACTIVE can be moved to LAPSED the way test setup does (P-6)")
    void lapsedPolicyCannotBeCancelled() {
        PolicyEntity policy = newPolicy();
        policy.applyTransition(PolicyStatus.LAPSED, 1);

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.LAPSED);
        assertThatThrownBy(() -> policy.applyTransition(PolicyStatus.CANCELLED, 1))
                .isInstanceOf(InvalidPolicyStateException.class);
        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.LAPSED);
    }

    @Test
    @DisplayName("AC-10.4: the current version number never goes back, and a refused move changes nothing")
    void currentVersionNeverGoesBack() {
        PolicyEntity policy = newPolicy();
        policy.applyTransition(PolicyStatus.ENDORSED, 2);

        assertThatThrownBy(() -> policy.applyTransition(PolicyStatus.ENDORSED, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.ENDORSED);
        assertThat(policy.getCurrentVersionNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-05.1: markPaid is the only change a policy term allows: it sets the paid flag")
    void markPaidSetsThePaidFlag() {
        PolicyTermEntity term = PolicyTermEntity.of("PF-TL-2026-000001", 2, LocalDate.parse("2027-11-01"),
                LocalDate.parse("2028-10-31"), new BigDecimal("6850.00"), false);

        assertThat(term.isPaid()).isFalse();
        term.markPaid();

        assertThat(term.isPaid()).isTrue();
    }

    @Test
    @DisplayName("NFR-03.1: the string form of a policy, a version, a term and a transition shows IDs only, never the address or the risk inputs")
    void stringFormsShowNoPersonalData() {
        PolicyVersionEntity version = PolicyVersionEntity.of("PF-TL-2026-000001", 1, new BigDecimal("5000000.00"),
                new BigDecimal("6850.00"), "{\"bmi\":\"27.3189\"}", new Address("1 Demo Street", "Pune", "411001"),
                "[]", 1, LocalDate.parse("2026-11-01"), LocalDate.parse("2027-10-31"), NOW);
        PolicyStatusTransitionEntity transition = PolicyStatusTransitionEntity.of("PF-TL-2026-000001", null,
                PolicyStatus.ACTIVE, "ISSUANCE", "customer1", NOW);

        String printed = newPolicy() + " " + version + " " + transition + " " + PolicyTermEntity.of(
                "PF-TL-2026-000001", 1, LocalDate.parse("2026-11-01"), LocalDate.parse("2027-10-31"),
                new BigDecimal("6850.00"), true);

        assertThat(printed).contains("PF-TL-2026-000001");
        assertThat(printed).doesNotContain("1 Demo Street", "Pune", "411001", "27.3189", "bmi");
        assertThat(version.getAddress().toString()).doesNotContain("1 Demo Street");
    }
}
