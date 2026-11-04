package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.DashboardApi;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The dashboard's {@code asOf} bound (AC-11.6, security review MINOR-1): a date outside 2000-01-01..2100-12-31, or a
 * value that is not a date, is a 400 problem naming {@code asOf} with a fixed detail, never a 500.
 */
@Tag("AC-11")
@Tag("AC-11.6")
class PortfolioAsOfBoundIT extends DashboardTestSupport {

    private MvcResult asAdmin(String asOf) throws Exception {
        return mvc.perform(get(DashboardApi.PATH).param("asOf", asOf)
                .with(httpBasic("admin1", ApplicationApi.PASSWORD))).andReturn();
    }

    @Test
    @DisplayName("AC-11.6: asOf +999999999-12-31, -999999999-01-01, 1999-12-31, 2101-01-01 and a non-date are each a 400 problem+json naming asOf with one fixed detail that never echoes the value, never a 500")
    void anAsOfOutsideTheRangeIsA400() throws Exception {
        Set<String> details = new HashSet<>();
        for (String asOf : List.of("+999999999-12-31", "-999999999-01-01", "1999-12-31", "2101-01-01",
                "SYNTHETIC_NOT_A_DATE")) {
            MvcResult result = asAdmin(asOf);

            assertThat(result.getResponse().getStatus()).as(asOf).isEqualTo(400);
            assertThat(result.getResponse().getContentType()).as(asOf).startsWith("application/problem+json");
            JsonNode problem = dashboards.bodyOf(result);
            assertThat(errorFields(problem)).as(asOf).containsExactly("asOf");
            assertThat(result.getResponse().getContentAsString()).as(asOf).doesNotContain(asOf);
            details.add(problem.get("detail").asText());
        }
        assertThat(details).as("one fixed detail").hasSize(1);
    }

    @Test
    @DisplayName("AC-11.6: the range ends 2000-01-01 and 2100-12-31 are accepted and echoed")
    void theRangeEndsAreAccepted() throws Exception {
        for (String asOf : List.of("2000-01-01", "2100-12-31")) {
            MvcResult result = asAdmin(asOf);

            assertThat(result.getResponse().getStatus()).as(asOf).isEqualTo(200);
            assertThat(dashboards.bodyOf(result).get("asOf").asText()).isEqualTo(asOf);
        }
    }
}
