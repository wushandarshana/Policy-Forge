package com.horizon.policyforge.support;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads what the renewal cycle and a renewal payment left in the database, so the tests can prove both what was written
 * and, after a refused request, that nothing was. Column keys are upper case, as H2 returns them.
 */
public final class RenewalRows {

    private RenewalRows() {
    }

    /** The audit entries of one action, in the order they were appended. */
    public static List<Map<String, Object>> audit(JdbcTemplate jdbc, String action) {
        return jdbc.queryForList("select actor_id, action, entity_type, entity_id, occurred_at from audit_entry"
                + " where action = ? order by id", action);
    }

    /** Every row count of one policy, its status and its current version, plus the audit total. */
    public record Snapshot(int terms, int paidTerms, int versions, int transitions, int auditEntries, String status,
            int currentVersionNo) {
    }

    /** What a refused or skipped request must leave unchanged. */
    public static Snapshot snapshot(JdbcTemplate jdbc, String policyNumber) {
        Map<String, Object> policy = PolicyRows.policy(jdbc, policyNumber);
        return new Snapshot(
                PolicyRows.terms(jdbc, policyNumber).size(),
                jdbc.queryForObject("select count(*) from policy_term where policy_number = ? and paid = true",
                        Integer.class, policyNumber),
                PolicyRows.versions(jdbc, policyNumber).size(),
                PolicyRows.transitions(jdbc, policyNumber).size(),
                jdbc.queryForObject("select count(*) from audit_entry", Integer.class),
                (String) policy.get("STATUS"),
                ((Number) policy.get("CURRENT_VERSION_NO")).intValue());
    }

    /** The status of the policy. */
    public static String status(JdbcTemplate jdbc, String policyNumber) {
        return (String) PolicyRows.policy(jdbc, policyNumber).get("STATUS");
    }
}
