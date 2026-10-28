package com.horizon.policyforge.domain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horizon.policyforge.domain.underwriting.ReasonCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The manual decision and override rules of U-10 (AC-09.4, AC-09.5, AC-09.6 at domain level). */
@Tag("AC-09")
class ManualDecisionRulesTest {

    private static final String COMMENT = "Reviewed the file";

    private static List<DecisionViolation> violationsOf(Runnable action) {
        try {
            action.run();
        } catch (InvalidDecisionException e) {
            return e.violations();
        }
        throw new AssertionError("expected InvalidDecisionException");
    }

    private static List<String> fieldsOf(Runnable action) {
        return violationsOf(action).stream().map(DecisionViolation::field).toList();
    }

    @Test
    @DisplayName("AC-09.6: the catalogue has the spec's codes and descriptions")
    void catalogue() {
        assertThat(ManualReasonCatalogue.forDecision(ManualDecision.APPROVE)).containsExactly(
                new ReasonCode("UW-MAN-001", "Acceptable after review"),
                new ReasonCode("UW-MAN-002", "Additional evidence satisfactory"));
        assertThat(ManualReasonCatalogue.forDecision(ManualDecision.DECLINE))
                .containsExactly(new ReasonCode("UW-MAN-010", "Risk outside appetite"));
        assertThat(ManualReasonCatalogue.overrideCodes()).containsExactly(
                new ReasonCode("UW-OVR-001", "Commercial exception approved"),
                new ReasonCode("UW-OVR-002", "Evidence rebuts decline reason"));
        assertThat(ManualReasonCatalogue.all()).hasSize(5);
        assertThat(ManualReasonCatalogue.find("UW-MAN-010")).contains(new ReasonCode("UW-MAN-010",
                "Risk outside appetite"));
        assertThat(ManualReasonCatalogue.find("UW-XXX-999")).isEmpty();
        assertThat(ManualReasonCatalogue.find(null)).isEmpty();
    }

    @Test
    @DisplayName("AC-09.6: APPROVE with UW-MAN-001 or UW-MAN-002 and DECLINE with UW-MAN-010 are accepted from REFERRED")
    void pairedCodesAreAccepted() {
        ManualDecisionOutcome approve1 =
                ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE, "UW-MAN-001", COMMENT);
        ManualDecisionOutcome approve2 =
                ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE, "UW-MAN-002", COMMENT);
        ManualDecisionOutcome decline =
                ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.DECLINE, "UW-MAN-010", COMMENT);

        assertThat(approve1.newStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(approve1.reason().code()).isEqualTo("UW-MAN-001");
        assertThat(approve1.reason().description()).isEqualTo("Acceptable after review");
        assertThat(approve2.newStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(decline.newStatus()).isEqualTo(ApplicationStatus.DECLINED);
        assertThat(decline.reason().code()).isEqualTo("UW-MAN-010");
    }

    @Test
    @DisplayName("AC-09.6: APPROVE with UW-MAN-010, DECLINE with UW-MAN-001 and a UW-OVR code are each rejected naming reasonCode")
    void wrongPairingIsRejected() {
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE,
                "UW-MAN-010", COMMENT))).containsExactly("reasonCode");
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.DECLINE,
                "UW-MAN-001", COMMENT))).containsExactly("reasonCode");
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.DECLINE,
                "UW-MAN-002", COMMENT))).containsExactly("reasonCode");
        for (ManualDecision decision : ManualDecision.values()) {
            for (String code : List.of("UW-OVR-001", "UW-OVR-002")) {
                assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, decision, code,
                        COMMENT))).containsExactly("reasonCode");
            }
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "UW-MAN-999", "uw-man-001", "UW-OK", "UW-TL-001"})
    @DisplayName("AC-09.6: a missing or unknown reason code is rejected naming reasonCode, with no transition")
    void unknownReasonCodeIsRejected(String code) {
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE, code,
                COMMENT))).containsExactly("reasonCode");
        assertThat(fieldsOf(() -> ManualDecisionRules.override(ApplicationStatus.DECLINED, code, COMMENT)))
                .containsExactly("reasonCode");
    }

    @Test
    @DisplayName("AC-09.6: a missing decision is rejected naming decision")
    void missingDecisionIsRejected() {
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, null, "UW-MAN-001", COMMENT)))
                .containsExactly("decision");
    }

    @Test
    @DisplayName("AC-09.6: the override accepts UW-OVR-001 and UW-OVR-002 only, and rejects the manual codes")
    void overrideCodesOnly() {
        assertThat(ManualDecisionRules.override(ApplicationStatus.DECLINED, "UW-OVR-001", COMMENT).reason().code())
                .isEqualTo("UW-OVR-001");
        assertThat(ManualDecisionRules.override(ApplicationStatus.DECLINED, "UW-OVR-002", COMMENT).reason()
                .description()).isEqualTo("Evidence rebuts decline reason");
        for (String code : List.of("UW-MAN-001", "UW-MAN-002", "UW-MAN-010")) {
            assertThat(fieldsOf(() -> ManualDecisionRules.override(ApplicationStatus.DECLINED, code, COMMENT)))
                    .containsExactly("reasonCode");
        }
    }

    @Test
    @DisplayName("AC-09.4: an override from DECLINED gives APPROVED with the trimmed comment")
    void overrideGivesApproved() {
        ManualDecisionOutcome outcome = ManualDecisionRules.override(ApplicationStatus.DECLINED, "UW-OVR-001",
                "  Commercial exception signed off  ");

        assertThat(outcome.newStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(outcome.comment()).isEqualTo("Commercial exception signed off");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "          ", "123456789", "  123456789  "})
    @DisplayName("AC-09.4: a missing, blank or 9-character comment (after trimming) is rejected naming comment")
    void shortCommentIsRejected(String comment) {
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE,
                "UW-MAN-001", comment))).containsExactly("comment");
        assertThat(fieldsOf(() -> ManualDecisionRules.override(ApplicationStatus.DECLINED, "UW-OVR-001", comment)))
                .containsExactly("comment");
    }

    @Test
    @DisplayName("AC-09.4: a 2001-character comment is rejected, 2000 characters and 10 characters are accepted")
    void commentLengthBounds() {
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE,
                "UW-MAN-001", "a".repeat(2001)))).containsExactly("comment");
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE,
                "UW-MAN-001", " " + "a".repeat(2001) + " "))).containsExactly("comment");

        assertThat(ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE, "UW-MAN-001",
                "a".repeat(2000)).comment()).hasSize(2000);
        assertThat(ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE, "UW-MAN-001",
                "1234567890").comment()).isEqualTo("1234567890");
        assertThat(ManualDecisionRules.decide(ApplicationStatus.REFERRED, ManualDecision.APPROVE, "UW-MAN-001",
                "  1234567890  ").comment()).isEqualTo("1234567890");
        assertThat(ManualDecisionRules.MIN_COMMENT_LENGTH).isEqualTo(10);
        assertThat(ManualDecisionRules.MAX_COMMENT_LENGTH).isEqualTo(2000);
    }

    @Test
    @DisplayName("AC-09.4: a bad comment and a bad reason code are reported together, and violations never echo input")
    void violationsAreReportedTogetherWithoutEchoingInput() {
        List<DecisionViolation> violations = violationsOf(() -> ManualDecisionRules.decide(ApplicationStatus.REFERRED,
                ManualDecision.APPROVE, "UW-MAN-010", "short"));

        assertThat(violations).extracting(DecisionViolation::field).containsExactlyInAnyOrder("reasonCode", "comment");
        for (DecisionViolation v : violations) {
            assertThat(v.message()).doesNotContain("UW-MAN-010", "short");
        }
    }

    @Test
    @DisplayName("AC-09.4: invalid input is reported before the application state (C3 order: 400, then 409)")
    void inputIsCheckedBeforeState() {
        assertThat(fieldsOf(() -> ManualDecisionRules.override(ApplicationStatus.APPROVED, "UW-OVR-001", "short")))
                .containsExactly("comment");
        assertThat(fieldsOf(() -> ManualDecisionRules.decide(ApplicationStatus.APPROVED, ManualDecision.APPROVE,
                "UW-MAN-010", COMMENT))).containsExactly("reasonCode");
    }

    @Test
    @DisplayName("AC-09.5: an override on APPROVED, REFERRED or UNDERWRITING raises the invalid-application-state exception")
    void overrideOnlyFromDeclined() {
        for (ApplicationStatus current : List.of(ApplicationStatus.APPROVED, ApplicationStatus.REFERRED,
                ApplicationStatus.UNDERWRITING, ApplicationStatus.SUBMITTED, ApplicationStatus.ISSUED)) {
            assertThatThrownBy(() -> ManualDecisionRules.override(current, "UW-OVR-001", COMMENT))
                    .isInstanceOfSatisfying(InvalidApplicationStateException.class, e -> {
                        assertThat(e.from()).isEqualTo(current);
                        assertThat(e.to()).isEqualTo(ApplicationStatus.APPROVED);
                    });
        }
    }

    @Test
    @DisplayName("AC-09.5: a decision on any status except REFERRED raises the invalid-application-state exception")
    void decisionOnlyFromReferred() {
        for (ApplicationStatus current : ApplicationStatus.values()) {
            if (current == ApplicationStatus.REFERRED) {
                continue;
            }
            assertThatThrownBy(() -> ManualDecisionRules.decide(current, ManualDecision.APPROVE, "UW-MAN-001",
                    COMMENT)).isInstanceOf(InvalidApplicationStateException.class);
            assertThatThrownBy(() -> ManualDecisionRules.decide(current, ManualDecision.DECLINE, "UW-MAN-010",
                    COMMENT)).isInstanceOf(InvalidApplicationStateException.class);
        }
    }
}
