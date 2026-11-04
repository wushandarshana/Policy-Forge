package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.PolicyRows;
import com.horizon.policyforge.support.RenewalRows;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Re-pricing a pending renewal offer (AC-07.7, security review MAJOR-1): a CHANGE_SUM_INSURED endorsement applied while
 * an unpaid renewal term exists re-prices that term in the same transaction, re-quoting the endorsed risk inputs with the
 * rule version the term records. The term's dates and rule version are unchanged and it is never paid by this step. An
 * endorsement that does not re-rate leaves the offer alone. The numbers are the spec's: the AC-07.1 offer at 6850.00
 * (rule version 1) for sum insured 5,000,000, the sum insured changed to 7,000,000 effective 2027-10-05, giving 9490.00
 * (cross-checked with premium_check.py: {@code quote TERM_LIFE {sumInsured: 7000000.00, age 35, non-smoker, 20 years}
 * --version 1 = 9490.00}).
 */
@Tag("AC-07")
@Tag("AC-07.7")
class RenewalRepriceIT extends RenewalTestSupport {

    private static String money(Object value) {
        return ((BigDecimal) value).toPlainString();
    }

    private Map<String, Object> term2(String number) {
        List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
        assertThat(terms).hasSize(2);
        return terms.get(1);
    }

    private int term2RuleVersion(String number) {
        return jdbc.queryForObject("select rule_version from policy_term where policy_number = ? and term_no = 2",
                Integer.class, number);
    }

    @Test
    @DisplayName("AC-07.7: the AC-07.1 offer at 6850.00 / v1, the sum insured changed to 7,000,000 effective 2027-10-05: the pending term's premium becomes 9490.00 with its dates and rule version unchanged and still unpaid, and paying on 2027-10-06 records the RENEWED version with sum insured 7000000.00 and premium 9490.00")
    void aSumInsuredChangeRepricesThePendingOffer() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");
        renewals.jobOk("2027-10-01");
        Map<String, Object> offered = term2(number);
        assertThat(money(offered.get("PREMIUM"))).isEqualTo("6850.00");
        assertThat(term2RuleVersion(number)).isEqualTo(1);

        at("2027-10-05");
        JsonNode endorsed = endorsements.endorseOk(number, sumInsuredBody("7000000", "2027-10-05"));

        assertThat(endorsed.get("policy").get("sumInsured").asText()).isEqualTo("7000000.00");
        Map<String, Object> repriced = term2(number);
        assertThat(money(repriced.get("PREMIUM"))).as("the offer is re-priced").isEqualTo("9490.00");
        assertThat(repriced.get("START_DATE").toString()).isEqualTo("2027-11-01");
        assertThat(repriced.get("END_DATE").toString()).isEqualTo("2028-10-31");
        assertThat(repriced.get("PAID")).as("re-pricing never pays the term").isEqualTo(false);
        assertThat(term2RuleVersion(number)).as("the term keeps its rule version").isEqualTo(1);
        assertThat(RenewalRows.status(jdbc, number)).isEqualTo("ENDORSED");

        at("2027-10-06");
        JsonNode paid = renewals.payOk(number);

        assertThat(paid.get("term").get("premium").asText()).isEqualTo("9490.00");
        assertThat(paid.get("term").get("ruleVersion").asInt()).isEqualTo(1);
        assertThat(paid.get("policy").get("status").asText()).isEqualTo("RENEWED");
        assertThat(paid.get("policy").get("sumInsured").asText()).isEqualTo("7000000.00");
        assertThat(paid.get("policy").get("annualPremium").asText()).isEqualTo("9490.00");
        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        assertThat(versions).as("issued, endorsed, renewed").hasSize(3);
        Map<String, Object> renewed = versions.get(2);
        assertThat(money(renewed.get("SUM_INSURED"))).isEqualTo("7000000.00");
        assertThat(money(renewed.get("ANNUAL_PREMIUM"))).isEqualTo("9490.00");
        assertThat(((Number) renewed.get("RULE_VERSION")).intValue()).isEqualTo(1);
        assertThat(renewed.get("EFFECTIVE_DATE").toString()).isEqualTo("2027-11-01");
        assertThat(renewed.get("EXPIRY_DATE").toString()).isEqualTo("2028-10-31");
        assertThat(money(term2(number).get("PREMIUM"))).isEqualTo("9490.00");
        assertThat(term2(number).get("PAID")).isEqualTo(true);
    }

    @Test
    @DisplayName("AC-07.7: the endorsement preview of the sum-insured change reports only the current-term adjustment and writes nothing, so the pending offer stays at 6850.00")
    void thePreviewLeavesTheOfferAlone() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");
        renewals.jobOk("2027-10-01");
        at("2027-10-05");

        JsonNode preview = endorsements.previewOk(number, sumInsuredBody("7000000", "2027-10-05"));

        assertThat(preview.get("newAnnualPremium").asText()).isEqualTo("9490.00");
        assertThat(preview.get("adjustment").asText()).as("27 of 365 days of the 2640.00 difference")
                .isEqualTo("195.29");
        assertThat(money(term2(number).get("PREMIUM"))).isEqualTo("6850.00");
    }

    @Test
    @DisplayName("AC-07.7: an endorsement that does not re-rate (CHANGE_ADDRESS) while an offer is pending leaves the offer at 6850.00, and paying it renews at 6850.00")
    void anEndorsementThatDoesNotReRateLeavesTheOffer() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-10-01");
        renewals.jobOk("2027-10-01");
        at("2027-10-05");

        endorsements.endorseOk(number, newAddressBody("2027-10-05"));

        assertThat(money(term2(number).get("PREMIUM"))).isEqualTo("6850.00");
        assertThat(term2(number).get("PAID")).isEqualTo(false);
        at("2027-10-06");
        assertThat(renewals.payOk(number).get("term").get("premium").asText()).isEqualTo("6850.00");
    }

    @Test
    @DisplayName("AC-07.7: a sum-insured change with no offer pending changes no term, so the next offer is rated from the endorsed inputs at 9490.00")
    void withoutAnOfferNoTermChanges() throws Exception {
        String number = issued("TERM_LIFE");
        at("2027-05-01");

        endorsements.endorseOk(number, sumInsuredBody("7000000", "2027-05-01"));

        List<Map<String, Object>> terms = PolicyRows.terms(jdbc, number);
        assertThat(terms).hasSize(1);
        assertThat(money(terms.get(0).get("PREMIUM"))).as("the paid term 1 is never re-priced").isEqualTo("6850.00");
        at("2027-10-01");
        renewals.jobOk("2027-10-01");
        assertThat(money(term2(number).get("PREMIUM"))).isEqualTo("9490.00");
    }
}
