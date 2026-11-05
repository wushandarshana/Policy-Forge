package com.horizon.policyforge.domain.policy;

import static com.horizon.policyforge.domain.policy.PolicyStatus.ACTIVE;
import static com.horizon.policyforge.domain.policy.PolicyStatus.CANCELLED;
import static com.horizon.policyforge.domain.policy.PolicyStatus.ENDORSED;
import static com.horizon.policyforge.domain.policy.PolicyStatus.LAPSED;
import static com.horizon.policyforge.domain.policy.PolicyStatus.RENEWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.PolicyForgeDomainException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The policy state machine against the table in specs/policy-issuance_spec.md (AC-10). The expected matrix below is
 * written out literally from the spec, independent of the implementation. A null {@code from} is a new policy.
 */
@Tag("AC-10")
class PolicyStateMachineTest {

    private static final PolicyStatus[] TO = {ACTIVE, ENDORSED, RENEWED, LAPSED, CANCELLED};

    /** Rows: (new), ACTIVE, ENDORSED, RENEWED, LAPSED, CANCELLED. Columns: ACTIVE, ENDORSED, RENEWED, LAPSED, CANCELLED. */
    private static final boolean[][] SPEC_TABLE = {
            {true, false, false, false, false},
            {false, true, true, true, true},
            {false, true, true, true, true},
            {false, true, true, true, true},
            {false, false, false, false, false},
            {false, false, false, false, false},
    };

    private static final PolicyStatus[] FROM = {null, ACTIVE, ENDORSED, RENEWED, LAPSED, CANCELLED};

    private final PolicyStateMachine machine = new PolicyStateMachine();

    static Stream<Arguments> matrix() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (int row = 0; row < FROM.length; row++) {
            for (int col = 0; col < TO.length; col++) {
                cases.add(Arguments.of(FROM[row], TO[col], SPEC_TABLE[row][col]));
            }
        }
        return cases.build();
    }

    @Test
    @Tag("AC-10.1")
    @DisplayName("AC-10.1: the spec table has 30 cells, 13 allowed and 17 forbidden")
    void tableShape() {
        assertThat(matrix().count()).isEqualTo(30);
        long allowed = matrix().filter(a -> (boolean) a.get()[2]).count();
        assertThat(allowed).isEqualTo(13);
    }

    @ParameterizedTest(name = "AC-10.1: {0} -> {1} allowed={2}")
    @Tag("AC-10.1")
    @MethodSource("matrix")
    void everyCellOfTheTransitionMatrix(PolicyStatus from, PolicyStatus to, boolean allowed) {
        assertThat(machine.canTransition(from, to)).isEqualTo(allowed);
        if (allowed) {
            assertThat(machine.transition(from, to)).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> machine.transition(from, to))
                    .isInstanceOf(InvalidPolicyStateException.class)
                    .isInstanceOf(PolicyForgeDomainException.class)
                    .satisfies(e -> {
                        InvalidPolicyStateException ex = (InvalidPolicyStateException) e;
                        assertThat(ex.from()).isEqualTo(from);
                        assertThat(ex.to()).isEqualTo(to);
                    });
        }
    }

    @Test
    @Tag("AC-10.1")
    @DisplayName("AC-10.1: ACTIVE, ENDORSED and RENEWED are in force, LAPSED and CANCELLED are terminal")
    void statusFlags() {
        for (PolicyStatus status : new PolicyStatus[] {ACTIVE, ENDORSED, RENEWED}) {
            assertThat(status.isInForce()).isTrue();
            assertThat(status.isTerminal()).isFalse();
        }
        for (PolicyStatus status : new PolicyStatus[] {LAPSED, CANCELLED}) {
            assertThat(status.isInForce()).isFalse();
            assertThat(status.isTerminal()).isTrue();
        }
    }

    @Test
    @Tag("AC-10.3")
    @DisplayName("AC-10.3: a LAPSED policy cannot be CANCELLED (domain rule; the API half is CancellationStateIT)")
    void lapsedPolicyCannotBeCancelled() {
        assertThatThrownBy(() -> machine.transition(LAPSED, CANCELLED))
                .isInstanceOf(InvalidPolicyStateException.class)
                .hasMessage("Invalid policy state transition: LAPSED -> CANCELLED");
    }
}
