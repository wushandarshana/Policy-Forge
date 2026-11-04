package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.householdInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static com.horizon.policyforge.support.ApplicationFixtures.motorInputs;
import static com.horizon.policyforge.support.PolicyApi.HEADER_FIELDS;
import static com.horizon.policyforge.support.PolicyApi.issueBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.ApplicationRows;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.PolicyApi;
import com.horizon.policyforge.support.PolicyRows;
import java.math.BigDecimal;
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
 * Policy issuance (AC-05.1, AC-05.3, AC-05.4, NFR-04.3): {@code POST /api/applications/{id}/issue} for an APPROVED
 * application, its date window, its refusals and what it writes. The startup seed is present, so rows are found by ID
 * and "nothing is stored" is proved with row counts taken before and after. The clock is a movable test clock that every
 * test puts back.
 */
@Tag("AC-05")
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class PolicyIssuanceIT {

    private static final LocalDate TODAY = FixedClockConfig.TODAY;
    private static final String UNKNOWN_APPLICATION_ID = "A-00000000-0000-4000-8000-000000000000";
    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ApplicationApi api;
    private PolicyApi policies;

    @BeforeEach
    void setUp() {
        clock.setDate(TODAY);
        api = new ApplicationApi(mvc, json);
        policies = new PolicyApi(mvc, json);
    }

    @AfterEach
    void resetClock() {
        clock.setDate(TODAY);
    }

    /** A new APPROVED TERM_LIFE application of customer1 (the AC-04.1 profile) on the clock's date. */
    private String approvedTermLife() throws Exception {
        String applicationId = api.approvedApplication("customer1", LocalDate.now(clock));
        assertThat(ApplicationRows.status(jdbc, applicationId)).isEqualTo("APPROVED");
        return applicationId;
    }

    private static void assertNotFound(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    }

    // ---- AC-05.1: what a successful issue returns and writes ----

    @Test
    @DisplayName("AC-05.1: an APPROVED TERM_LIFE application issued on 2026-11-01 gets 201, a number matching ^PF-TL-2026-\\d{6}$, effective date 2026-11-01, expiry 2027-10-31, the quote's sum insured 5000000.00 and premium 6850.00, the quote's rule version and status ACTIVE")
    void issuesAnApprovedTermLifeApplication() throws Exception {
        LocalDate issueDay = LocalDate.parse("2026-11-01");
        clock.setDate(issueDay);
        JsonNode quote = api.createQuote("TERM_LIFE", cleanTermLifeInputs());
        String applicationId = api.applyOk("customer1", quote.get("quoteId").asText(), kyc(issueDay.minusYears(35)))
                .get("applicationId").asText();
        assertThat(ApplicationRows.status(jdbc, applicationId)).isEqualTo("APPROVED");

        MvcResult result = policies.issue("customer1", applicationId, issueBody("2026-11-01"));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        JsonNode policy = policies.bodyOf(result);
        assertThat(fieldNames(policy)).as("the C1 key set, and nothing else").isEqualTo(HEADER_FIELDS);
        assertThat(policy.get("policyNumber").asText()).matches("^PF-TL-2026-\\d{6}$");
        assertThat(policy.get("applicationId").asText()).isEqualTo(applicationId);
        assertThat(policy.get("productCode").asText()).isEqualTo("TERM_LIFE");
        assertThat(policy.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(policy.get("currentVersionNo").asInt()).isEqualTo(1);
        assertThat(policy.get("effectiveDate").asText()).isEqualTo("2026-11-01");
        assertThat(policy.get("expiryDate").asText()).isEqualTo("2027-10-31");
        assertThat(policy.get("sumInsured").asText()).isEqualTo(quote.get("sumInsured").asText())
                .isEqualTo("5000000.00");
        assertThat(policy.get("annualPremium").asText()).isEqualTo(quote.get("annualPremium").asText())
                .isEqualTo("6850.00");
        assertThat(policy.get("ruleVersion").asInt()).isEqualTo(quote.get("ruleVersion").asInt());
        assertThat(policy.get("nextPremiumDueDate").asText()).isEqualTo("2027-11-01");
        assertThat(policy.get("endorsementCount").asInt()).isZero();
    }

    @Test
    @DisplayName("AC-05.1: an issue request with no effectiveDate (an empty body) defaults to today and expires on today + 12 months - 1 day")
    void omittedEffectiveDateDefaultsToToday() throws Exception {
        String applicationId = approvedTermLife();

        JsonNode policy = policies.issueOk("customer1", applicationId, null);

        assertThat(policy.get("effectiveDate").asText()).isEqualTo(TODAY.toString());
        assertThat(policy.get("expiryDate").asText()).isEqualTo(TODAY.plusMonths(12).minusDays(1).toString());
    }

    @Test
    @DisplayName("AC-05.1: today - 1 and today + 61 get 400 naming effectiveDate with EFFECTIVE_DATE_OUT_OF_RANGE and store nothing, and today + 60 is accepted")
    void effectiveDateWindowIsTodayToSixtyDaysAhead() throws Exception {
        String applicationId = approvedTermLife();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

        for (LocalDate outside : List.of(TODAY.minusDays(1), TODAY.plusDays(61))) {
            MvcResult result = policies.issue("customer1", applicationId, issueBody(outside.toString()));

            assertThat(result.getResponse().getStatus()).as(outside.toString()).isEqualTo(400);
            JsonNode problem = policies.bodyOf(result);
            assertThat(errorFields(problem)).containsExactly("effectiveDate");
            assertThat(problem.get("code").asText()).isEqualTo("EFFECTIVE_DATE_OUT_OF_RANGE");
            assertThat(result.getResponse().getContentAsString()).as("the submitted date is never echoed")
                    .doesNotContain(outside.toString());
            assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
        }

        LocalDate last = TODAY.plusDays(60);
        JsonNode accepted = policies.issueOk("customer1", applicationId, last.toString());
        assertThat(accepted.get("effectiveDate").asText()).isEqualTo(last.toString());
        assertThat(accepted.get("expiryDate").asText()).isEqualTo(last.plusMonths(12).minusDays(1).toString());
    }

    @Test
    @DisplayName("AC-05.1: a malformed or non-text effectiveDate and an unknown key get 400 naming the field, and store nothing")
    void malformedDatesAndUnknownKeysAreRefused() throws Exception {
        String applicationId = approvedTermLife();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

        for (Object bad : List.of("01/11/2026", "2026-13-40", "2026-1-1", " 2026-11-01", "", 20261101,
                Map.of("year", 2026))) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("effectiveDate", bad);
            MvcResult result = policies.issue("customer1", applicationId, body);

            assertThat(result.getResponse().getStatus()).as(String.valueOf(bad)).isEqualTo(400);
            JsonNode problem = policies.bodyOf(result);
            assertThat(errorFields(problem)).containsExactly("effectiveDate");
            assertThat(problem.has("code")).as("a format error has no business code").isFalse();
            assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
        }

        Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("status", "ISSUED");
        unknown.put("customerId", "customer2");
        MvcResult result = policies.issue("customer1", applicationId, unknown);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorFields(policies.bodyOf(result))).containsExactly("customerId", "status");
        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-05.1: an unknown key of 5000 characters gets 400 naming the key cut to its first 64 characters, the full key is never echoed, and nothing is stored")
    void aLongUnknownKeyIsEchoedOnlyInItsBoundedForm() throws Exception {
        String applicationId = approvedTermLife();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);
        String longKey = "unknownKey" + "Q".repeat(4990);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(longKey, "x");

        MvcResult result = policies.issue("customer1", applicationId, body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode problem = policies.bodyOf(result);
        assertThat(errorFields(problem)).containsExactly(longKey.substring(0, 64));
        assertThat(problem.get("errors").get(0).get("message").asText()).isEqualTo("is not an accepted field");
        assertThat(problem.get("errors")).as("no other field is named").hasSize(1);
        assertThat(result.getResponse().getContentAsString())
                .as("the key is never reflected beyond 64 characters")
                .doesNotContain(longKey.substring(0, 65));
        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-05.1: unknown keys holding <, >, quotes and spaces are echoed with each of those characters replaced by an underscore, keys that sanitise to the same text are named once, and nothing is stored")
    void unknownKeysAreEchoedSanitised() throws Exception {
        String applicationId = approvedTermLife();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("<a b=\"c\" d='e'>", "x");
        body.put("a b", "x");
        body.put("a<b", "x");
        body.put("valid.Key-1_x", "x");

        MvcResult result = policies.issue("customer1", applicationId, body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode problem = policies.bodyOf(result);
        assertThat(errorFields(problem)).containsExactly("_a_b__c__d__e__", "a_b", "valid.Key-1_x");
        problem.get("errors").forEach(error -> assertThat(error.get("message").asText())
                .isEqualTo("is not an accepted field"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("<").doesNotContain(">")
                .doesNotContain("'").doesNotContain("a b");
        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-05.1: MOTOR and HOUSEHOLD applications get PF-MO- and PF-HH- policy numbers")
    void otherProductsGetTheirOwnPrefix() throws Exception {
        String motor = api.applyAs("customer1", "MOTOR", motorInputs(30, 0), 30, TODAY);
        String household = api.applyAs("customer1", "HOUSEHOLD", householdInputs("BRICK"), 50, TODAY);
        assertThat(ApplicationRows.status(jdbc, motor)).isEqualTo("APPROVED");
        assertThat(ApplicationRows.status(jdbc, household)).isEqualTo("APPROVED");

        JsonNode motorPolicy = policies.issueOk("customer1", motor, null);
        JsonNode householdPolicy = policies.issueOk("customer1", household, null);

        assertThat(motorPolicy.get("policyNumber").asText()).matches("^PF-MO-2026-\\d{6}$");
        assertThat(motorPolicy.get("productCode").asText()).isEqualTo("MOTOR");
        assertThat(householdPolicy.get("policyNumber").asText()).matches("^PF-HH-2026-\\d{6}$");
        assertThat(householdPolicy.get("productCode").asText()).isEqualTo("HOUSEHOLD");
    }

    // ---- AC-05.3: what cannot be issued ----

    @Test
    @DisplayName("AC-05.3: a REFERRED and a DECLINED application get 409 INVALID_APPLICATION_STATE and nothing is stored")
    void referredAndDeclinedApplicationsCannotBeIssued() throws Exception {
        for (String applicationId : List.of(api.referredApplication("customer1", TODAY),
                api.declinedApplication("customer1", TODAY))) {
            PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

            MvcResult result = policies.issue("customer1", applicationId, issueBody(null));

            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
            assertThat(policies.bodyOf(result).get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
            assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
            assertThat(PolicyRows.policiesForApplication(jdbc, applicationId)).isZero();
        }
    }

    @Test
    @DisplayName("AC-05.3: a second issue of an issued application gets 409 and the application still has exactly one policy")
    void anApplicationIsIssuedOnlyOnce() throws Exception {
        String applicationId = approvedTermLife();
        JsonNode first = policies.issueOk("customer1", applicationId, null);
        PolicyRows.Snapshot afterFirst = PolicyRows.snapshot(jdbc, applicationId);

        MvcResult second = policies.issue("customer1", applicationId, issueBody(null));

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(policies.bodyOf(second).get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
        assertThat(PolicyRows.policiesForApplication(jdbc, applicationId)).isEqualTo(1);
        assertThat(PolicyRows.numberFor(jdbc, applicationId)).isEqualTo(first.get("policyNumber").asText());
        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("the refused issue stored nothing")
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("AC-05.3: another customer's, an unknown and a malformed application ID are 404 with one fixed body, and nothing is stored")
    void unownedUnknownAndMalformedApplicationsAreNotFound() throws Exception {
        String applicationId = approvedTermLife();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

        MvcResult otherCustomer = policies.issue("customer2", applicationId, issueBody(null));
        MvcResult unknown = policies.issue("customer1", UNKNOWN_APPLICATION_ID, issueBody(null));

        assertNotFound(otherCustomer);
        assertNotFound(unknown);
        assertThat(policies.bodyOf(otherCustomer).get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        assertThat(policies.bodyOf(otherCustomer)).as("another customer's application looks exactly like a missing one")
                .isEqualTo(policies.bodyOf(unknown));
        for (String malformed : List.of("not-an-application", "A-1", "A-0000000000004000800000000000")) {
            MvcResult result = policies.issue("customer1", malformed, issueBody(null));
            assertNotFound(result);
            assertThat(policies.bodyOf(result)).isEqualTo(policies.bodyOf(unknown));
        }
        assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);

        assertThat(policies.issue("customer1", applicationId, issueBody(null)).getResponse().getStatus())
                .as("the owner can still issue it").isEqualTo(201);
    }

    // ---- AC-05.4: what an issue writes ----

    @Test
    @DisplayName("AC-05.4: an issued policy has exactly one transition (null to ACTIVE) and exactly one version v1, and its status equals that transition's target")
    void issuedPolicyHasOneTransitionAndOneVersion() throws Exception {
        JsonNode quote = api.createQuote("TERM_LIFE", cleanTermLifeInputs());
        String applicationId = api.applyOk("customer1", quote.get("quoteId").asText(), kyc(TODAY.minusYears(35)))
                .get("applicationId").asText();
        JsonNode issued = policies.issueOk("customer1", applicationId, null);
        String number = issued.get("policyNumber").asText();

        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions).hasSize(1);
        Map<String, Object> transition = transitions.get(0);
        assertThat(transition.get("FROM_STATUS")).as("a new policy has no previous status").isNull();
        assertThat(transition.get("TO_STATUS")).isEqualTo("ACTIVE");
        assertThat(transition.get("REASON")).isEqualTo("ISSUANCE");
        assertThat(transition.get("ACTOR_ID")).isEqualTo("customer1");
        assertThat(ApplicationRows.instant(transition.get("OCCURRED_AT"))).isEqualTo(clock.instant());

        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).get("VERSION_NO")).isEqualTo(1);
        assertThat(PolicyRows.policy(jdbc, number).get("STATUS")).as("the status equals the transition's to")
                .isEqualTo(transition.get("TO_STATUS"));
        assertThat(PolicyRows.policy(jdbc, number).get("CURRENT_VERSION_NO")).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-05.4: version v1 is copied from the quote and the KYC address, with no nominees, and term #1 is paid for the whole term")
    void versionOneAndTermOneAreCopiedFromTheQuote() throws Exception {
        JsonNode quote = api.createQuote("TERM_LIFE", cleanTermLifeInputs());
        String quoteId = quote.get("quoteId").asText();
        String applicationId = api.applyOk("customer1", quoteId, kyc(TODAY.minusYears(35))).get("applicationId")
                .asText();
        String number = policies.issueOk("customer1", applicationId, "2026-11-01").get("policyNumber").asText();

        Map<String, Object> version = PolicyRows.versions(jdbc, number).get(0);
        assertThat((BigDecimal) version.get("SUM_INSURED")).isEqualByComparingTo("5000000.00");
        assertThat((BigDecimal) version.get("ANNUAL_PREMIUM")).isEqualByComparingTo("6850.00");
        assertThat(version.get("RULE_VERSION")).isEqualTo(quote.get("ruleVersion").asInt());
        assertThat(version.get("EFFECTIVE_DATE").toString()).isEqualTo("2026-11-01");
        assertThat(version.get("EXPIRY_DATE").toString()).isEqualTo("2027-10-31");
        assertThat(version.get("ADDRESS_LINE1")).isEqualTo(ApplicationFixtures.LINE1);
        assertThat(version.get("ADDRESS_CITY")).isEqualTo(ApplicationFixtures.CITY);
        assertThat(version.get("ADDRESS_POSTCODE")).isEqualTo(ApplicationFixtures.POSTCODE);
        assertThat(PolicyRows.nomineesJson(jdbc, number, 1)).isEqualTo("[]");
        assertThat(PolicyRows.riskInputsJson(jdbc, number, 1)).as("the full snapshot of the quote's risk inputs")
                .isEqualTo(jdbc.queryForObject("select risk_inputs_json from quote where quote_id = ?", String.class,
                        quoteId));

        List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
        assertThat(terms).hasSize(1);
        assertThat(terms.get(0).get("TERM_NO")).isEqualTo(1);
        assertThat(terms.get(0).get("START_DATE").toString()).isEqualTo("2026-11-01");
        assertThat(terms.get(0).get("END_DATE").toString()).isEqualTo("2027-10-31");
        assertThat((BigDecimal) terms.get(0).get("PREMIUM")).isEqualByComparingTo("6850.00");
        assertThat(terms.get(0).get("PAID")).as("the first premium payment is simulated at issuance").isEqualTo(true);
    }

    @Test
    @DisplayName("AC-05.4: issuing moves the application APPROVED to ISSUED and appends an APPLICATION_ISSUED audit entry by the customer")
    void issuingMovesTheApplicationToIssuedAndAudits() throws Exception {
        String applicationId = approvedTermLife();
        int auditBefore = ApplicationRows.audit(jdbc, applicationId).size();

        policies.issueOk("customer1", applicationId, null);

        assertThat(ApplicationRows.status(jdbc, applicationId)).isEqualTo("ISSUED");
        List<Map<String, Object>> audit = ApplicationRows.audit(jdbc, applicationId);
        assertThat(audit).hasSize(auditBefore + 1);
        Map<String, Object> entry = audit.get(audit.size() - 1);
        assertThat(entry.get("ACTION")).isEqualTo("APPLICATION_ISSUED");
        assertThat(entry.get("ACTOR_ID")).isEqualTo("customer1");
        assertThat(entry.get("PREVIOUS_STATUS")).isEqualTo("APPROVED");
        assertThat(entry.get("NEW_STATUS")).isEqualTo("ISSUED");
    }

    // ---- NFR-04.3: JSON only ----

    @Test
    @DisplayName("NFR-04.3: a text/plain issue request gets 415 with the fixed detail and stores nothing")
    void issueConsumesJsonOnly() throws Exception {
        String applicationId = approvedTermLife();
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, applicationId);

        for (String contentType : List.of("text/plain", "application/x-www-form-urlencoded")) {
            MvcResult result = policies.issueRaw("customer1", applicationId, contentType, "{}");

            assertThat(result.getResponse().getStatus()).as(contentType).isEqualTo(415);
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
            assertThat(policies.bodyOf(result).get("detail").asText())
                    .isEqualTo("The request content type must be application/json.");
            assertThat(PolicyRows.snapshot(jdbc, applicationId)).as("nothing stored").isEqualTo(before);
        }
        assertThat(PolicyRows.policiesForApplication(jdbc, applicationId)).isZero();
    }
}
