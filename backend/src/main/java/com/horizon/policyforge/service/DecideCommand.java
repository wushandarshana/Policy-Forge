package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.application.ManualDecision;
import java.util.List;

/**
 * An underwriter's decision request, already mapped from the wire shape.
 *
 * @param actorId       the authenticated username of the decider, never a value from the body (KD-008)
 * @param applicationId the application as written in the path
 * @param decision      APPROVE or DECLINE, or null when the body had no recognised decision (the domain rejects it)
 * @param reasonCode    the submitted reason code, or null
 * @param comment       the submitted comment, or null
 * @param unknownKeys   the keys the body carried that the contract does not define
 */
public record DecideCommand(String actorId, String applicationId, ManualDecision decision, String reasonCode,
        String comment, List<String> unknownKeys) {

    public DecideCommand {
        unknownKeys = unknownKeys == null ? List.of() : List.copyOf(unknownKeys);
    }

    /** The application ID only: the comment is free text and is never printed (NFR-03). */
    @Override
    public String toString() {
        return "DecideCommand[applicationId=" + applicationId + "]";
    }
}
