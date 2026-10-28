package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.texts;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.FixedClockConfig;
import java.math.BigDecimal;
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
 * The startup seed (U-14, P-5): one synthetic REFERRED TERM_LIFE application for customer2, created through the same
 * quote and application services as a real submission. This class has its own application context and makes no
 * application of its own, so the seed is the only application in it.
 */
@Tag("AC-09")
@SpringBootTest(properties = "policyforge.test.context=seed-isolated")
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class ApplicationSeedIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext context;

    private int applications() {
        return jdbc.queryForObject("select count(*) from application", Integer.class);
    }

    @Test
    @DisplayName("AC-09.7: after startup customer2 has a REFERRED TERM_LIFE application with reason codes [UW-TL-002], and customer1 has none")
    void startupSeedsAReferredApplicationForCustomer2() throws Exception {
        ApplicationApi api = new ApplicationApi(mvc, json);

        JsonNode seeded = api.bodyOf(api.mine("customer2"));

        assertThat(seeded).hasSize(1);
        JsonNode application = seeded.get(0);
        assertThat(application.get("productCode").asText()).isEqualTo("TERM_LIFE");
        assertThat(application.get("status").asText()).isEqualTo("REFERRED");
        assertThat(application.get("decision").asText()).isEqualTo("MANUAL_REVIEW");
        assertThat(texts(application.get("reasonCodes"))).containsExactly("UW-TL-002");
        assertThat(application.get("ruleVersion").asInt()).isEqualTo(1);
        assertThat(api.bodyOf(api.mine("customer1"))).isEmpty();
    }

    @Test
    @DisplayName("AC-09.7: the seeded application is a smoker with sum insured 12,000,000.00, with a RULES decision row by system and the two U-12 audit entries")
    void seedWentThroughTheRealSubmissionPath() throws Exception {
        ApplicationApi api = new ApplicationApi(mvc, json);
        String applicationId = api.bodyOf(api.mine("customer2")).get(0).get("applicationId").asText();

        Map<String, Object> quote = jdbc.queryForMap(
                "select q.sum_insured, q.risk_inputs_json from quote q join application a on a.quote_id = q.quote_id"
                        + " where a.application_id = ?", applicationId);
        assertThat((BigDecimal) quote.get("SUM_INSURED")).isEqualByComparingTo("12000000.00");
        JsonNode inputs = json.readTree(quote.get("RISK_INPUTS_JSON").toString());
        assertThat(inputs.get("smoker").asBoolean()).isTrue();

        List<Map<String, Object>> decisions = jdbc.queryForList(
                "select source, outcome, reason_codes, rule_version, actor_id from underwriting_decision"
                        + " where application_id = ?", applicationId);
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).get("SOURCE")).isEqualTo("RULES");
        assertThat(decisions.get(0).get("OUTCOME")).isEqualTo("MANUAL_REVIEW");
        assertThat(decisions.get(0).get("REASON_CODES")).isEqualTo("UW-TL-002");
        assertThat(decisions.get(0).get("ACTOR_ID")).isEqualTo("system");

        List<Map<String, Object>> audit = jdbc.queryForList(
                "select actor_id, action, previous_status, new_status from audit_entry"
                        + " where entity_type = 'APPLICATION' and entity_id = ? order by id", applicationId);
        assertThat(audit).extracting(row -> row.get("ACTION"))
                .containsExactly("APPLICATION_SUBMITTED", "UNDERWRITING_AUTO_DECISION");
        assertThat(audit).extracting(row -> row.get("ACTOR_ID")).containsExactly("customer2", "system");
        assertThat(audit).extracting(row -> row.get("NEW_STATUS")).containsExactly("UNDERWRITING", "REFERRED");
    }

    @Test
    @DisplayName("AC-09.7: running the seeding again adds no application, so a restart never duplicates the demo case (U-14)")
    void seedingIsIdempotent() throws Exception {
        int before = applications();
        ApplicationRunner seeder = context.getBean("applicationSeedLoader", ApplicationRunner.class);
        ApplicationArguments noArguments = new DefaultApplicationArguments();

        seeder.run(noArguments);
        seeder.run(noArguments);

        assertThat(applications()).isEqualTo(before).isEqualTo(1);
    }
}
