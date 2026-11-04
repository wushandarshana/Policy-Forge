package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.CancellationApi;
import com.horizon.policyforge.support.DashboardApi;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Premium collected, exact to the cent (AC-11.2, P-11). Every figure is the independent oracle's (premium_check.py):
 * two TERM_LIFE terms of 6850.00, an endorsement of +1330.85 (5,000,000.00 to 7,000,000.00 effective 2027-05-01) and a
 * refund of 4473.29 (cancelled 2027-02-09) move TERM_LIFE by exactly 10557.56. The seed is in the context (P-15), so
 * each test reads the dashboard before and after and compares the exact difference, as strings and as decimals.
 */
@Tag("AC-11")
class PortfolioPremiumIT extends DashboardTestSupport {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoves(JsonNode before, JsonNode after, String product, String expectedDifference) {
        BigDecimal difference = DashboardApi.premium(after, product).subtract(DashboardApi.premium(before, product));
        assertThat(difference).as(product).isEqualTo(d(expectedDifference));
        assertThat(after.get("premiumCollected").get(product).asText()).as("scale 2, no exponent")
                .matches("-?\\d+\\.\\d{2}");
    }

    private static void assertTotalIsTheSum(JsonNode dashboard) {
        BigDecimal sum = DashboardApi.PRODUCTS.stream().map(product -> DashboardApi.premium(dashboard, product))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(DashboardApi.premium(dashboard, "total")).isEqualTo(sum);
        assertThat(dashboard.get("premiumCollected").get("total").asText()).isEqualTo(sum.toPlainString());
    }

    @Test
    @DisplayName("AC-11.2: two paid TERM_LIFE terms (6850.00 each), an endorsement of +1330.85 and a refund of 4473.29 move TERM_LIFE by exactly 10557.56 with exact decimal strings, nothing else moves, and the total is the exact sum of the three products")
    void theWorkedExampleMovesTermLifeByExactly10557point56() throws Exception {
        JsonNode before = dashboards.ok(null);

        String endorsed = issued("TERM_LIFE");
        String cancelled = issued("TERM_LIFE");
        JsonNode afterIssue = dashboards.ok(null);
        assertMoves(before, afterIssue, "TERM_LIFE", "13700.00");

        JsonNode endorsement = endorsements.endorseOk(endorsed, sumInsuredBody("7000000.00", "2027-05-01"));
        assertThat(endorsement.get("endorsement").get("adjustment").asText()).isEqualTo("1330.85");
        JsonNode afterEndorsement = dashboards.ok(null);
        assertMoves(afterIssue, afterEndorsement, "TERM_LIFE", "1330.85");

        JsonNode refund = new CancellationApi(mvc, json).cancelOk(cancelled, "2027-02-09");
        assertThat(refund.get("refund").get("amount").asText()).isEqualTo("4473.29");
        JsonNode after = dashboards.ok(null);

        assertMoves(afterEndorsement, after, "TERM_LIFE", "-4473.29");
        assertMoves(before, after, "TERM_LIFE", "10557.56");
        assertMoves(before, after, "MOTOR", "0.00");
        assertMoves(before, after, "HOUSEHOLD", "0.00");
        assertMoves(before, after, "total", "10557.56");
        assertTotalIsTheSum(after);
    }

    @Test
    @DisplayName("AC-11.2: a negative adjustment (a credit of -1330.85) lowers the product's figure by exactly that amount, and the total follows")
    void aCreditLowersTheFigure() throws Exception {
        String number = issued("TERM_LIFE");
        endorsements.endorseOk(number, sumInsuredBody("7000000.00", "2027-05-01"));
        JsonNode before = dashboards.ok(null);

        JsonNode credit = endorsements.endorseOk(number, sumInsuredBody("5000000.00", "2027-05-01"));
        assertThat(credit.get("endorsement").get("adjustment").asText()).isEqualTo("-1330.85");
        JsonNode after = dashboards.ok(null);

        assertMoves(before, after, "TERM_LIFE", "-1330.85");
        assertThat(DashboardApi.premium(after, "TERM_LIFE")).isLessThan(DashboardApi.premium(before, "TERM_LIFE"));
        assertMoves(before, after, "total", "-1330.85");
        assertTotalIsTheSum(after);
    }

    @Test
    @DisplayName("AC-11.2: a MOTOR policy adds exactly 15150.00 and a HOUSEHOLD policy exactly 11350.00 to their own product, and the total moves by exactly 26500.00")
    void everyProductCollectsItsOwnPaidTerm() throws Exception {
        JsonNode before = dashboards.ok(null);

        issued("MOTOR");
        issued("HOUSEHOLD");
        JsonNode after = dashboards.ok(null);

        assertMoves(before, after, "MOTOR", "15150.00");
        assertMoves(before, after, "HOUSEHOLD", "11350.00");
        assertMoves(before, after, "TERM_LIFE", "0.00");
        assertMoves(before, after, "total", "26500.00");
        assertTotalIsTheSum(after);
    }

    @Test
    @DisplayName("AC-11.2: an unpaid renewal offer collects nothing, and paying it adds exactly its premium of 6850.00 (a second paid term)")
    void onlyAPaidRenewalTermCounts() throws Exception {
        String number = issued("TERM_LIFE");
        JsonNode issuedFigures = dashboards.ok(null);

        renewals.jobOk("2027-10-01");
        JsonNode offered = dashboards.ok(null);
        assertMoves(issuedFigures, offered, "TERM_LIFE", "0.00");

        at("2027-10-05");
        renewals.payOk(number);
        JsonNode paid = dashboards.ok(null);

        assertMoves(offered, paid, "TERM_LIFE", "6850.00");
        assertMoves(issuedFigures, paid, "total", "6850.00");
        assertTotalIsTheSum(paid);
    }
}
