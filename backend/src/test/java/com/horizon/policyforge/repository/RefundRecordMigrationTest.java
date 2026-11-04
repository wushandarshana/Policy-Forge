package com.horizon.policyforge.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * AC-08.4, NFR-02 and NFR-05: migration V11 adds the append-only {@code refund_record} table, unique on the policy
 * number (one refund per policy, the backstop of two racing cancels), with NUMERIC(19,2) money and no floating-point
 * column, and V1 to V10 stay byte-identical. It runs the real files against a private in-memory database.
 */
@Tag("AC-08")
@Tag("NFR-02")
@Tag("NFR-05")
class RefundRecordMigrationTest {

    /** Flyway's checksum of every migration committed before V11 (V10 is Sprint 4 TASK-004's). Never edit these files. */
    private static final Map<Integer, Integer> COMMITTED_CHECKSUMS = new LinkedHashMap<>();

    static {
        COMMITTED_CHECKSUMS.put(1, -313764939);
        COMMITTED_CHECKSUMS.put(2, -2003659274);
        COMMITTED_CHECKSUMS.put(3, -977583584);
        COMMITTED_CHECKSUMS.put(4, 1317615716);
        COMMITTED_CHECKSUMS.put(5, 2013707314);
        COMMITTED_CHECKSUMS.put(6, 8677239);
        COMMITTED_CHECKSUMS.put(7, -1021777737);
        COMMITTED_CHECKSUMS.put(8, 457276542);
        COMMITTED_CHECKSUMS.put(9, 1409785562);
        COMMITTED_CHECKSUMS.put(10, -1569138843);
    }

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private Flyway flyway;

    @BeforeEach
    void migrate() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:v11-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    private void insertPolicy(String number) {
        jdbc.update("insert into policy (policy_number, application_id, product_code, customer_id, status,"
                + " current_version_no, version, issued_at) values (?, ?, 'TERM_LIFE', 'customer1', 'ACTIVE', 1, 0,"
                + " current_timestamp)", number, "A-" + number);
    }

    private void insertRefund(String number, String premium, String fee, String amount) {
        jdbc.update("insert into refund_record (policy_number, term_no, cancellation_date, premium, term_days,"
                + " unexpired_days, cooling_off, fee, amount, formula, actor_id, created_at) values (?, 1,"
                + " date '2027-02-09', ?, 365, 265, false, ?, ?, 'formula text', 'customer1', current_timestamp)",
                number, new BigDecimal(premium), new BigDecimal(fee),
                new BigDecimal(amount));
    }

    @Test
    @DisplayName("NFR-05: V1 to V10 keep the checksums they were committed with, and V11 is the next migration after them")
    void earlierMigrationsAreUnchanged() {
        List<MigrationInfo> applied = List.of(flyway.info().applied());

        assertThat(applied).as("V1 to V11, in order").extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        for (MigrationInfo info : applied) {
            int version = Integer.parseInt(info.getVersion().getVersion());
            if (version <= 10) {
                assertThat(info.getChecksum()).as("checksum of V%d", version)
                        .isEqualTo(COMMITTED_CHECKSUMS.get(version));
            }
        }
        assertThat(applied.get(10).getDescription()).as("V11 adds the refund record").contains("refund record");
    }

    @Test
    @DisplayName("AC-08.4: V11 creates refund_record with NUMERIC(19,2) for premium, fee and amount and no floating-point column")
    void moneyIsFixedPoint() {
        List<Map<String, Object>> columns = jdbc.queryForList("select column_name, data_type, numeric_precision,"
                + " numeric_scale from information_schema.columns where table_name = 'REFUND_RECORD'");

        assertThat(columns).as("the table exists with its columns").isNotEmpty();
        for (String money : List.of("PREMIUM", "FEE", "AMOUNT")) {
            Map<String, Object> column = columns.stream().filter(c -> money.equals(c.get("COLUMN_NAME"))).findFirst()
                    .orElseThrow(() -> new AssertionError("no column " + money));
            assertThat(column.get("DATA_TYPE")).as(money).isEqualTo("NUMERIC");
            assertThat(((Number) column.get("NUMERIC_PRECISION")).intValue()).as(money + " precision").isEqualTo(19);
            assertThat(((Number) column.get("NUMERIC_SCALE")).intValue()).as(money + " scale").isEqualTo(2);
        }
        assertThat(columns.stream().map(c -> (String) c.get("DATA_TYPE")).toList())
                .as("no floating-point column").doesNotContain("DOUBLE PRECISION", "REAL", "FLOAT");
        assertThat(columns.stream().map(c -> (String) c.get("COLUMN_NAME")).toList()).containsExactlyInAnyOrder("ID",
                "POLICY_NUMBER", "TERM_NO", "CANCELLATION_DATE", "PREMIUM", "TERM_DAYS", "UNEXPIRED_DAYS",
                "COOLING_OFF", "FEE", "AMOUNT", "FORMULA", "ACTOR_ID", "CREATED_AT");
    }

    @Test
    @DisplayName("AC-08.4: refund_record is unique on the policy number, so a second refund row for one policy is refused by the database and another policy's refund is accepted")
    void oneRefundPerPolicy() {
        insertPolicy("PF-TL-2026-000001");
        insertPolicy("PF-TL-2026-000002");
        insertRefund("PF-TL-2026-000001", "6850.00", "500.00", "4473.29");

        assertThatThrownBy(() -> insertRefund("PF-TL-2026-000001", "6850.00", "500.00", "10.00"))
                .isInstanceOf(DuplicateKeyException.class);
        insertRefund("PF-TL-2026-000002", "6850.00", "500.00", "4473.29");

        assertThat(jdbc.queryForObject("select count(*) from refund_record", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-08.4: a refund row must belong to an existing policy, and a negative amount or an amount above the premium is refused by the database")
    void theDatabaseGuardsTheFigures() {
        insertPolicy("PF-TL-2026-000001");

        assertThatThrownBy(() -> insertRefund("PF-TL-2026-999999", "6850.00", "500.00", "4473.29"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefund("PF-TL-2026-000001", "6850.00", "500.00", "-0.01"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefund("PF-TL-2026-000001", "6850.00", "500.00", "6850.01"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from refund_record", Integer.class)).isZero();

        insertRefund("PF-TL-2026-000001", "6850.00", "0.00", "0.00");
        assertThat(jdbc.queryForObject("select count(*) from refund_record", Integer.class)).isEqualTo(1);
    }
}
