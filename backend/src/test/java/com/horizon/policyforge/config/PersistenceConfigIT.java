package com.horizon.policyforge.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/** NFR-05: schema changes go through Flyway only; Hibernate validates and never generates DDL. */
@Tag("NFR-05")
@SpringBootTest
class PersistenceConfigIT {

    @Autowired
    private Environment environment;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("NFR-05 baseline: Hibernate only validates the schema (no auto-DDL) and keeps no session open in views")
    void hibernateValidatesOnly() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    @DisplayName("NFR-05 baseline: Flyway runs on startup against H2 with validate-on-migrate enabled")
    void flywayRunsWithValidateOnMigrate() {
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(flyway.getConfiguration().isValidateOnMigrate()).isTrue();
        Integer historyTables = new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from information_schema.tables where upper(table_name) = 'FLYWAY_SCHEMA_HISTORY'",
                Integer.class);
        assertThat(historyTables).as("flyway_schema_history table created by startup migration").isEqualTo(1);
    }
}
