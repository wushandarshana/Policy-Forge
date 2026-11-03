package com.horizon.policyforge.support;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads what endorsing left in the database, so the tests can prove both what was written and, after a refused request,
 * that nothing was. Column keys are upper case, as H2 returns them.
 */
public final class EndorsementRows {

    private EndorsementRows() {
    }

    /** Every endorsement of the policy, lowest sequence first. */
    public static List<Map<String, Object>> endorsements(JdbcTemplate jdbc, String policyNumber) {
        return jdbc.queryForList("select sequence_no, type, payload_json, before_version_no, after_version_no,"
                + " new_annual_premium, adjustment, effective_date, actor_id, created_at from endorsement"
                + " where policy_number = ? order by sequence_no", policyNumber);
    }

    /** The row counts, across all policies, and the status and version number of one policy. */
    public record Snapshot(int endorsements, int versions, int transitions, int terms, String status,
            int currentVersionNo) {
    }

    /** What a refused or failed request must leave unchanged. */
    public static Snapshot snapshot(JdbcTemplate jdbc, String policyNumber) {
        Map<String, Object> policy = PolicyRows.policy(jdbc, policyNumber);
        return new Snapshot(
                jdbc.queryForObject("select count(*) from endorsement", Integer.class),
                jdbc.queryForObject("select count(*) from policy_version", Integer.class),
                jdbc.queryForObject("select count(*) from policy_status_transition", Integer.class),
                jdbc.queryForObject("select count(*) from policy_term", Integer.class),
                (String) policy.get("STATUS"),
                ((Number) policy.get("CURRENT_VERSION_NO")).intValue());
    }
}
