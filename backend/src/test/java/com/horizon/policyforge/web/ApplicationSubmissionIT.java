package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationFixtures.addressOf;
import static com.horizon.policyforge.support.ApplicationFixtures.application;
import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.householdInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static com.horizon.policyforge.support.ApplicationFixtures.motorInputs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The application submission API against the real application (AC-03.1 to AC-03.8, NFR-04.1 and NFR-04.3). The clock is
 * the shared movable test clock. The startup seed is present (U-14), so rows are found by ID and "nothing is stored"
 * is proved with row counts taken before and after.
 */
@Tag("AC-03")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class ApplicationSubmissionIT {

    private static final LocalDate TODAY = FixedClockConfig.TODAY;
    private static final String APPLICATION_ID_PATTERN =
            "^A-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
    private static final String UNKNOWN_QUOTE_ID = "Q-00000000-0000-4000-8000-000000000000";
    private static final List<String> OWNER_RESPONSE_FIELDS = List.of("applicationId", "quoteId", "productCode",
            "status", "decision", "reasonCodes", "reasons", "ruleVersion", "submittedAt");

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

    /** Rows an application leaves behind: applications, decisions and audit entries. */
    private record Counts(int applications, int decisions, int audit) {
    }

    private Counts counts() {
        return new Counts(
                jdbc.queryForObject("select count(*) from application", Integer.class),
                jdbc.queryForObject("select count(*) from underwriting_decision", Integer.class),
                jdbc.queryForObject("select count(*) from audit_entry", Integer.class));
    }

    private int applicationsForQuote(String quoteId) {
        return jdbc.queryForObject("select count(*) from application where quote_id = ?", Integer.class, quoteId);
    }

    private LocalDate dobForAge(int age) {
        return LocalDate.now(clock).minusYears(age);
    }

    private JsonNode problemOf(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        return api.bodyOf(result);
    }

    @Test
    @DisplayName("AC-03.1: customer1 applies with a valid TERM_LIFE quote and valid KYC, and the stored application is linked to that quote and customer1")
    void validApplicationIsStoredAndLinkedToQuoteAndCustomer() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());

        MvcResult result = api.apply("customer1", application(quoteId, kyc(dobForAge(35))));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode created = api.bodyOf(result);
        String applicationId = created.get("applicationId").asText();
        assertThat(applicationId).matches(APPLICATION_ID_PATTERN);
        assertThat(created.get("quoteId").asText()).isEqualTo(quoteId);
        assertThat(created.get("productCode").asText()).isEqualTo("TERM_LIFE");
        assertThat(created.get("submittedAt").asText()).isEqualTo("2026-10-28T00:00:00Z");

        Map<String, Object> stored = jdbc.queryForMap(
                "select quote_id, customer_id, product_code, status from application where application_id = ?",
                applicationId);
        assertThat(stored.get("QUOTE_ID")).isEqualTo(quoteId);
        assertThat(stored.get("CUSTOMER_ID")).isEqualTo("customer1");
        assertThat(stored.get("PRODUCT_CODE")).isEqualTo("TERM_LIFE");
        assertThat(stored.get("STATUS")).isEqualTo(created.get("status").asText());
    }

    @Test
    @DisplayName("AC-03.1: the APPLICATION_SUBMITTED entry (SUBMITTED to UNDERWRITING, actor customer1) is recorded before UNDERWRITING_AUTO_DECISION (UNDERWRITING to APPROVED, actor system)")
    void applicationMovesToUnderwritingBeforeItIsEvaluated() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());

        String applicationId = api.applyOk("customer1", quoteId, kyc(dobForAge(35))).get("applicationId").asText();

        List<Map<String, Object>> audit = jdbc.queryForList(
                "select id, actor_id, action, previous_status, new_status from audit_entry"
                        + " where entity_type = 'APPLICATION' and entity_id = ? order by id", applicationId);
        assertThat(audit).hasSize(2);
        Map<String, Object> submitted = audit.get(0);
        assertThat(submitted.get("ACTION")).isEqualTo("APPLICATION_SUBMITTED");
        assertThat(submitted.get("ACTOR_ID")).isEqualTo("customer1");
        assertThat(submitted.get("PREVIOUS_STATUS")).isEqualTo("SUBMITTED");
        assertThat(submitted.get("NEW_STATUS")).isEqualTo("UNDERWRITING");
        Map<String, Object> decided = audit.get(1);
        assertThat(decided.get("ACTION")).isEqualTo("UNDERWRITING_AUTO_DECISION");
        assertThat(decided.get("ACTOR_ID")).isEqualTo("system");
        assertThat(decided.get("PREVIOUS_STATUS")).isEqualTo("UNDERWRITING");
        assertThat(decided.get("NEW_STATUS")).isEqualTo("APPROVED");
        assertThat(((Number) submitted.get("ID")).longValue()).isLessThan(((Number) decided.get("ID")).longValue());
    }

    @Test
    @DisplayName("AC-03.1: the 201 body is the C1 allow-list and carries no KYC field or value")
    void submissionResponseHasNoKycFields() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());

        MvcResult result = api.apply("customer1", application(quoteId, kyc(dobForAge(35))));

        assertThat(fieldNames(api.bodyOf(result))).containsExactlyInAnyOrderElementsOf(OWNER_RESPONSE_FIELDS);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(ApplicationFixtures.AADHAAR).doesNotContain(ApplicationFixtures.PAN)
                .doesNotContain(ApplicationFixtures.FULL_NAME).doesNotContain(dobForAge(35).toString())
                .doesNotContain("kyc").doesNotContain("aadhaar").doesNotContain("dateOfBirth");
    }

    @Test
    @DisplayName("AC-03.2: PAN abcde1234f, Aadhaar 1234-5678 and postcode 41100 are a 400 naming kyc.pan, kyc.aadhaar and kyc.address.postcode, no application is stored and the body repeats none of the values")
    void invalidKycFieldsAreEachNamedAndNothingIsStored() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Map<String, Object> badKyc = kyc(dobForAge(35));
        badKyc.put("pan", "abcde1234f");
        badKyc.put("aadhaar", "1234-5678");
        addressOf(badKyc).put("postcode", "41100");
        Counts before = counts();

        MvcResult result = api.apply("customer1", application(quoteId, badKyc));

        JsonNode problem = problemOf(result, 400);
        assertThat(errorFields(problem)).containsExactlyInAnyOrder("kyc.pan", "kyc.aadhaar", "kyc.address.postcode");
        problem.get("errors").forEach(error -> assertThat(error.get("message").asText()).isNotBlank());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("abcde1234f").doesNotContain("1234-5678")
                .doesNotContain("41100").doesNotContain(ApplicationFixtures.FULL_NAME)
                .doesNotContain(dobForAge(35).toString());
        assertThat(counts()).isEqualTo(before);
        assertThat(applicationsForQuote(quoteId)).isZero();
    }

    @Test
    @DisplayName("AC-03.2: a blank name and address, a future date of birth and over-long text are each named and are a 400, never a 500 (U-4)")
    void blankFutureAndOverlongKycFieldsAreRejected() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Map<String, Object> badKyc = kyc(LocalDate.now(clock).plusDays(1));
        badKyc.put("fullName", " ");
        addressOf(badKyc).put("line1", "L".repeat(201));
        addressOf(badKyc).put("city", "C".repeat(101));
        Counts before = counts();

        JsonNode problem = problemOf(api.apply("customer1", application(quoteId, badKyc)), 400);

        assertThat(errorFields(problem)).containsExactlyInAnyOrder("kyc.fullName", "kyc.dateOfBirth",
                "kyc.address.line1", "kyc.address.city");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.3: once the clock passes the quote's validUntil, the application is a 400 QUOTE_EXPIRED and nothing is stored")
    void expiredQuoteIsRejected() throws Exception {
        JsonNode quote = api.createQuote("TERM_LIFE", cleanTermLifeInputs());
        String quoteId = quote.get("quoteId").asText();
        LocalDate validUntil = LocalDate.parse(quote.get("validUntil").asText());
        clock.setDate(validUntil.plusDays(1));
        Counts before = counts();

        JsonNode problem = problemOf(api.apply("customer1", application(quoteId, kyc(dobForAge(35)))), 400);

        assertThat(problem.get("code").asText()).isEqualTo("QUOTE_EXPIRED");
        assertThat(errorFields(problem)).containsExactly("quoteId");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.3: the validUntil day itself is still valid and the application is accepted (U-2)")
    void validUntilDayIsAccepted() throws Exception {
        JsonNode quote = api.createQuote("TERM_LIFE", cleanTermLifeInputs());
        String quoteId = quote.get("quoteId").asText();
        clock.setDate(LocalDate.parse(quote.get("validUntil").asText()));

        JsonNode created = api.applyOk("customer1", quoteId, kyc(dobForAge(35)));

        assertThat(created.get("quoteId").asText()).isEqualTo(quoteId);
    }

    @Test
    @DisplayName("AC-03.3: an expired quote that already has an application is reported as QUOTE_EXPIRED, because expiry is checked before the duplicate check (U-2 order)")
    void expiryIsCheckedBeforeDuplicates() throws Exception {
        JsonNode quote = api.createQuote("TERM_LIFE", cleanTermLifeInputs());
        String quoteId = quote.get("quoteId").asText();
        api.applyOk("customer1", quoteId, kyc(dobForAge(35)));
        clock.setDate(LocalDate.parse(quote.get("validUntil").asText()).plusDays(1));

        JsonNode problem = problemOf(api.apply("customer1", application(quoteId, kyc(dobForAge(35)))), 400);

        assertThat(problem.get("code").asText()).isEqualTo("QUOTE_EXPIRED");
    }

    @Test
    @DisplayName("AC-03.4: a date of birth that gives age 40 for a quote priced at age 35 is a 400 KYC_AGE_MISMATCH naming kyc.dateOfBirth and nothing is stored")
    void ageMismatchIsRejected() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Counts before = counts();

        MvcResult result = api.apply("customer1", application(quoteId, kyc(dobForAge(40))));

        JsonNode problem = problemOf(result, 400);
        assertThat(problem.get("code").asText()).isEqualTo("KYC_AGE_MISMATCH");
        assertThat(errorFields(problem)).containsExactly("kyc.dateOfBirth");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(dobForAge(40).toString());
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.4: a MOTOR quote is checked against driverAge, and a HOUSEHOLD quote has no age check (U-4)")
    void motorChecksDriverAgeAndHouseholdChecksNothing() throws Exception {
        String motorQuote = api.quoteId("MOTOR", motorInputs(30, 0));
        JsonNode mismatch = problemOf(api.apply("customer1", application(motorQuote, kyc(dobForAge(31)))), 400);
        assertThat(mismatch.get("code").asText()).isEqualTo("KYC_AGE_MISMATCH");
        assertThat(applicationsForQuote(motorQuote)).isZero();
        assertThat(api.applyOk("customer1", motorQuote, kyc(dobForAge(30))).get("productCode").asText())
                .isEqualTo("MOTOR");

        String householdQuote = api.quoteId("HOUSEHOLD", householdInputs("BRICK"));
        assertThat(api.applyOk("customer1", householdQuote, kyc(dobForAge(77))).get("productCode").asText())
                .isEqualTo("HOUSEHOLD");
    }

    @Test
    @DisplayName("AC-03.4: an age mismatch and a KYC format error are not reported together, because the age check runs after the format checks (U-2)")
    void formatErrorsAreReportedBeforeTheAgeCheck() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Map<String, Object> badKyc = kyc(dobForAge(40));
        badKyc.put("pan", "abcde1234f");

        JsonNode problem = problemOf(api.apply("customer1", application(quoteId, badKyc)), 400);

        assertThat(errorFields(problem)).containsExactly("kyc.pan");
        assertThat(problem.has("code")).isFalse();
    }

    @Test
    @DisplayName("AC-03.5: unknown keys at the top level, inside kyc and inside kyc.address are a 400 naming each by its dotted path and nothing is stored")
    void unknownKeysAreEachNamedByDottedPath() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Map<String, Object> kyc = kyc(dobForAge(35));
        kyc.put("nationality", "synthetic");
        addressOf(kyc).put("landmark", "synthetic");
        Map<String, Object> body = application(quoteId, kyc);
        body.put("bonus", true);
        Counts before = counts();

        MvcResult result = api.apply("customer1", body);

        JsonNode problem = problemOf(result, 400);
        assertThat(errorFields(problem)).containsExactlyInAnyOrder("bonus", "kyc.nationality",
                "kyc.address.landmark");
        assertThat(counts()).isEqualTo(before);
        assertThat(applicationsForQuote(quoteId)).isZero();
    }

    @Test
    @DisplayName("AC-03.5: each unknown key on its own, at any of the three levels, is a 400 naming just that key")
    void eachUnknownKeyOnItsOwnIsRejected() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());

        Map<String, Object> top = application(quoteId, kyc(dobForAge(35)));
        top.put("bonus", true);
        assertThat(errorFields(problemOf(api.apply("customer1", top), 400))).containsExactly("bonus");

        Map<String, Object> inKyc = kyc(dobForAge(35));
        inKyc.put("nationality", "synthetic");
        assertThat(errorFields(problemOf(api.apply("customer1", application(quoteId, inKyc)), 400)))
                .containsExactly("kyc.nationality");

        Map<String, Object> inAddress = kyc(dobForAge(35));
        addressOf(inAddress).put("landmark", "synthetic");
        assertThat(errorFields(problemOf(api.apply("customer1", application(quoteId, inAddress)), 400)))
                .containsExactly("kyc.address.landmark");

        assertThat(applicationsForQuote(quoteId)).isZero();
    }

    @Test
    @DisplayName("AC-03.5: an unknown key of 5000 characters, at the top level, inside kyc and inside kyc.address, is named with its fixed prefix and the key cut to its first 64 characters, and the full key is never echoed")
    void aLongUnknownKeyIsEchoedOnlyInItsBoundedForm() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        String longKey = "unknownKey" + "Q".repeat(4990);
        String bounded = longKey.substring(0, 64);
        Counts before = counts();

        Map<String, Object> top = application(quoteId, kyc(dobForAge(35)));
        top.put(longKey, true);
        Map<String, Object> inKyc = kyc(dobForAge(35));
        inKyc.put(longKey, "synthetic");
        Map<String, Object> inAddress = kyc(dobForAge(35));
        addressOf(inAddress).put(longKey, "synthetic");

        MvcResult topResult = api.apply("customer1", top);
        MvcResult kycResult = api.apply("customer1", application(quoteId, inKyc));
        MvcResult addressResult = api.apply("customer1", application(quoteId, inAddress));

        assertThat(errorFields(problemOf(topResult, 400))).containsExactly(bounded);
        assertThat(errorFields(problemOf(kycResult, 400))).containsExactly("kyc." + bounded);
        assertThat(errorFields(problemOf(addressResult, 400))).containsExactly("kyc.address." + bounded);
        for (MvcResult result : List.of(topResult, kycResult, addressResult)) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(longKey.substring(0, 65));
            problemOf(result, 400).get("errors").forEach(error -> assertThat(error.get("message").asText())
                    .isEqualTo("is not an accepted field"));
        }
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.5: unknown keys holding <, >, quotes and spaces, at each of the three levels, are echoed with those characters replaced by an underscore, and keys that sanitise to the same text are named once")
    void unknownKeysAreEchoedSanitised() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Counts before = counts();

        Map<String, Object> body = application(quoteId, kyc(dobForAge(35)));
        body.put("<a b=\"c\" d='e'>", true);
        Map<String, Object> inKyc = kyc(dobForAge(35));
        inKyc.put("<a b=\"c\" d='e'>", "synthetic");
        inKyc.put("x y", "synthetic");
        inKyc.put("x<y", "synthetic");
        addressOf(inKyc).put("<a b=\"c\" d='e'>", "synthetic");
        body.put("kyc", inKyc);

        MvcResult result = api.apply("customer1", body);

        JsonNode problem = problemOf(result, 400);
        assertThat(errorFields(problem)).containsExactlyInAnyOrder("_a_b__c__d__e__", "kyc._a_b__c__d__e__",
                "kyc.x_y", "kyc.address._a_b__c__d__e__");
        problem.get("errors").forEach(error -> assertThat(error.get("message").asText())
                .isEqualTo("is not an accepted field"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("<").doesNotContain(">")
                .doesNotContain("'").doesNotContain("x y");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.5: an unknown key and an invalid PAN are reported together (U-2 step 1)")
    void unknownKeyAndFormatErrorAreReportedTogether() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Map<String, Object> badKyc = kyc(dobForAge(35));
        badKyc.put("pan", "abcde1234f");
        Map<String, Object> body = application(quoteId, badKyc);
        body.put("bonus", true);

        JsonNode problem = problemOf(api.apply("customer1", body), 400);

        assertThat(errorFields(problem)).containsExactlyInAnyOrder("bonus", "kyc.pan");
    }

    @Test
    @DisplayName("AC-03.6: an unknown quoteId is a 400 QUOTE_NOT_FOUND naming quoteId and nothing is stored")
    void unknownQuoteIsRejected() throws Exception {
        Counts before = counts();

        MvcResult result = api.apply("customer1", application(UNKNOWN_QUOTE_ID, kyc(dobForAge(35))));

        JsonNode problem = problemOf(result, 400);
        assertThat(problem.get("code").asText()).isEqualTo("QUOTE_NOT_FOUND");
        assertThat(errorFields(problem)).containsExactly("quoteId");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(UNKNOWN_QUOTE_ID);
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.6: a missing quoteId is a 400 naming quoteId, as a request shape error")
    void missingQuoteIdIsRejected() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kyc", kyc(dobForAge(35)));

        JsonNode problem = problemOf(api.apply("customer1", body), 400);

        assertThat(errorFields(problem)).containsExactly("quoteId");
    }

    @Test
    @DisplayName("AC-03.7: a second application for the same quote is a 409 QUOTE_ALREADY_APPLIED and the quote still has exactly one application")
    void secondApplicationForTheSameQuoteIsRejected() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        api.applyOk("customer1", quoteId, kyc(dobForAge(35)));
        Counts before = counts();

        MvcResult second = api.apply("customer1", application(quoteId, kyc(dobForAge(35))));
        MvcResult byAnotherCustomer = api.apply("customer2", application(quoteId, kyc(dobForAge(35))));

        for (MvcResult result : List.of(second, byAnotherCustomer)) {
            JsonNode problem = problemOf(result, 409);
            assertThat(problem.get("code").asText()).isEqualTo("QUOTE_ALREADY_APPLIED");
            assertThat(result.getResponse().getContentAsString()).doesNotContain(quoteId);
        }
        assertThat(applicationsForQuote(quoteId)).isEqualTo(1);
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.8: customer1 gets only its own applications from GET /api/applications/mine, newest first, in the submission shape with no KYC fields")
    void mineReturnsOnlyTheCallersApplicationsNewestFirst() throws Exception {
        String first = api.applyOk("customer1", api.quoteId("TERM_LIFE", cleanTermLifeInputs()),
                kyc(dobForAge(35))).get("applicationId").asText();
        String others = api.applyOk("customer2", api.quoteId("TERM_LIFE", cleanTermLifeInputs()),
                kyc(dobForAge(35))).get("applicationId").asText();
        clock.setDate(TODAY.plusDays(1));
        String second = api.applyOk("customer1", api.quoteId("MOTOR", motorInputs(30, 0)), kyc(dobForAge(30)))
                .get("applicationId").asText();

        MvcResult result = api.mine("customer1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode list = api.bodyOf(result);
        List<String> ids = list.findValuesAsText("applicationId");
        assertThat(ids).contains(first, second).doesNotContain(others);
        assertThat(ids.indexOf(second)).as("newest first").isLessThan(ids.indexOf(first));
        list.forEach(item -> assertThat(fieldNames(item)).containsExactlyInAnyOrderElementsOf(OWNER_RESPONSE_FIELDS));
        assertThat(result.getResponse().getContentAsString()).doesNotContain(ApplicationFixtures.AADHAAR)
                .doesNotContain(ApplicationFixtures.PAN).doesNotContain(ApplicationFixtures.FULL_NAME)
                .doesNotContain("kyc");
        List<String> customerIds = jdbc.queryForList(
                "select customer_id from application where application_id in (?, ?)", String.class, first, second);
        assertThat(customerIds).containsOnly("customer1");
    }

    @Test
    @DisplayName("NFR-04.3: a text/plain POST to /api/applications with a valid JSON body is a 415 and nothing is stored")
    void textPlainApplicationIsUnsupportedMediaType() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE", cleanTermLifeInputs());
        Counts before = counts();

        MvcResult result = mvc.perform(post("/api/applications")
                .with(httpBasic("customer1", ApplicationApi.PASSWORD))
                .contentType("text/plain")
                .content(json.writeValueAsString(application(quoteId, kyc(dobForAge(35)))))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(counts()).isEqualTo(before);
        assertThat(applicationsForQuote(quoteId)).isZero();
    }

    @Test
    @DisplayName("NFR-04.1: anonymous requests to the application endpoints are 401 and an UNDERWRITER gets 403")
    void applicationEndpointsAreForCustomersOnly() throws Exception {
        String body = json.writeValueAsString(application(UNKNOWN_QUOTE_ID, kyc(dobForAge(35))));

        assertThat(mvc.perform(post("/api/applications").contentType("application/json").content(body))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/applications/mine")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(api.apply("underwriter1", json.readTree(body)).getResponse().getStatus()).isEqualTo(403);
        assertThat(api.mine("underwriter1").getResponse().getStatus()).isEqualTo(403);
    }
}
