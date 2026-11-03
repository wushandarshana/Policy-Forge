package com.horizon.policyforge.support;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads what issuance left in the database, so the tests can prove both what was written and, after a refused request,
 * that nothing was. Column keys are upper case, as H2 returns them.
 */
public final class PolicyRows {

    private PolicyRows() {
    }

    /** How many policies exist in all. */
    public static int policies(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from policy", Integer.class);
    }

    /** How many policies were issued for the application (never more than one, C7). */
    public static int policiesForApplication(JdbcTemplate jdbc, String applicationId) {
        return jdbc.queryForObject("select count(*) from policy where application_id = ?", Integer.class,
                applicationId);
    }

    /** The number of the policy issued for the application. */
    public static String numberFor(JdbcTemplate jdbc, String applicationId) {
        return jdbc.queryForObject("select policy_number from policy where application_id = ?", String.class,
                applicationId);
    }

    /** The policy row. */
    public static Map<String, Object> policy(JdbcTemplate jdbc, String policyNumber) {
        return jdbc.queryForMap("select policy_number, application_id, product_code, customer_id, status,"
                + " current_version_no, issued_at from policy where policy_number = ?", policyNumber);
    }

    /** Every version of the policy, lowest first. */
    public static List<Map<String, Object>> versions(JdbcTemplate jdbc, String policyNumber) {
        return jdbc.queryForList("select version_no, sum_insured, annual_premium, address_line1,"
                + " address_city, address_postcode, rule_version, effective_date, expiry_date,"
                + " created_at from policy_version where policy_number = ? order by version_no", policyNumber);
    }

    /** The stored risk inputs of one version: the full snapshot, health inputs included. */
    public static String riskInputsJson(JdbcTemplate jdbc, String policyNumber, int versionNo) {
        return jdbc.queryForObject("select risk_inputs_json from policy_version where policy_number = ?"
                + " and version_no = ?", String.class, policyNumber, versionNo);
    }

    /** The stored nominees of one version, as JSON text. */
    public static String nomineesJson(JdbcTemplate jdbc, String policyNumber, int versionNo) {
        return jdbc.queryForObject("select nominees_json from policy_version where policy_number = ?"
                + " and version_no = ?", String.class, policyNumber, versionNo);
    }

    /** Every term of the policy, lowest first. */
    public static List<Map<String, Object>> terms(JdbcTemplate jdbc, String policyNumber) {
        return jdbc.queryForList("select term_no, start_date, end_date, premium, paid from policy_term"
                + " where policy_number = ? order by term_no", policyNumber);
    }

    /** Every status transition of the policy, in append order. */
    public static List<Map<String, Object>> transitions(JdbcTemplate jdbc, String policyNumber) {
        return jdbc.queryForList("select from_status, to_status, reason, actor_id, occurred_at"
                + " from policy_status_transition where policy_number = ? order by id", policyNumber);
    }

    /** The row counts a refused issue must leave unchanged. */
    public record Snapshot(int policies, int versions, int terms, int transitions, int auditEntries,
            String applicationStatus) {
    }

    public static Snapshot snapshot(JdbcTemplate jdbc, String applicationId) {
        return new Snapshot(
                policies(jdbc),
                jdbc.queryForObject("select count(*) from policy_version", Integer.class),
                jdbc.queryForObject("select count(*) from policy_term", Integer.class),
                jdbc.queryForObject("select count(*) from policy_status_transition", Integer.class),
                ApplicationRows.audit(jdbc, applicationId).size(),
                ApplicationRows.status(jdbc, applicationId));
    }
}
