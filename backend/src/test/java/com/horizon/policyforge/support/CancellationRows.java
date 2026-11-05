package com.horizon.policyforge.support;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads what a cancellation left in the database, so the tests can prove both what was written and, after a refused,
 * failed or lost request, that nothing was. Column keys are upper case, as H2 returns them.
 */
public final class CancellationRows {

    private CancellationRows() {
    }

    /** Every refund record of the policy, in the order they were appended (there is at most one). */
    public static List<Map<String, Object>> refunds(JdbcTemplate jdbc, String policyNumber) {
        return jdbc.queryForList("select term_no, cancellation_date, premium, term_days, unexpired_days, cooling_off,"
                + " fee, amount, formula, actor_id, created_at from refund_record where policy_number = ?"
                + " order by id", policyNumber);
    }

    /** How many refund records exist, across all policies. */
    public static int totalRefunds(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from refund_record", Integer.class);
    }

    /** The row counts of one policy (and of the refund table), its status and its current version. */
    public record Snapshot(int totalRefunds, int policyRefunds, int terms, int versions, int transitions,
            int endorsements, int auditEntries, String status, int currentVersionNo) {
    }

    /** What a refused, failed or lost request must leave unchanged. */
    public static Snapshot snapshot(JdbcTemplate jdbc, String policyNumber) {
        Map<String, Object> policy = PolicyRows.policy(jdbc, policyNumber);
        return new Snapshot(
                totalRefunds(jdbc),
                refunds(jdbc, policyNumber).size(),
                PolicyRows.terms(jdbc, policyNumber).size(),
                PolicyRows.versions(jdbc, policyNumber).size(),
                PolicyRows.transitions(jdbc, policyNumber).size(),
                EndorsementRows.endorsements(jdbc, policyNumber).size(),
                jdbc.queryForObject("select count(*) from audit_entry", Integer.class),
                (String) policy.get("STATUS"),
                ((Number) policy.get("CURRENT_VERSION_NO")).intValue());
    }
}
