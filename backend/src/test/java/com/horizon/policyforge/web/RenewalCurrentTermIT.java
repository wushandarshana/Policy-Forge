package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static com.horizon.policyforge.support.PolicyApi.PENDING_RENEWAL_FIELDS;
import static com.horizon.policyforge.support.PolicyApi.TERM_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.PolicyRows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The current paid term (plan G-5) and the detail additions (contract C5). The current paid term is the paid term with
 * the highest number: an unpaid offer changes nothing, and a paid renewal term is current from the moment it is paid.
 * The header, {@code nextPremiumDueDate}, the endorsement window and the endorsement preview all follow it.
 */
@Tag("AC-07")
class RenewalCurrentTermIT extends RenewalTestSupport {

    @Test
    @DisplayName("AC-07.1: after an offer exists the header dates, nextPremiumDueDate, the endorsement window and the AC-06.2 preview (9490.00, 1330.85, 184, 365) are unchanged, because term 1 stays the current paid term")
    void anUnpaidOfferChangesNothing() throws Exception {
        String number = issued("TERM_LIFE");
        JsonNode headerBefore = detail(number).get("policy");
        JsonNode previewBefore = endorsements.previewOk(number, sumInsuredBody("7000000.00", "2027-05-01"));
        renewals.jobOk("2027-10-01");
        at("2027-10-01");

        JsonNode detail = detail(number);
        JsonNode header = detail.get("policy");

        assertThat(header).as("the header is exactly what it was before the offer").isEqualTo(headerBefore);
        assertThat(header.get("effectiveDate").asText()).isEqualTo("2026-11-01");
        assertThat(header.get("expiryDate").asText()).isEqualTo("2027-10-31");
        assertThat(header.get("nextPremiumDueDate").asText()).isEqualTo("2027-11-01");
        assertThat(header.get("annualPremium").asText()).isEqualTo("6850.00");
        JsonNode previewAfter = endorsements.previewOk(number, sumInsuredBody("7000000.00", "2027-05-01"));
        assertThat(previewAfter).isEqualTo(previewBefore);
        assertThat(previewAfter.get("newAnnualPremium").asText()).isEqualTo("9490.00");
        assertThat(previewAfter.get("adjustment").asText()).isEqualTo("1330.85");
        assertThat(previewAfter.get("unexpiredDays").asInt()).isEqualTo(184);
        assertThat(previewAfter.get("termDays").asInt()).isEqualTo(365);
        MvcResult inTheOffer = endorsements.preview("customer1", number, sumInsuredBody("7000000.00", "2027-11-02"));
        JsonNode problem = problem(inTheOffer, 400);
        assertThat(problem.get("code").asText()).as("a date in the unpaid term is outside the current term")
                .isEqualTo("EFFECTIVE_DATE_OUT_OF_TERM");
        assertThat(detail.get("terms")).hasSize(2);
    }

    @Test
    @DisplayName("AC-07.1: the detail carries the C5 additions: terms[].ruleVersion, and pendingRenewal with the offered term and payableUntil = the current paid term's end + its grace days")
    void theDetailShowsThePendingRenewal() throws Exception {
        String number = issued("TERM_LIFE");
        assertThat(detail(number).path("pendingRenewal").isNull()).as("no offer yet").isTrue();
        renewals.jobOk("2027-10-01");
        at("2027-10-01");

        JsonNode detail = detail(number);

        JsonNode terms = detail.get("terms");
        assertThat(terms).hasSize(2);
        for (JsonNode term : terms) {
            assertThat(fieldNames(term)).as("the C5 term key set").isEqualTo(TERM_FIELDS);
            assertThat(term.get("ruleVersion").asInt()).isEqualTo(1);
        }
        assertThat(terms.get(1).get("paid").asBoolean()).isFalse();
        JsonNode pending = detail.path("pendingRenewal");
        assertThat(pending.isObject()).isTrue();
        assertThat(fieldNames(pending)).as("the C5 pendingRenewal key set").isEqualTo(PENDING_RENEWAL_FIELDS);
        assertThat(pending.get("termNo").asInt()).isEqualTo(2);
        assertThat(pending.get("startDate").asText()).isEqualTo("2027-11-01");
        assertThat(pending.get("endDate").asText()).isEqualTo("2028-10-31");
        assertThat(pending.get("premium").asText()).isEqualTo("6850.00");
        assertThat(pending.get("ruleVersion").asInt()).isEqualTo(1);
        assertThat(pending.get("payableUntil").asText()).as("2027-10-31 + 30 grace days").isEqualTo("2027-11-30");
    }

    @Test
    @DisplayName("AC-07.1: payableUntil follows the product's grace days: 2027-11-15 for a MOTOR policy (grace 15)")
    void payableUntilUsesTheProductsGrace() throws Exception {
        String number = issued("MOTOR");
        renewals.jobOk("2027-10-01");
        at("2027-10-01");

        JsonNode pending = detail(number).path("pendingRenewal");

        assertThat(pending.get("payableUntil").asText()).isEqualTo("2027-11-15");
        assertThat(pending.get("premium").asText()).isEqualTo("15150.00");
    }

    @Test
    @DisplayName("AC-07.5: after the payment the header shows the renewal term's dates, there is no pending renewal, an endorsement is rated against the renewal term, and its version keeps the renewal expiry")
    void afterPaymentTheRenewalTermIsCurrent() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        at("2027-10-05");

        renewals.payOk(number);

        JsonNode detail = detail(number);
        JsonNode header = detail.get("policy");
        assertThat(header.get("status").asText()).isEqualTo("RENEWED");
        assertThat(header.get("effectiveDate").asText()).isEqualTo("2027-11-01");
        assertThat(header.get("expiryDate").asText()).isEqualTo("2028-10-31");
        assertThat(header.get("nextPremiumDueDate").asText()).isEqualTo("2028-11-01");
        assertThat(detail.path("pendingRenewal").isNull()).as("the offer is paid, nothing is pending").isTrue();
        assertThat(detail.get("terms").get(1).get("paid").asBoolean()).isTrue();
        MvcResult inOldTerm = endorsements.preview("customer1", number, newAddressBody("2027-05-01"));
        assertThat(problem(inOldTerm, 400).get("code").asText()).as("the old term is no longer the current one")
                .isEqualTo("EFFECTIVE_DATE_OUT_OF_TERM");

        JsonNode endorsed = endorsements.endorseOk(number, newAddressBody("2027-11-15"));

        assertThat(endorsed.get("endorsement").get("beforeVersionNo").asInt()).isEqualTo(2);
        assertThat(endorsed.get("endorsement").get("afterVersionNo").asInt()).isEqualTo(3);
        assertThat(endorsed.get("policy").get("effectiveDate").asText()).isEqualTo("2027-11-01");
        assertThat(endorsed.get("policy").get("expiryDate").asText()).isEqualTo("2028-10-31");
        assertThat(String.valueOf(PolicyRows.versions(jdbc, number).get(2).get("EXPIRY_DATE"))).isEqualTo("2028-10-31");
        JsonNode preview = endorsements.previewOk(number, sumInsuredBody("7000000.00", "2028-05-01"));
        assertThat(preview.get("termDays").asInt()).as("2027-11-01..2028-10-31 has 366 days").isEqualTo(366);
    }

    @Test
    @DisplayName("AC-07.4: a lapsed policy shows no pending renewal in its detail, because the offer can no longer be paid")
    void aLapsedPolicyHasNoPendingRenewal() throws Exception {
        String number = issued("TERM_LIFE");
        renewals.jobOk("2027-10-01");
        renewals.jobOk("2027-12-01");
        at("2027-12-01");

        JsonNode detail = detail(number);

        assertThat(detail.get("policy").get("status").asText()).isEqualTo("LAPSED");
        assertThat(detail.path("pendingRenewal").isNull()).isTrue();
        assertThat(detail.get("terms")).as("the unpaid term stays in the history").hasSize(2);
    }
}
