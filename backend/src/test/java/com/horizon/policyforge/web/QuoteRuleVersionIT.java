package com.horizon.policyforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.RuleSetFixtures;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC-01.6 through the API: publishing a new MOTOR rule version changes the next quote with no code change. This test
 * publishes to the catalog, so it gets its own application context and database (DirtiesContext after the method).
 */
@Tag("AC-01")
@SpringBootTest
@AutoConfigureMockMvc
class QuoteRuleVersionIT {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-28");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(TODAY);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private MvcResult quoteMotor() throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("vehicleValue", "800000.00");
        inputs.put("vehicleAgeYears", 2);
        inputs.put("driverAge", 30);
        inputs.put("claimsLast3Years", 0);
        inputs.put("noClaimBonusYears", 2);
        inputs.put("vehicleUse", "PRIVATE");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", "MOTOR");
        body.put("riskInputs", inputs);
        return mvc.perform(post("/api/quotes").contentType("application/json").content(json.writeValueAsString(body)))
                .andReturn();
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    @DisplayName("AC-01.6: after MOTOR v2 (baseRate 0.030, effective on or before today) is published, the AC-01.2 input quotes at 18150.00 with ruleVersion 2, while the earlier v1 quote still reads 15150.00")
    void newRuleVersionPricesTheNextQuote() throws Exception {
        MvcResult before = quoteMotor();
        assertThat(before.getResponse().getStatus()).as(before.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode v1Quote = json.readTree(before.getResponse().getContentAsString());
        assertThat(v1Quote.get("annualPremium").asText()).isEqualTo("15150.00");
        assertThat(v1Quote.get("ruleVersion").asInt()).isEqualTo(1);

        MvcResult published = mvc.perform(post("/api/products/MOTOR/versions")
                        .with(httpBasic("admin1", "password"))
                        .contentType("application/json")
                        .content(RuleSetFixtures.motorV2("2026-10-28")))
                .andReturn();
        assertThat(published.getResponse().getStatus()).as(published.getResponse().getContentAsString())
                .isEqualTo(201);

        MvcResult after = quoteMotor();
        assertThat(after.getResponse().getStatus()).as(after.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode v2Quote = json.readTree(after.getResponse().getContentAsString());
        assertThat(v2Quote.get("annualPremium").asText()).isEqualTo("18150.00");
        assertThat(v2Quote.get("ruleVersion").asInt()).isEqualTo(2);

        JsonNode reread = json.readTree(mvc.perform(get("/api/quotes/" + v1Quote.get("quoteId").asText()))
                .andReturn().getResponse().getContentAsString());
        assertThat(reread.get("annualPremium").asText()).as("a stored quote never changes").isEqualTo("15150.00");
        assertThat(reread.get("ruleVersion").asInt()).isEqualTo(1);
    }
}
