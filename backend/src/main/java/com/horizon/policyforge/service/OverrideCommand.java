package com.horizon.policyforge.service;

import java.util.List;

/**
 * An admin's override request, already mapped from the wire shape.
 *
 * @param actorId       the authenticated username of the admin, never a value from the body (KD-008)
 * @param applicationId the application as written in the path
 * @param reasonCode    the submitted reason code, or null
 * @param comment       the submitted comment, or null
 * @param unknownKeys   the keys the body carried that the contract does not define
 */
public record OverrideCommand(String actorId, String applicationId, String reasonCode, String comment,
        List<String> unknownKeys) {

    public OverrideCommand {
        unknownKeys = unknownKeys == null ? List.of() : List.copyOf(unknownKeys);
    }

    /** The application ID only: the comment is free text and is never printed (NFR-03). */
    @Override
    public String toString() {
        return "OverrideCommand[applicationId=" + applicationId + "]";
    }
}
