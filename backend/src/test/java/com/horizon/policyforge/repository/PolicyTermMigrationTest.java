package com.horizon.policyforge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * AC-07.3 and NFR-05: migration V10 adds {@code policy_term.rule_version}, backfills it from each policy's version 1 (the
 * rule version the policy was issued under) and makes it NOT NULL, on a database that already holds V9 data. Existing
 * migrations stay byte-identical, so this runs the real files against a private in-memory database.
 */
@Tag("AC-07")
@Tag("NFR-05")
class PolicyTermMigrationTest {

    private static Flyway flyway(DataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }

    private static void insertPolicy(JdbcTemplate jdbc, String number, int[] versionRuleVersions, int termCount) {
        jdbc.update("insert into policy (policy_number, application_id, product_code, customer_id, status,"
                + " current_version_no, version, issued_at) values (?, ?, 'TERM_LIFE', 'customer1', 'ACTIVE', ?, 0,"
                + " current_timestamp)", number, "A-" + number, versionRuleVersions.length);
        for (int i = 0; i < versionRuleVersions.length; i++) {
            jdbc.update("insert into policy_version (policy_number, version_no, sum_insured, annual_premium,"
                    + " risk_inputs_json, address_line1, address_city, address_postcode, nominees_json, rule_version,"
                    + " effective_date, expiry_date, created_at) values (?, ?, 5000000.00, 6850.00, '{}',"
                    + " '1 Demo Street', 'Pune', '411001', '[]', ?, date '2026-11-01', date '2027-10-31',"
                    + " current_timestamp)", number, i + 1, versionRuleVersions[i]);
        }
        for (int termNo = 1; termNo <= termCount; termNo++) {
            jdbc.update("insert into policy_term (policy_number, term_no, start_date, end_date, premium, paid)"
                    + " values (?, ?, date '2026-11-01', date '2027-10-31', 6850.00, ?)", number, termNo, termNo == 1);
        }
    }

    @Test
    @DisplayName("AC-07.3: V10 adds a NOT NULL rule_version to policy_term and backfills every existing term from its policy's version 1")
    void v10BackfillsTheRuleVersionFromVersionOne() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:v10-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        flyway(dataSource, "9").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        // Version 1 was priced with rule version 3; a later version carries another (it must not be used).
        insertPolicy(jdbc, "PF-TL-2026-000001", new int[] {3, 4}, 2);
        insertPolicy(jdbc, "PF-TL-2026-000002", new int[] {1}, 1);

        flyway(dataSource, "latest").migrate();

        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name ="
                + " 'POLICY_TERM' and column_name = 'RULE_VERSION'", Integer.class)).as("the column exists")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_name ="
                + " 'POLICY_TERM' and column_name = 'RULE_VERSION'", String.class)).as("and is NOT NULL")
                .isEqualTo("NO");
        assertThat(jdbc.queryForObject("select count(*) from policy_term where policy_number = 'PF-TL-2026-000001'"
                + " and rule_version = 3", Integer.class)).as("both terms take version 1's rule version")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select rule_version from policy_term where policy_number ="
                + " 'PF-TL-2026-000002'", Integer.class)).isEqualTo(1);
    }
}
