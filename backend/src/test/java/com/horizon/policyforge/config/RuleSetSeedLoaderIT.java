package com.horizon.policyforge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.horizon.policyforge.support.RuleSetFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.context.TestPropertySource;

/** Startup seed load (AC-02.1, NFR-05): inserts only what is absent and leaves existing rows unchanged on restart. */
@Tag("AC-02")
@Tag("NFR-05")
@SpringBootTest
@TestPropertySource(properties = "policyforge.test.context=seed-loader")
class RuleSetSeedLoaderIT {

    @Autowired
    private RuleSetSeedLoader loader;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-02.1: startup stores the three seed files in launch order, each exactly as shipped")
    void startupStoresSeedFilesAsShipped() {
        List<String> products = jdbc.queryForList("select product_code from rule_set_version order by id",
                String.class);
        assertThat(products).containsExactly("TERM_LIFE", "MOTOR", "HOUSEHOLD");
        assertThat(jdbc.queryForObject("select rules_json from rule_set_version where product_code = 'MOTOR'",
                String.class)).isEqualTo(RuleSetFixtures.seedText("motor", 1));
    }

    @Test
    @DisplayName("NFR-05 seed: running the loader again (a restart) inserts nothing and leaves every row unchanged")
    void restartLeavesRowsUnchanged() throws Exception {
        List<String> before = snapshot();

        assertThat(loader.load()).isZero();

        assertThat(snapshot()).hasSize(3).isEqualTo(before);
    }

    private List<String> snapshot() {
        return jdbc.query(
                "select id, product_code, version, rules_json, created_at from rule_set_version order by id",
                (rs, row) -> rs.getLong(1) + "|" + rs.getString(2) + "|" + rs.getInt(3) + "|" + rs.getString(4)
                        + "|" + rs.getObject(5));
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("NFR-05 seed: a seed file whose row is missing is inserted, and rows that exist are not touched")
    void insertsOnlyWhereAbsent() throws Exception {
        long motorId = jdbc.queryForObject("select id from rule_set_version where product_code = 'MOTOR'", Long.class);
        jdbc.update("delete from rule_set_version where product_code = 'MOTOR'");

        assertThat(loader.load()).isEqualTo(1);

        assertThat(jdbc.queryForObject("select count(*) from rule_set_version", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select id from rule_set_version where product_code = 'MOTOR'", Long.class))
                .isNotEqualTo(motorId);
        assertThat(jdbc.queryForObject("select id from rule_set_version where product_code = 'TERM_LIFE'", Long.class))
                .isLessThan(motorId);
    }
}
