package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.DashboardApi;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.PolicyApi;
import com.horizon.policyforge.support.PolicyRows;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The synthetic sample portfolio (app_spec section 9, plan C10, AC-11.1). This class has its own application context on
 * the fixed clock ({@link FixedClockConfig#TODAY}, call it T) and makes no policy of its own, so the portfolio seed is
 * the only thing in it. S1 to S6 are told apart by the seed's synthetic Aadhaar markers (XXXX-XXXX-0101 to 0106), and
 * every expected figure is the independent oracle's: TERM_LIFE 6850.00, MOTOR 15150.00, HOUSEHOLD 11350.00.
 */
@Tag("AC-11")
@SpringBootTest(properties = "policyforge.test.context=portfolio-seed")
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class PortfolioSeedIT {

    private static final LocalDate T = FixedClockConfig.TODAY;

    /** One row of the C10 table: the marker, the owner, the product, the premium and the first term's dates. */
    private record Expected(String name, String aadhaar, String customer, String product, String premium,
            LocalDate start, LocalDate end) {
    }

    private static final List<Expected> SEEDED = List.of(
            new Expected("S1", "XXXX-XXXX-0101", "customer1", "TERM_LIFE", "6850.00", T.plusDays(21).minusYears(1),
                    T.plusDays(20)),
            new Expected("S2", "XXXX-XXXX-0102", "customer1", "MOTOR", "15150.00", T.minusDays(30),
                    T.minusDays(30).plusYears(1).minusDays(1)),
            new Expected("S3", "XXXX-XXXX-0103", "customer1", "HOUSEHOLD", "11350.00", T.minusDays(30),
                    T.minusDays(30).plusYears(1).minusDays(1)),
            new Expected("S4", "XXXX-XXXX-0104", "customer2", "TERM_LIFE", "6850.00", T.minusDays(30),
                    T.minusDays(30).plusYears(1).minusDays(1)),
            new Expected("S5", "XXXX-XXXX-0105", "customer2", "MOTOR", "15150.00", T.minusDays(4).minusYears(1),
                    T.minusDays(5)),
            new Expected("S6", "XXXX-XXXX-0106", "customer2", "HOUSEHOLD", "11350.00", T.minusDays(30),
                    T.minusDays(30).plusYears(1).minusDays(1)));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext context;

    private String numberOf(Expected seeded) {
        List<String> numbers = jdbc.queryForList("select p.policy_number from policy p join application a"
                + " on a.application_id = p.application_id where a.kyc_aadhaar = ? and a.customer_id = ?",
                String.class, seeded.aadhaar(), seeded.customer());
        assertThat(numbers).as(seeded.name() + " has exactly one policy").hasSize(1);
        return numbers.get(0);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private int inForceSeedPolicies(String product) {
        int total = 0;
        for (Expected seeded : SEEDED) {
            if (seeded.product().equals(product)) {
                String status = (String) PolicyRows.policy(jdbc, numberOf(seeded)).get("STATUS");
                if (List.of("ACTIVE", "ENDORSED", "RENEWED").contains(status)) {
                    total++;
                }
            }
        }
        return total;
    }

    @Test
    @DisplayName("AC-11.1: the seed holds S1 to S6 with the C10 owners, products, premiums and term dates: S1 ends T+20, S2 S3 S4 and S6 start T-30, S5 ended T-5, each in force with a paid first term")
    void theSeedHoldsSixPoliciesWithTheC10Dates() {
        assertThat(count("policy")).as("the seed is the only thing in this context").isEqualTo(6);
        for (Expected seeded : SEEDED) {
            String number = numberOf(seeded);
            Map<String, Object> policy = PolicyRows.policy(jdbc, number);
            assertThat(policy.get("CUSTOMER_ID")).as(seeded.name()).isEqualTo(seeded.customer());
            assertThat(policy.get("PRODUCT_CODE")).as(seeded.name()).isEqualTo(seeded.product());
            assertThat(policy.get("STATUS")).as(seeded.name()).isEqualTo("ACTIVE");
            Map<String, Object> first = PolicyRows.terms(jdbc, number).get(0);
            assertThat(first.get("START_DATE").toString()).as(seeded.name()).isEqualTo(seeded.start().toString());
            assertThat(first.get("END_DATE").toString()).as(seeded.name()).isEqualTo(seeded.end().toString());
            assertThat((BigDecimal) first.get("PREMIUM")).as(seeded.name()).isEqualByComparingTo(seeded.premium());
            assertThat(first.get("PAID")).as(seeded.name()).isEqualTo(true);
            Map<String, Object> version = PolicyRows.versions(jdbc, number).get(0);
            assertThat(version.get("EFFECTIVE_DATE").toString()).as(seeded.name()).isEqualTo(seeded.start().toString());
            assertThat(version.get("EXPIRY_DATE").toString()).as(seeded.name()).isEqualTo(seeded.end().toString());
        }
        assertThat(SEEDED.get(0).end()).isEqualTo(T.plusDays(20));
        assertThat(SEEDED.get(4).end()).isEqualTo(T.minusDays(5));
    }

    @Test
    @DisplayName("AC-11.1: after the seed's one renewal cycle for T, S1 and S5 each hold an unpaid second term from the day after their end at the current premium, and no other seed policy has a second term")
    void theSeedCycleOfferedS1AndS5() {
        for (Expected seeded : SEEDED) {
            List<Map<String, Object>> terms = PolicyRows.terms(jdbc, numberOf(seeded));
            if (seeded.name().equals("S1") || seeded.name().equals("S5")) {
                assertThat(terms).as(seeded.name()).hasSize(2);
                Map<String, Object> offer = terms.get(1);
                assertThat(offer.get("PAID")).isEqualTo(false);
                assertThat(offer.get("START_DATE").toString()).isEqualTo(seeded.end().plusDays(1).toString());
                assertThat((BigDecimal) offer.get("PREMIUM")).isEqualByComparingTo(seeded.premium());
            } else {
                assertThat(terms).as(seeded.name()).hasSize(1);
            }
        }
        List<Map<String, Object>> runs = jdbc.queryForList("select actor_id, entity_type, entity_id from audit_entry"
                + " where action = 'RENEWAL_CYCLE_RUN'");
        assertThat(runs).as("exactly one cycle, for T, by the scheduler's actor").hasSize(1);
        assertThat(runs.get(0)).containsEntry("ACTOR_ID", "system:renewal-scheduler")
                .containsEntry("ENTITY_TYPE", "RENEWAL_CYCLE").containsEntry("ENTITY_ID", T.toString());
        for (Expected seeded : SEEDED) {
            assertThat(PolicyRows.transitions(jdbc, numberOf(seeded))).as(seeded.name() + " is only issued")
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("AC-11.1: the admin dashboard on T counts the in-force seed policies per product (2, 2, 2 from the seed's own records), collects 13700.00, 30300.00 and 22700.00 (total 66700.00), lists S1 at 6850.00 unpaid in the pipeline and S5 as the one policy in the lapse forecast")
    void theDashboardShowsTheSeedPortfolio() throws Exception {
        DashboardApi dashboards = new DashboardApi(mvc, json);
        String s1 = numberOf(SEEDED.get(0));
        String s5 = numberOf(SEEDED.get(4));

        JsonNode dashboard = dashboards.ok(null);

        assertThat(dashboard.get("asOf").asText()).isEqualTo(T.toString());
        for (String product : DashboardApi.PRODUCTS) {
            assertThat(dashboard.get("activePoliciesByProduct").get(product).asInt()).as(product)
                    .isEqualTo(inForceSeedPolicies(product)).isEqualTo(2);
        }
        assertThat(dashboard.get("premiumCollected").get("TERM_LIFE").asText()).isEqualTo("13700.00");
        assertThat(dashboard.get("premiumCollected").get("MOTOR").asText()).isEqualTo("30300.00");
        assertThat(dashboard.get("premiumCollected").get("HOUSEHOLD").asText()).isEqualTo("22700.00");
        assertThat(dashboard.get("premiumCollected").get("total").asText()).isEqualTo("66700.00");
        assertThat(dashboard.get("renewalPipeline")).hasSize(1);
        JsonNode pipeline = DashboardApi.pipelineItem(dashboard, s1);
        assertThat(pipeline).isNotNull();
        assertThat(pipeline.get("termEnd").asText()).isEqualTo(T.plusDays(20).toString());
        assertThat(pipeline.get("renewalPremium").asText()).isEqualTo("6850.00");
        assertThat(pipeline.get("paid").asBoolean()).isFalse();
        assertThat(dashboard.get("lapseForecast").get("count").asInt()).isEqualTo(1);
        assertThat(DashboardApi.forecastNumbers(dashboard)).containsExactly(s5);
    }

    @Test
    @DisplayName("AC-11.1: every seed policy came through the real services: an owned quote, a submitted application that the rules approved and issuance moved to ISSUED, the three audit entries, and a null to ACTIVE transition by the customer")
    void theSeedWentThroughTheRealServices() {
        for (Expected seeded : SEEDED) {
            String number = numberOf(seeded);
            Map<String, Object> application = jdbc.queryForMap("select application_id, quote_id, status from application"
                    + " where kyc_aadhaar = ? and customer_id = ?", seeded.aadhaar(), seeded.customer());
            String applicationId = (String) application.get("APPLICATION_ID");
            assertThat(application.get("STATUS")).as(seeded.name()).isEqualTo("ISSUED");
            assertThat(jdbc.queryForObject("select owner_id from quote where quote_id = ?", String.class,
                    application.get("QUOTE_ID"))).as(seeded.name()).isEqualTo(seeded.customer());
            Map<String, Object> decision = jdbc.queryForMap("select source, outcome, actor_id from underwriting_decision"
                    + " where application_id = ?", applicationId);
            assertThat(decision).as(seeded.name()).containsEntry("SOURCE", "RULES").containsEntry("OUTCOME", "AUTO_BIND")
                    .containsEntry("ACTOR_ID", "system");
            List<String> actions = jdbc.queryForList("select action from audit_entry where entity_type = 'APPLICATION'"
                    + " and entity_id = ? order by id", String.class, applicationId);
            assertThat(actions).as(seeded.name()).containsExactly("APPLICATION_SUBMITTED",
                    "UNDERWRITING_AUTO_DECISION", "APPLICATION_ISSUED");
            Map<String, Object> transition = PolicyRows.transitions(jdbc, number).get(0);
            assertThat(transition.get("FROM_STATUS")).isNull();
            assertThat(transition).containsEntry("TO_STATUS", "ACTIVE").containsEntry("REASON", "ISSUANCE")
                    .containsEntry("ACTOR_ID", seeded.customer());
        }
    }

    @Test
    @DisplayName("AC-11.1: the owners see their seed policies, and the renewal offers of S1 and S5 show payable until their term end plus the grace days (30 for TERM_LIFE, 15 for MOTOR)")
    void theOwnersSeeTheSeedAndTheOffers() throws Exception {
        PolicyApi policies = new PolicyApi(mvc, json);
        List<String> customer1 = new ArrayList<>();
        policies.bodyOf(policies.mine("customer1")).forEach(item -> customer1.add(item.get("policyNumber").asText()));
        List<String> customer2 = new ArrayList<>();
        policies.bodyOf(policies.mine("customer2")).forEach(item -> customer2.add(item.get("policyNumber").asText()));

        assertThat(customer1).containsExactlyInAnyOrder(numberOf(SEEDED.get(0)), numberOf(SEEDED.get(1)),
                numberOf(SEEDED.get(2)));
        assertThat(customer2).containsExactlyInAnyOrder(numberOf(SEEDED.get(3)), numberOf(SEEDED.get(4)),
                numberOf(SEEDED.get(5)));

        JsonNode s1 = policies.bodyOf(policies.detail("customer1", numberOf(SEEDED.get(0)))).get("pendingRenewal");
        assertThat(s1.get("termNo").asInt()).isEqualTo(2);
        assertThat(s1.get("premium").asText()).isEqualTo("6850.00");
        assertThat(s1.get("payableUntil").asText()).isEqualTo(T.plusDays(20).plusDays(30).toString());
        JsonNode s5 = policies.bodyOf(policies.detail("customer2", numberOf(SEEDED.get(4)))).get("pendingRenewal");
        assertThat(s5.get("premium").asText()).isEqualTo("15150.00");
        assertThat(s5.get("payableUntil").asText()).isEqualTo(T.minusDays(5).plusDays(15).toString());
        assertThat(policies.bodyOf(policies.detail("customer1", numberOf(SEEDED.get(1)))).get("pendingRenewal")
                .isNull()).isTrue();
    }

    @Test
    @DisplayName("AC-11.1: running the seeding again adds nothing, so a restart never duplicates the portfolio: every table keeps its row count and the dashboard is unchanged, and the older application seed is still one application")
    void seedingTwiceAddsNothing() throws Exception {
        DashboardApi dashboards = new DashboardApi(mvc, json);
        List<String> tables = List.of("quote", "application", "underwriting_decision", "policy", "policy_version",
                "policy_term", "policy_status_transition", "audit_entry", "endorsement", "refund_record");
        List<Integer> before = new ArrayList<>();
        tables.forEach(table -> before.add(count(table)));
        JsonNode dashboardBefore = dashboards.ok(null);
        ApplicationRunner seeder = context.getBean("portfolioSeedLoader", ApplicationRunner.class);
        ApplicationArguments noArguments = new DefaultApplicationArguments();

        seeder.run(noArguments);
        seeder.run(noArguments);

        List<Integer> after = new ArrayList<>();
        tables.forEach(table -> after.add(count(table)));
        assertThat(after).as(tables.toString()).isEqualTo(before);
        assertThat(dashboards.ok(null)).isEqualTo(dashboardBefore);
        assertThat(count("policy")).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from application where kyc_aadhaar = 'XXXX-XXXX-0002'",
                Integer.class)).isEqualTo(1);
        ApplicationApi applications = new ApplicationApi(mvc, json);
        assertThat(applications.bodyOf(applications.mine("customer1"))).as("customer1's applications are the seed's three")
                .hasSize(3);
    }
}
