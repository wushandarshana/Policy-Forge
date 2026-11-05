package com.horizon.policyforge.domain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.underwriting.UnderwritingOutcome;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The application status lifecycle (AC-03.1 at domain level, P-4). */
@Tag("AC-03")
class ApplicationLifecycleTest {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
            ApplicationStatus.SUBMITTED, EnumSet.of(ApplicationStatus.UNDERWRITING),
            ApplicationStatus.UNDERWRITING,
            EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.REFERRED, ApplicationStatus.DECLINED),
            ApplicationStatus.REFERRED, EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.DECLINED),
            ApplicationStatus.DECLINED, EnumSet.of(ApplicationStatus.APPROVED),
            ApplicationStatus.APPROVED, EnumSet.of(ApplicationStatus.ISSUED),
            ApplicationStatus.ISSUED, EnumSet.noneOf(ApplicationStatus.class));

    @Test
    @DisplayName("AC-03.1: the six statuses are SUBMITTED, UNDERWRITING, APPROVED, REFERRED, DECLINED and ISSUED")
    void sixStatuses() {
        assertThat(ApplicationStatus.values()).containsExactlyInAnyOrder(ApplicationStatus.SUBMITTED,
                ApplicationStatus.UNDERWRITING, ApplicationStatus.APPROVED, ApplicationStatus.REFERRED,
                ApplicationStatus.DECLINED, ApplicationStatus.ISSUED);
    }

    @Test
    @DisplayName("AC-03.1: a submitted application moves to UNDERWRITING before any outcome is applied")
    void submittedMovesToUnderwriting() {
        assertThat(ApplicationLifecycle.startUnderwriting(ApplicationStatus.SUBMITTED))
                .isEqualTo(ApplicationStatus.UNDERWRITING);
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = "SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("AC-03.1: only a SUBMITTED application can start underwriting")
    void onlySubmittedStartsUnderwriting(ApplicationStatus current) {
        assertThatThrownBy(() -> ApplicationLifecycle.startUnderwriting(current))
                .isInstanceOf(InvalidApplicationStateException.class);
    }

    @Test
    @DisplayName("AC-03.1: an outcome applied in UNDERWRITING maps AUTO_BIND, MANUAL_REVIEW, DECLINE to APPROVED, REFERRED, DECLINED")
    void outcomeMapsToStatus() {
        assertThat(ApplicationLifecycle.applyOutcome(ApplicationStatus.UNDERWRITING, UnderwritingOutcome.AUTO_BIND))
                .isEqualTo(ApplicationStatus.APPROVED);
        assertThat(ApplicationLifecycle.applyOutcome(ApplicationStatus.UNDERWRITING, UnderwritingOutcome.MANUAL_REVIEW))
                .isEqualTo(ApplicationStatus.REFERRED);
        assertThat(ApplicationLifecycle.applyOutcome(ApplicationStatus.UNDERWRITING, UnderwritingOutcome.DECLINE))
                .isEqualTo(ApplicationStatus.DECLINED);
    }

    @ParameterizedTest
    @EnumSource(UnderwritingOutcome.class)
    @DisplayName("AC-03.1: SUBMITTED cannot move directly to APPROVED, REFERRED or DECLINED")
    void submittedCannotSkipUnderwriting(UnderwritingOutcome outcome) {
        assertThatThrownBy(() -> ApplicationLifecycle.applyOutcome(ApplicationStatus.SUBMITTED, outcome))
                .isInstanceOfSatisfying(InvalidApplicationStateException.class, e -> {
                    assertThat(e.from()).isEqualTo(ApplicationStatus.SUBMITTED);
                });
        assertThat(ApplicationStatus.SUBMITTED.canTransitionTo(ApplicationStatus.APPROVED)).isFalse();
        assertThat(ApplicationStatus.SUBMITTED.canTransitionTo(ApplicationStatus.REFERRED)).isFalse();
        assertThat(ApplicationStatus.SUBMITTED.canTransitionTo(ApplicationStatus.DECLINED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = "UNDERWRITING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("AC-03.1: an outcome is applied only to an application in UNDERWRITING")
    void outcomeOnlyFromUnderwriting(ApplicationStatus current) {
        for (UnderwritingOutcome outcome : UnderwritingOutcome.values()) {
            assertThatThrownBy(() -> ApplicationLifecycle.applyOutcome(current, outcome))
                    .isInstanceOf(InvalidApplicationStateException.class);
        }
    }

    @Test
    @DisplayName("AC-03.1: the transition table is exactly the spec's: every other pair raises InvalidApplicationStateException")
    void transitionTableIsExact() {
        for (ApplicationStatus from : ApplicationStatus.values()) {
            assertThat(from.allowedTargets()).as("targets of %s", from).containsExactlyInAnyOrderElementsOf(ALLOWED.get(from));
            for (ApplicationStatus to : ApplicationStatus.values()) {
                if (ALLOWED.get(from).contains(to)) {
                    assertThat(from.canTransitionTo(to)).isTrue();
                    assertThat(ApplicationLifecycle.transition(from, to)).isEqualTo(to);
                } else {
                    assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isFalse();
                    assertThatThrownBy(() -> ApplicationLifecycle.transition(from, to))
                            .isInstanceOfSatisfying(InvalidApplicationStateException.class, e -> {
                                assertThat(e.from()).isEqualTo(from);
                                assertThat(e.to()).isEqualTo(to);
                            });
                }
            }
        }
    }

    @Test
    @DisplayName("AC-03.1: APPROVED moves to ISSUED and nothing leaves ISSUED")
    void issuance() {
        assertThat(ApplicationLifecycle.issue(ApplicationStatus.APPROVED)).isEqualTo(ApplicationStatus.ISSUED);
        for (ApplicationStatus current : ApplicationStatus.values()) {
            if (current != ApplicationStatus.APPROVED) {
                assertThatThrownBy(() -> ApplicationLifecycle.issue(current))
                        .isInstanceOf(InvalidApplicationStateException.class);
            }
        }
        assertThat(ApplicationStatus.ISSUED.allowedTargets()).isEmpty();
    }

    @Test
    @DisplayName("AC-03.1: a null current status or outcome fails with a domain exception, never a pass")
    void nullsAreRefused() {
        assertThatThrownBy(() -> ApplicationLifecycle.applyOutcome(ApplicationStatus.UNDERWRITING, null))
                .isInstanceOf(com.horizon.policyforge.domain.PolicyForgeDomainException.class);
        assertThatThrownBy(() -> ApplicationLifecycle.applyOutcome(null, UnderwritingOutcome.AUTO_BIND))
                .isInstanceOf(com.horizon.policyforge.domain.PolicyForgeDomainException.class);
    }
}
