package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.underwriting.ReasonCode;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules for manual decisions and overrides (U-10). Input is checked first, then the application's state, so a
 * caller can map the failures in the C3 order: 400, then 409. Messages are fixed text and never echo the input.
 */
public final class ManualDecisionRules {

    public static final int MIN_COMMENT_LENGTH = 10;
    public static final int MAX_COMMENT_LENGTH = 2000;

    private static final String REASON_CODE = "reasonCode";
    private static final String COMMENT = "comment";

    private ManualDecisionRules() {
    }

    /** An underwriter decision. Valid only from REFERRED: APPROVE gives APPROVED and DECLINE gives DECLINED. */
    public static ManualDecisionOutcome decide(
            ApplicationStatus current, ManualDecision decision, String reasonCode, String comment) {
        List<DecisionViolation> violations = new ArrayList<>();
        if (decision == null) {
            violations.add(new DecisionViolation("decision", "is required"));
        }
        ReasonCode reason = decision == null
                ? checkManualCode(violations, reasonCode)
                : checkCode(violations, reasonCode, ManualReasonCatalogue.forDecision(decision));
        String trimmed = checkComment(violations, comment);
        failIfAny(violations);

        ApplicationStatus target = decision == ManualDecision.APPROVE ? ApplicationStatus.APPROVED
                : ApplicationStatus.DECLINED;
        if (current != ApplicationStatus.REFERRED) {
            throw new InvalidApplicationStateException(current, target);
        }
        return new ManualDecisionOutcome(ApplicationLifecycle.transition(current, target), reason, trimmed);
    }

    /** An admin override. Valid only from DECLINED, and gives APPROVED. */
    public static ManualDecisionOutcome override(ApplicationStatus current, String reasonCode, String comment) {
        List<DecisionViolation> violations = new ArrayList<>();
        ReasonCode reason = checkCode(violations, reasonCode, ManualReasonCatalogue.overrideCodes());
        String trimmed = checkComment(violations, comment);
        failIfAny(violations);

        if (current != ApplicationStatus.DECLINED) {
            throw new InvalidApplicationStateException(current, ApplicationStatus.APPROVED);
        }
        return new ManualDecisionOutcome(
                ApplicationLifecycle.transition(current, ApplicationStatus.APPROVED), reason, trimmed);
    }

    private static ReasonCode checkCode(List<DecisionViolation> violations, String code, List<ReasonCode> allowed) {
        if (code == null) {
            violations.add(new DecisionViolation(REASON_CODE, "is required"));
            return null;
        }
        return allowed.stream().filter(reason -> reason.code().equals(code)).findFirst().orElseGet(() -> {
            violations.add(new DecisionViolation(REASON_CODE, "is not valid for this action"));
            return null;
        });
    }

    /** With no decision to pair against, the code must at least be a manual (UW-MAN) code. */
    private static ReasonCode checkManualCode(List<DecisionViolation> violations, String code) {
        if (code != null && ManualReasonCatalogue.isManualCode(code)) {
            return ManualReasonCatalogue.find(code).orElse(null);
        }
        violations.add(new DecisionViolation(REASON_CODE, code == null ? "is required" : "is not valid for this action"));
        return null;
    }

    private static String checkComment(List<DecisionViolation> violations, String comment) {
        String trimmed = comment == null ? null : comment.strip();
        if (trimmed == null || trimmed.length() < MIN_COMMENT_LENGTH || trimmed.length() > MAX_COMMENT_LENGTH) {
            violations.add(new DecisionViolation(COMMENT,
                    "must be between " + MIN_COMMENT_LENGTH + " and " + MAX_COMMENT_LENGTH + " characters"));
        }
        return trimmed;
    }

    private static void failIfAny(List<DecisionViolation> violations) {
        if (!violations.isEmpty()) {
            throw new InvalidDecisionException(violations);
        }
    }
}
