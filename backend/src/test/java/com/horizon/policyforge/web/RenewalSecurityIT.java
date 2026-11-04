package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.RenewalApi;
import com.horizon.policyforge.support.RenewalRows;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The authentication, request-format and request-shape boundary of the two renewal endpoints (NFR-04.1, NFR-04.3, plan
 * C1, C2 and P-5): the admin cycle job and the owner's payment. Every refused request is proved to write nothing, and
 * nothing a caller typed is echoed except a key name, which goes through the shared sanitiser first.
 */
@Tag("NFR-04")
class RenewalSecurityIT extends RenewalTestSupport {

    private static final String FIXED_415_DETAIL = "The request content type must be application/json.";

    /** A TERM_LIFE policy with its offer pending, and the clock inside the payment window. */
    private String offered() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-10-05");
        return number;
    }

    private static Map<String, Object> body(String key, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(key, value);
        return body;
    }

    @Test
    @DisplayName("NFR-04.1: the renewal job answers anonymous with 401 and no Basic challenge, customer1 and underwriter1 with 403, and admin1 with 200, and a refused call runs nothing and audits nothing")
    void onlyAnAdminCanRunTheJob() throws Exception {
        String number = issued("TERM_LIFE");
        int auditBefore = RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN").size();
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        MvcResult anonymous = renewals.job(null, "2027-10-01", Map.of());
        MvcResult customer = renewals.job("customer1", "2027-10-01", Map.of());
        MvcResult underwriter = renewals.job("underwriter1", "2027-10-01", Map.of());
        MvcResult badPassword = mvc.perform(post(RenewalApi.JOB_PATH + "?date=2027-10-01")
                .contentType("application/json").content("{}").with(httpBasic("admin1", "wrong-password")))
                .andReturn();

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(anonymous.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(anonymous.getResponse().getHeader("WWW-Authenticate")).isNull();
        assertThat(badPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(badPassword.getResponse().getHeader("WWW-Authenticate")).isNull();
        assertThat(customer.getResponse().getStatus()).isEqualTo(403);
        assertThat(underwriter.getResponse().getStatus()).isEqualTo(403);
        assertThat(RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN")).as("no run was audited").hasSize(auditBefore);
        assertThat(RenewalRows.snapshot(jdbc, number)).as("no run happened").isEqualTo(before);

        MvcResult admin = renewals.job("admin1", "2027-10-01", Map.of());

        assertThat(admin.getResponse().getStatus()).as(admin.getResponse().getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("NFR-04.1: paying answers anonymous with 401 and no Basic challenge, underwriter1 and admin1 with 403, and another customer and an unknown policy number with 404, and writes nothing")
    void onlyTheOwningCustomerCanPay() throws Exception {
        String number = offered();
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        MvcResult anonymous = renewals.pay(null, number, Map.of());
        MvcResult underwriter = renewals.pay("underwriter1", number, Map.of());
        MvcResult admin = renewals.pay("admin1", number, Map.of());

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(anonymous.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(anonymous.getResponse().getHeader("WWW-Authenticate")).isNull();
        assertThat(underwriter.getResponse().getStatus()).isEqualTo(403);
        assertThat(admin.getResponse().getStatus()).isEqualTo(403);
        for (MvcResult notFound : List.of(renewals.pay("customer2", number, Map.of()),
                renewals.pay("customer1", "PF-TL-2026-999999", Map.of()),
                renewals.pay("customer1", "PF-XX-0000-000000", Map.of()))) {
            JsonNode problem = problem(notFound, 404);
            assertThat(problem.get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
        }
        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);

        MvcResult owner = renewals.pay("customer1", number, Map.of());

        assertThat(owner.getResponse().getStatus()).as("the owner is let through").isEqualTo(200);
    }

    @Test
    @DisplayName("AC-07.1: an unknown key in the job body is 400 naming it sanitised (a 5000-character key is cut to 64, markup characters become underscores), a malformed date is 400 naming date, both are reported together, and nothing is run or audited")
    void theJobRefusesABadShape() throws Exception {
        String number = issued("TERM_LIFE");
        int auditBefore = RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN").size();
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);
        String longKey = "k".repeat(5000);

        MvcResult unknown = renewals.job("admin1", "2027-10-01", body("<b>unknown key</b>", 1));
        MvcResult longUnknown = renewals.job("admin1", "2027-10-01", body(longKey, 1));
        MvcResult malformed = renewals.job("admin1", "2027-13-45", Map.of());
        MvcResult notADate = renewals.job("admin1", "tomorrow", Map.of());
        MvcResult both = renewals.job("admin1", "not-a-date", body("amount", 1));

        JsonNode unknownProblem = problem(unknown, 400);
        assertThat(errorFields(unknownProblem)).containsExactly("_b_unknown_key__b_");
        assertThat(unknown.getResponse().getContentAsString()).doesNotContain("<b>");
        assertThat(errorFields(problem(longUnknown, 400))).containsExactly("k".repeat(64));
        assertThat(longUnknown.getResponse().getContentAsString()).doesNotContain("k".repeat(65));
        assertThat(errorFields(problem(malformed, 400))).containsExactly("date");
        assertThat(malformed.getResponse().getContentAsString()).doesNotContain("2027-13-45");
        assertThat(errorFields(problem(notADate, 400))).containsExactly("date");
        assertThat(errorFields(problem(both, 400))).containsExactlyInAnyOrder("amount", "date");
        assertThat(RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN")).as("no refused call is audited").hasSize(auditBefore);
        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing changed").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-07.5: an unknown key in the pay body is 400 naming it sanitised, including date and amount, and nothing is written, while an empty object is accepted")
    void payRefusesABadShape() throws Exception {
        String number = offered();
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        MvcResult amount = renewals.pay("customer1", number, body("amount", "1.00"));
        MvcResult date = renewals.pay("customer1", number, body("date", "not-a-date"));
        MvcResult markup = renewals.pay("customer1", number, body("<script>", 1));
        MvcResult longKey = renewals.pay("customer1", number, body("p".repeat(5000), 1));

        assertThat(errorFields(problem(amount, 400))).containsExactly("amount");
        assertThat(errorFields(problem(date, 400))).as("a malformed date is just an unknown key here")
                .containsExactly("date");
        assertThat(date.getResponse().getContentAsString()).doesNotContain("not-a-date");
        assertThat(errorFields(problem(markup, 400))).containsExactly("_script_");
        assertThat(errorFields(problem(longKey, 400))).containsExactly("p".repeat(64));
        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing written").isEqualTo(before);

        MvcResult empty = renewals.pay("customer1", number, Map.of());

        assertThat(empty.getResponse().getStatus()).as(empty.getResponse().getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("NFR-04.3: a text/plain or form-encoded job or payment is 415 with the fixed detail and changes nothing, even though the same text is a valid JSON body")
    void onlyJsonIsAccepted() throws Exception {
        String number = offered();
        int auditBefore = RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN").size();
        RenewalRows.Snapshot before = RenewalRows.snapshot(jdbc, number);

        for (String contentType : List.of("text/plain", "application/x-www-form-urlencoded")) {
            MvcResult job = renewals.jobRaw("admin1", "2027-12-01", contentType, "{}");
            MvcResult pay = renewals.payRaw("customer1", number, contentType, "{}");

            for (MvcResult refused : List.of(job, pay)) {
                JsonNode problem = problem(refused, 415);
                assertThat(problem.get("detail").asText()).isEqualTo(FIXED_415_DETAIL);
            }
        }

        assertThat(RenewalRows.audit(jdbc, "RENEWAL_CYCLE_RUN")).hasSize(auditBefore);
        assertThat(RenewalRows.snapshot(jdbc, number)).as("nothing changed").isEqualTo(before);
        assertThat(renewals.job("admin1", "2027-10-02", Map.of()).getResponse().getStatus())
                .as("the same body as JSON is accepted").isEqualTo(200);
        assertThat(renewals.pay("customer1", number, Map.of()).getResponse().getStatus()).isEqualTo(200);
    }
}
