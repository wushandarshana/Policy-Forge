package com.horizon.policyforge.service;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Recognises a lost race on a unique key. Two requests that compute the same next number (an endorsement's sequence, a
 * policy version, a term) both reach the insert, and the second one hits the unique key: the service then answers 409
 * {@code POLICY_UPDATE_CONFLICT} with nothing written, instead of a 500.
 */
final class WriteConflicts {

    /** The SQL state of a unique-key violation, in H2 and in PostgreSQL. */
    private static final String UNIQUE_VIOLATION = "23505";

    private WriteConflicts() {
    }

    /** Whether the failure is a unique-key violation, as opposed to any other broken constraint. */
    static boolean isUniqueViolation(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
