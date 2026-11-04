package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.decisionBody;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationFixtures.termLifeInputs;
import static com.horizon.policyforge.support.PolicyApi.DETAIL_FIELDS;
import static com.horizon.policyforge.support.PolicyApi.HEADER_FIELDS;
import static com.horizon.policyforge.support.PolicyApi.SUMMARY_FIELDS;
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
 * Viewing policies (AC-05.1, NFR-04.1): {@code GET /api/policies/mine} (C2) and {@code GET /api/policies/{number}}
 * (C3). The detail is built from an allow-list (P-9): no KYC field and no health input, even though the stored snapshot
 * keeps the full risk inputs. Every policy here is issued on 2026-11-01, so its first term ends on 2027-10-31.
 */
@Tag("AC-05")
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class PolicyViewIT {

    private static final LocalDate ISSUE_DAY = LocalDate.parse("2026-11-01");
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
        clock.setDate(ISSUE_DAY);
        api = new ApplicationApi(mvc, json);
        policies = new PolicyApi(mvc, json);
    }

    @AfterEach
    void resetClock() {
        clock.setDate(FixedClockConfig.TODAY);
    }

    /** Issues a new TERM_LIFE policy (5000000.00, 6850.00) for {@code customer} and returns the C1 body. */
    private JsonNode issuePolicyFor(String customer) throws Exception {
        String applicationId = api.approvedApplication(customer, ISSUE_DAY);
        return policies.issueOk(customer, applicationId, null);
    }

    private static List<String> numbersOf(JsonNode list) {
        List<String> numbers = new ArrayList<>();
        list.forEach(item -> numbers.add(item.get("policyNumber").asText()));
        return numbers;
    }

    @Test
    @DisplayName("AC-05.1: GET /api/policies/mine lists only the caller's policies, newest first, each with exactly the C2 fields, a next premium due date of 2027-11-01 and 0 endorsements")
    void mineListsTheCallersPoliciesNewestFirst() throws Exception {
        String first = issuePolicyFor("customer1").get("policyNumber").asText();
        String second = issuePolicyFor("customer1").get("policyNumber").asText();
        String others = issuePolicyFor("customer2").get("policyNumber").asText();

        MvcResult result = policies.mine("customer1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode list = policies.bodyOf(result);
        assertThat(list.isArray()).isTrue();
        List<String> numbers = numbersOf(list);
        assertThat(numbers).contains(first, second).doesNotContain(others);
        assertThat(numbers.indexOf(second)).as("newest first").isLessThan(numbers.indexOf(first));
        for (JsonNode item : list) {
            assertThat(fieldNames(item)).as("the C2 key set, and nothing else").isEqualTo(SUMMARY_FIELDS);
            assertThat(PolicyRows.policy(jdbc, item.get("policyNumber").asText()).get("CUSTOMER_ID"))
                    .as("every listed policy belongs to the caller").isEqualTo("customer1");
        }
        JsonNode item = list.get(numbers.indexOf(first));
        assertThat(item.get("productCode").asText()).isEqualTo("TERM_LIFE");
        assertThat(item.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(item.get("sumInsured").asText()).isEqualTo("5000000.00");
        assertThat(item.get("annualPremium").asText()).isEqualTo("6850.00");
        assertThat(item.get("nextPremiumDueDate").asText()).isEqualTo("2027-11-01");
        assertThat(item.get("endorsementCount").asInt()).isZero();
    }

    @Test
    @DisplayName("AC-05.1: GET /api/policies/{number} returns the C3 detail: the header of the issue response, version v1, the null to ACTIVE transition, term #1, no endorsements and no refunds")
    void detailReturnsTheC3Shape() throws Exception {
        JsonNode issued = issuePolicyFor("customer1");
        String number = issued.get("policyNumber").asText();

        MvcResult result = policies.detail("customer1", number);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode detail = policies.bodyOf(result);
        assertThat(fieldNames(detail)).isEqualTo(DETAIL_FIELDS);
        assertThat(fieldNames(detail.get("policy"))).isEqualTo(HEADER_FIELDS);
        assertThat(detail.get("policy")).as("the same header the issue returned").isEqualTo(issued);
        assertThat(detail.get("policy").get("nextPremiumDueDate").asText()).isEqualTo("2027-11-01");
        assertThat(detail.get("policy").get("endorsementCount").asInt()).isZero();

        JsonNode versions = detail.get("versions");
        assertThat(versions).hasSize(1);
        JsonNode version = versions.get(0);
        assertThat(fieldNames(version)).containsExactly("versionNo", "effectiveDate", "expiryDate", "sumInsured",
                "annualPremium", "ruleVersion", "riskInputs", "address", "nominees", "createdAt");
        assertThat(version.get("versionNo").asInt()).isEqualTo(1);
        assertThat(version.get("effectiveDate").asText()).isEqualTo("2026-11-01");
        assertThat(version.get("expiryDate").asText()).isEqualTo("2027-10-31");
        assertThat(version.get("sumInsured").asText()).isEqualTo("5000000.00");
        assertThat(version.get("annualPremium").asText()).isEqualTo("6850.00");
        assertThat(version.get("ruleVersion").asInt()).isEqualTo(issued.get("ruleVersion").asInt());
        assertThat(fieldNames(version.get("address"))).containsExactly("line1", "city", "postcode");
        assertThat(version.get("address").get("line1").asText()).isEqualTo(ApplicationFixtures.LINE1);
        assertThat(version.get("address").get("city").asText()).isEqualTo(ApplicationFixtures.CITY);
        assertThat(version.get("address").get("postcode").asText()).isEqualTo(ApplicationFixtures.POSTCODE);
        assertThat(version.get("nominees").isArray()).isTrue();
        assertThat(version.get("nominees")).isEmpty();
        assertThat(version.get("createdAt").asText()).isNotBlank();

        assertThat(detail.get("endorsements")).isEmpty();
        JsonNode transitions = detail.get("transitions");
        assertThat(transitions).hasSize(1);
        assertThat(fieldNames(transitions.get(0))).containsExactly("fromStatus", "toStatus", "reason", "actorId", "at");
        assertThat(transitions.get(0).get("fromStatus").isNull()).as("issuance has no previous status").isTrue();
        assertThat(transitions.get(0).get("toStatus").asText()).isEqualTo("ACTIVE");
        assertThat(transitions.get(0).get("reason").asText()).isEqualTo("ISSUANCE");
        assertThat(transitions.get(0).get("actorId").asText()).isEqualTo("customer1");
        JsonNode terms = detail.get("terms");
        assertThat(terms).hasSize(1);
        assertThat(fieldNames(terms.get(0))).containsExactly("termNo", "startDate", "endDate", "premium", "paid",
                "ruleVersion");
        assertThat(terms.get(0).get("termNo").asInt()).isEqualTo(1);
        assertThat(terms.get(0).get("startDate").asText()).isEqualTo("2026-11-01");
        assertThat(terms.get(0).get("endDate").asText()).isEqualTo("2027-10-31");
        assertThat(terms.get(0).get("premium").asText()).isEqualTo("6850.00");
        assertThat(terms.get(0).get("paid").asBoolean()).isTrue();
        assertThat(terms.get(0).get("ruleVersion").asInt()).as("the term records the quote's rule version")
                .isEqualTo(issued.get("ruleVersion").asInt());
        assertThat(detail.path("pendingRenewal").isNull()).as("no offer yet").isTrue();
        assertThat(detail.get("refunds").isArray()).isTrue();
        assertThat(detail.get("refunds")).isEmpty();
    }

    @Test
    @DisplayName("AC-05.1: the policy detail is built from an allow-list: no KYC field and no health input, while the stored snapshot keeps the full risk inputs")
    void detailCarriesNoKycAndNoHealthInputs() throws Exception {
        String quoteId = api.quoteId("TERM_LIFE",
                termLifeInputs(35, false, "5000000.00", "27.3189", List.of(ApplicationFixtures.HEALTH_TEXT)));
        String applicationId = api.applyOk("customer1", quoteId, ApplicationFixtures.kyc(ISSUE_DAY.minusYears(35)))
                .get("applicationId").asText();
        assertThat(ApplicationRows.status(jdbc, applicationId)).as("a health declaration refers it").isEqualTo("REFERRED");
        MvcResult approved = api.decide("underwriter1", applicationId,
                decisionBody("APPROVE", "UW-MAN-001", "Acceptable after medical review"));
        assertThat(approved.getResponse().getStatus()).isEqualTo(200);
        String number = policies.issueOk("customer1", applicationId, null).get("policyNumber").asText();

        MvcResult result = policies.detail("customer1", number);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        JsonNode riskInputs = policies.bodyOf(result).get("versions").get(0).get("riskInputs");
        assertThat(fieldNames(riskInputs)).containsExactly("age", "smoker", "sumInsured", "termYears");
        assertThat(body).doesNotContain("bmi", "healthDeclarations", ApplicationFixtures.HEALTH_TEXT, "27.3189",
                ApplicationFixtures.FULL_NAME, ApplicationFixtures.AADHAAR, ApplicationFixtures.PAN,
                ISSUE_DAY.minusYears(35).toString(), "fullName", "dateOfBirth", "aadhaar", "\"pan\"");
        assertThat(PolicyRows.riskInputsJson(jdbc, number, 1)).as("the snapshot keeps the health inputs for re-rating")
                .contains("bmi", ApplicationFixtures.HEALTH_TEXT);

        String list = policies.mine("customer1").getResponse().getContentAsString();
        assertThat(list).doesNotContain("bmi", ApplicationFixtures.HEALTH_TEXT, ApplicationFixtures.AADHAAR,
                ApplicationFixtures.FULL_NAME);
    }

    @Test
    @DisplayName("AC-05.1: another customer's, an unknown and a malformed policy number are 404 with one fixed body, and an UNDERWRITER gets 403")
    void policyDetailIsOwnerOnly() throws Exception {
        String number = issuePolicyFor("customer1").get("policyNumber").asText();

        MvcResult owner = policies.detail("customer1", number);
        MvcResult otherCustomer = policies.detail("customer2", number);
        MvcResult unknown = policies.detail("customer1", "PF-TL-2026-999999");

        assertThat(owner.getResponse().getStatus()).isEqualTo(200);
        assertThat(otherCustomer.getResponse().getStatus()).isEqualTo(404);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
        assertThat(otherCustomer.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(policies.bodyOf(otherCustomer).get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        assertThat(policies.bodyOf(otherCustomer)).as("another customer's policy looks exactly like a missing one")
                .isEqualTo(policies.bodyOf(unknown));
        for (String malformed : List.of("not-a-policy", "PF-XX-2026-000001", "PF-TL-26-1")) {
            MvcResult result = policies.detail("customer1", malformed);
            assertThat(result.getResponse().getStatus()).as(malformed).isEqualTo(404);
            assertThat(policies.bodyOf(result)).isEqualTo(policies.bodyOf(unknown));
        }
        assertThat(policies.detail("underwriter1", number).getResponse().getStatus()).isEqualTo(403);
    }
}
