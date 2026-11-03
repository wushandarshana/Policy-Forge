package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.ApplicationFixtures.application;
import static com.horizon.policyforge.support.ApplicationFixtures.cleanTermLifeInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.kyc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Quote ownership (P-8, C10): the owner recorded on a signed-in customer's quote, hidden from every response, and the
 * owner-only apply with its anonymous claim (U-2 step 2). Ownership is read from the database because the API never
 * returns it. The startup seed (U-14) is present, so rows are found by ID and "nothing is stored" is proved with row
 * counts taken before and after.
 */
@Tag("AC-01")
@Tag("AC-03")
@Tag("NFR-04")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class QuoteOwnershipIT {

    private static final LocalDate TODAY = FixedClockConfig.TODAY;
    private static final String UNKNOWN_QUOTE_ID = "Q-00000000-0000-4000-8000-000000000000";
    private static final List<String> CREATE_FIELDS = List.of("quoteId", "productCode", "ruleVersion", "sumInsured",
            "annualPremium", "breakdown", "validUntil");
    private static final List<String> READ_FIELDS = List.of("quoteId", "productCode", "ruleVersion", "sumInsured",
            "annualPremium", "breakdown", "validUntil", "riskInputs");

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

    /** POST /api/quotes, optionally with Basic credentials ({@code user} null means none). */
    private MvcResult postQuote(String user, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", "TERM_LIFE");
        body.put("riskInputs", cleanTermLifeInputs());
        MockHttpServletRequestBuilder request = post("/api/quotes").contentType("application/json")
                .content(json.writeValueAsString(body));
        if (user != null) {
            request = request.with(httpBasic(user, password));
        }
        return mvc.perform(request).andReturn();
    }

    private String quoteAs(String user) throws Exception {
        MvcResult result = postQuote(user, ApplicationApi.PASSWORD);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return api.bodyOf(result).get("quoteId").asText();
    }

    private String ownerOf(String quoteId) {
        return jdbc.queryForObject("select owner_id from quote where quote_id = ?", String.class, quoteId);
    }

    private int quotes() {
        return jdbc.queryForObject("select count(*) from quote", Integer.class);
    }

    private int applicationsForQuote(String quoteId) {
        return jdbc.queryForObject("select count(*) from application where quote_id = ?", Integer.class, quoteId);
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

    private LocalDate dobForAge(int age) {
        return LocalDate.now(clock).minusYears(age);
    }

    private JsonNode problemOf(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        return api.bodyOf(result);
    }

    private JsonNode getQuote(String quoteId) throws Exception {
        MvcResult result = mvc.perform(get("/api/quotes/{id}", quoteId)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return api.bodyOf(result);
    }

    @Test
    @DisplayName("AC-01.12: customer1's quote is stored with owner customer1, and an anonymous quote is stored with no owner")
    void signedInCustomerOwnsTheQuoteAndAnAnonymousQuoteHasNoOwner() throws Exception {
        String owned = quoteAs("customer1");
        String anonymous = quoteAs(null);
        String ownedByTwo = quoteAs("customer2");

        assertThat(ownerOf(owned)).isEqualTo("customer1");
        assertThat(ownerOf(anonymous)).isNull();
        assertThat(ownerOf(ownedByTwo)).isEqualTo("customer2");
    }

    @Test
    @DisplayName("AC-01.12: the create response and GET /api/quotes/{id} carry no owner field and the same key set as before, for owned and anonymous quotes alike")
    void ownerIsNeverReturned() throws Exception {
        for (String user : new String[] {"customer1", null}) {
            MvcResult created = postQuote(user, ApplicationApi.PASSWORD);
            assertThat(created.getResponse().getStatus()).isEqualTo(201);
            JsonNode createBody = api.bodyOf(created);
            assertThat(fieldNames(createBody)).containsExactlyElementsOf(CREATE_FIELDS);

            JsonNode readBody = getQuote(createBody.get("quoteId").asText());
            assertThat(fieldNames(readBody)).containsExactlyElementsOf(READ_FIELDS);

            assertThat(created.getResponse().getContentAsString()).doesNotContain("owner").doesNotContain("customer1");
            assertThat(readBody.toString()).doesNotContain("owner").doesNotContain("customer1");
        }
    }

    @Test
    @DisplayName("AC-01.12: underwriter1's quote is stored with no owner, because only valid CUSTOMER credentials make an owner")
    void quoteFromAnotherRoleHasNoOwner() throws Exception {
        String byUnderwriter = quoteAs("underwriter1");
        String byAdmin = quoteAs("admin1");

        assertThat(ownerOf(byUnderwriter)).isNull();
        assertThat(ownerOf(byAdmin)).isNull();
    }

    @Test
    @DisplayName("AC-01.12: invalid credentials on POST /api/quotes get 401 and no quote row is added")
    void invalidCredentialsAreRefusedAndStoreNothing() throws Exception {
        int before = quotes();

        MvcResult wrongPassword = postQuote("customer1", "not-the-password");
        MvcResult unknownUser = postQuote("nobody", ApplicationApi.PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(quotes()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.9: customer2 applying for customer1's quote is a 400 QUOTE_NOT_FOUND naming quoteId, JSON-equal to the unknown-quote body, and nothing is stored")
    void anotherCustomersQuoteLooksExactlyLikeAMissingOne() throws Exception {
        String quoteId = quoteAs("customer1");
        JsonNode unknown = problemOf(api.apply("customer2", application(UNKNOWN_QUOTE_ID, kyc(dobForAge(35)))), 400);
        Counts before = counts();

        MvcResult result = api.apply("customer2", application(quoteId, kyc(dobForAge(35))));

        JsonNode problem = problemOf(result, 400);
        assertThat(problem.get("code").asText()).isEqualTo("QUOTE_NOT_FOUND");
        assertThat(errorFields(problem)).containsExactly("quoteId");
        assertThat(problem).isEqualTo(unknown);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(quoteId).doesNotContain("customer1");
        assertThat(counts()).isEqualTo(before);
        assertThat(applicationsForQuote(quoteId)).isZero();
    }

    @Test
    @DisplayName("AC-03.9: customer1 applying for customer1's own quote is accepted, and the stored owner is unchanged")
    void ownerCanApply() throws Exception {
        String quoteId = quoteAs("customer1");

        JsonNode created = api.applyOk("customer1", quoteId, kyc(dobForAge(35)));

        assertThat(created.get("quoteId").asText()).isEqualTo(quoteId);
        assertThat(applicationsForQuote(quoteId)).isEqualTo(1);
        assertThat(ownerOf(quoteId)).isEqualTo("customer1");
    }

    @Test
    @DisplayName("AC-03.9: an anonymous quote is claimed by the first customer who applies, a later customer gets 409 QUOTE_ALREADY_APPLIED, and the stored quote never gains an owner")
    void firstApplicantClaimsAnAnonymousQuote() throws Exception {
        String quoteId = quoteAs(null);

        api.applyOk("customer1", quoteId, kyc(dobForAge(35)));
        Counts before = counts();
        MvcResult later = api.apply("customer2", application(quoteId, kyc(dobForAge(35))));

        JsonNode problem = problemOf(later, 409);
        assertThat(problem.get("code").asText()).isEqualTo("QUOTE_ALREADY_APPLIED");
        assertThat(applicationsForQuote(quoteId)).isEqualTo(1);
        assertThat(counts()).isEqualTo(before);
        assertThat(ownerOf(quoteId)).as("quotes are append-only: claiming never updates the owner").isNull();
    }

    @Test
    @DisplayName("AC-03.9: ownership is checked before expiry, so customer2 applying for customer1's expired quote gets QUOTE_NOT_FOUND, not QUOTE_EXPIRED")
    void ownershipIsCheckedBeforeExpiry() throws Exception {
        JsonNode quote = api.bodyOf(postQuote("customer1", ApplicationApi.PASSWORD));
        String quoteId = quote.get("quoteId").asText();
        clock.setDate(LocalDate.parse(quote.get("validUntil").asText()).plusDays(1));
        Counts before = counts();

        JsonNode byOther = problemOf(api.apply("customer2", application(quoteId, kyc(dobForAge(35)))), 400);
        JsonNode byOwner = problemOf(api.apply("customer1", application(quoteId, kyc(dobForAge(35)))), 400);

        assertThat(byOther.get("code").asText()).isEqualTo("QUOTE_NOT_FOUND");
        assertThat(byOwner.get("code").asText()).isEqualTo("QUOTE_EXPIRED");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-03.9: ownership is checked before already-applied and KYC age, so another customer never learns the quote exists")
    void ownershipIsCheckedBeforeAlreadyAppliedAndAge() throws Exception {
        String applied = quoteAs("customer1");
        api.applyOk("customer1", applied, kyc(dobForAge(35)));
        String fresh = quoteAs("customer1");

        JsonNode afterApplied = problemOf(api.apply("customer2", application(applied, kyc(dobForAge(35)))), 400);
        JsonNode wrongAge = problemOf(api.apply("customer2", application(fresh, kyc(dobForAge(50)))), 400);

        assertThat(afterApplied.get("code").asText()).isEqualTo("QUOTE_NOT_FOUND");
        assertThat(wrongAge.get("code").asText()).isEqualTo("QUOTE_NOT_FOUND");
        assertThat(errorFields(wrongAge)).containsExactly("quoteId");
        assertThat(applicationsForQuote(fresh)).isZero();
    }

    @Test
    @DisplayName("NFR-04.1: POST /api/quotes and GET /api/quotes/{id} without credentials do not return 401")
    void quoteEndpointsStayPublic() throws Exception {
        MvcResult created = postQuote(null, null);
        String quoteId = api.bodyOf(created).get("quoteId").asText();
        int read = mvc.perform(get("/api/quotes/{id}", quoteId)).andReturn().getResponse().getStatus();

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(read).isEqualTo(200);
    }
}
