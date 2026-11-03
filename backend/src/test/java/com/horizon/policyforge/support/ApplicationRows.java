package com.horizon.policyforge.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads what an application left in the database, so the underwriting tests can prove both what changed and, after a
 * refused request, that nothing did. Column keys are upper case, as H2 returns them.
 */
public final class ApplicationRows {

    private ApplicationRows() {
    }

    /** The stored status of an application. */
    public static String status(JdbcTemplate jdbc, String applicationId) {
        return jdbc.queryForObject("select status from application where application_id = ?", String.class,
                applicationId);
    }

    /** Every decision row of the application, oldest first. */
    public static List<Map<String, Object>> decisions(JdbcTemplate jdbc, String applicationId) {
        return jdbc.queryForList(
                "select source, outcome, reason_codes, reasons_json, rule_version, actor_id, comment_text, decided_at"
                        + " from underwriting_decision where application_id = ? order by id", applicationId);
    }

    /** Every audit entry of the application, oldest first. */
    public static List<Map<String, Object>> audit(JdbcTemplate jdbc, String applicationId) {
        return jdbc.queryForList(
                "select actor_id, action, entity_type, entity_id, reason_code, comment_text, previous_status,"
                        + " new_status, occurred_at from audit_entry where entity_type = 'APPLICATION'"
                        + " and entity_id = ? order by id", applicationId);
    }

    /** A timestamp column as an instant, whichever Java type the driver returns for TIMESTAMP WITH TIME ZONE. */
    public static Instant instant(Object column) {
        if (column instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (column instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("Not a timestamp column: " + column);
    }

    /** A snapshot of the three things a manual action touches, to compare before and after a refused request. */
    public record Snapshot(String status, int decisionRows, int auditRows) {
    }

    public static Snapshot snapshot(JdbcTemplate jdbc, String applicationId) {
        return new Snapshot(status(jdbc, applicationId), decisions(jdbc, applicationId).size(),
                audit(jdbc, applicationId).size());
    }
}
