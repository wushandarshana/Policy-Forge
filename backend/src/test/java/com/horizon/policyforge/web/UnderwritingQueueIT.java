package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationApi.indexOf;
import static com.horizon.policyforge.support.ApplicationApi.itemFor;
import static com.horizon.policyforge.support.ApplicationApi.texts;
import static com.horizon.policyforge.support.ApplicationFixtures.householdInputs;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The underwriter queue against the real application (AC-09.1, AC-09.7; contract C4, U-7, U-11, U-14). The startup seed is
 * present, so every application is found by its ID and no test assumes a queue length or position.
 */
@Tag("AC-09")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class UnderwritingQueueIT {

    private static final LocalDate TODAY = FixedClockConfig.TODAY;
    private static final List<String> ITEM_FIELDS = List.of("applicationId", "quoteId", "productCode", "status",
            "submittedAt", "ruleVersion", "decision", "reasonCodes", "reasons", "kyc", "riskInputs");
    private static final String SEED_AADHAAR = "XXXX-XXXX-0002";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ApplicationApi api;

    @BeforeEach
    void setUp() {
        clock.setDate(TODAY);
        api = new ApplicationApi(mvc, json);
    }

    @AfterEach
    void resetClock() {
        clock.setDate(TODAY);
    }

    private JsonNode queueAs(String user, String query) throws Exception {
        MvcResult result = api.queue(user, query);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return api.bodyOf(result);
    }

    private String seededApplicationId() {
        return jdbc.queryForObject(
                "select application_id from application where customer_id = 'customer2' and kyc_aadhaar = ?",
                String.class, SEED_AADHAAR);
    }

    @Test
    @DisplayName("AC-09.1: two REFERRED applications submitted at different instants come back oldest first, whichever was stored first")
    void queueIsOldestFirstBySubmissionTime() throws Exception {
        // The later submission is stored first, so only ordering by submittedAt (not by insertion order) passes.
        clock.setDate(TODAY.plusDays(1));
        String later = api.referredApplication("customer1", LocalDate.now(clock));
        clock.setDate(TODAY);
        String earlier = api.referredApplication("customer1", LocalDate.now(clock));

        JsonNode queue = queueAs("underwriter1", "");

        assertThat(indexOf(queue, earlier)).as("the earlier application is listed").isNotNegative();
        assertThat(indexOf(queue, later)).as("the later application is listed").isNotNegative();
        assertThat(indexOf(queue, earlier)).isLessThan(indexOf(queue, later));
        List<Instant> submitted = new ArrayList<>();
        queue.forEach(item -> submitted.add(Instant.parse(item.get("submittedAt").asText())));
        assertThat(submitted).as("the whole queue is oldest first").isSorted();
    }

    @Test
    @DisplayName("AC-09.1: the queue shows Aadhaar **********1234 and PAN ******234F, and the body holds neither full value, no date of birth and no address")
    void aadhaarAndPanAreMaskedToTheirLastFourCharacters() throws Exception {
        String applicationId = api.referredApplication("customer1", TODAY);

        MvcResult result = api.queue("underwriter1", "");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        JsonNode item = itemFor(api.bodyOf(result), applicationId);
        assertThat(item).isNotNull();
        assertThat(item.get("kyc").get("aadhaar").asText()).isEqualTo("**********1234");
        assertThat(item.get("kyc").get("pan").asText()).isEqualTo("******234F");
        assertThat(item.get("kyc").get("fullName").asText()).isEqualTo(ApplicationFixtures.FULL_NAME);
        assertThat(body).doesNotContain(ApplicationFixtures.AADHAAR).doesNotContain(ApplicationFixtures.PAN)
                .doesNotContain(SEED_AADHAAR).doesNotContain(ApplicationFixtures.LINE1)
                .doesNotContain(TODAY.minusYears(40).toString());
    }

    @Test
    @DisplayName("AC-09.1: a queue item is built from the allow-list: the C4 fields, kyc with only fullName, aadhaar and pan, and the product's declared risk inputs with health inputs")
    void queueItemCarriesOnlyTheAllowListedFields() throws Exception {
        String applicationId = api.referredApplication("customer1", TODAY);

        JsonNode item = itemFor(queueAs("underwriter1", ""), applicationId);

        assertThat(item).isNotNull();
        assertThat(fieldNames(item)).containsExactlyElementsOf(ITEM_FIELDS);
        assertThat(fieldNames(item.get("kyc"))).containsExactly("fullName", "aadhaar", "pan");
        assertThat(item.get("productCode").asText()).isEqualTo("TERM_LIFE");
        assertThat(item.get("status").asText()).isEqualTo("REFERRED");
        assertThat(item.get("decision").asText()).isEqualTo("MANUAL_REVIEW");
        assertThat(item.get("ruleVersion").asInt()).isEqualTo(1);
        assertThat(texts(item.get("reasonCodes"))).containsExactly("UW-TL-002");
        assertThat(item.get("reasons").get(0).get("code").asText()).isEqualTo("UW-TL-002");
        assertThat(item.get("reasons").get(0).get("description").asText()).isNotBlank();
        JsonNode inputs = item.get("riskInputs");
        assertThat(fieldNames(inputs)).containsExactly("age", "smoker", "sumInsured", "termYears", "bmi",
                "healthDeclarations");
        assertThat(inputs.get("sumInsured").isTextual()).as("money stays a decimal string").isTrue();
        assertThat(inputs.get("sumInsured").asText()).isEqualTo("12000000.00");
        assertThat(inputs.get("smoker").asBoolean()).isTrue();
        assertThat(inputs.get("healthDeclarations").isArray()).isTrue();
    }

    @Test
    @DisplayName("AC-09.1: a HOUSEHOLD item carries exactly the HOUSEHOLD declared risk inputs and nothing else")
    void householdItemCarriesOnlyItsDeclaredInputs() throws Exception {
        String applicationId = api.applyAs("customer1", "HOUSEHOLD", householdInputs("TIMBER"), 50, TODAY);

        JsonNode item = itemFor(queueAs("underwriter1", ""), applicationId);

        assertThat(item).isNotNull();
        assertThat(texts(item.get("reasonCodes"))).containsExactly("UW-HH-003");
        assertThat(fieldNames(item.get("riskInputs"))).containsExactly("buildingSumInsured", "contentsSumInsured",
                "propertyAgeYears", "constructionType", "floodZone", "hasSecuritySystem", "hasFloodDefences");
    }

    @Test
    @DisplayName("AC-09.1: the plain queue lists REFERRED applications only, never an APPROVED or a DECLINED one")
    void plainQueueListsOnlyReferredApplications() throws Exception {
        String referred = api.referredApplication("customer1", TODAY);
        String approved = api.approvedApplication("customer1", TODAY);
        String declined = api.declinedApplication("customer1", TODAY);

        JsonNode queue = queueAs("underwriter1", "");

        assertThat(indexOf(queue, referred)).isNotNegative();
        assertThat(indexOf(queue, approved)).isNegative();
        assertThat(indexOf(queue, declined)).isNegative();
        queue.forEach(item -> assertThat(item.get("status").asText()).isEqualTo("REFERRED"));
    }

    @Test
    @DisplayName("AC-09.7: underwriter1 requesting includeDeclined=true gets 403, and the problem body does not echo the request")
    void underwriterCannotIncludeDeclined() throws Exception {
        MvcResult result = api.queue("underwriter1", "includeDeclined=true");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("includeDeclined");
    }

    @Test
    @DisplayName("AC-09.7: the seeded customer2 application is listed as REFERRED with reason codes [UW-TL-002] in underwriter1's plain queue")
    void seededApplicationIsInThePlainQueue() throws Exception {
        String seeded = seededApplicationId();

        JsonNode item = itemFor(queueAs("underwriter1", ""), seeded);

        assertThat(item).as("the seed is listed by its ID").isNotNull();
        assertThat(item.get("status").asText()).isEqualTo("REFERRED");
        assertThat(texts(item.get("reasonCodes"))).containsExactly("UW-TL-002");
        assertThat(item.get("kyc").get("aadhaar").asText()).isEqualTo("**********0002");
    }

    @Test
    @DisplayName("AC-09.7: admin1 with includeDeclined=true sees the REFERRED and the DECLINED applications, oldest first, and still no APPROVED one")
    void adminSeesDeclinedApplications() throws Exception {
        String referred = api.referredApplication("customer1", TODAY);
        String declined = api.declinedApplication("customer1", TODAY);
        String approved = api.approvedApplication("customer1", TODAY);

        JsonNode queue = queueAs("admin1", "includeDeclined=true");

        assertThat(indexOf(queue, referred)).isNotNegative();
        assertThat(indexOf(queue, declined)).isNotNegative();
        assertThat(indexOf(queue, approved)).isNegative();
        JsonNode declinedItem = itemFor(queue, declined);
        assertThat(declinedItem.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declinedItem.get("decision").asText()).isEqualTo("DECLINE");
        assertThat(texts(declinedItem.get("reasonCodes"))).containsExactly("UW-TL-001", "UW-TL-004");
        List<Instant> submitted = new ArrayList<>();
        queue.forEach(item -> submitted.add(Instant.parse(item.get("submittedAt").asText())));
        assertThat(submitted).isSorted();
    }

    @Test
    @DisplayName("AC-09.7: admin1 with includeDeclined=false, or no parameter, gets the plain queue without DECLINED applications")
    void adminWithoutIncludeDeclinedGetsThePlainQueue() throws Exception {
        String declined = api.declinedApplication("customer1", TODAY);

        assertThat(indexOf(queueAs("admin1", "includeDeclined=false"), declined)).isNegative();
        assertThat(indexOf(queueAs("admin1", ""), declined)).isNegative();
    }

    @Test
    @DisplayName("AC-09.7: an includeDeclined value that is neither true nor false is a 400 naming includeDeclined, never silently ignored (U-11)")
    void unrecognisedIncludeDeclinedIsRejected() throws Exception {
        MvcResult result = api.queue("underwriter1", "includeDeclined=maybe");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode problem = api.bodyOf(result);
        assertThat(errorFields(problem)).containsExactly("includeDeclined");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("maybe");
    }
}
