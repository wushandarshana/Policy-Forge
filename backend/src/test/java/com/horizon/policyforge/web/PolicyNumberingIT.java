package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationFixtures.householdInputs;
import static com.horizon.policyforge.support.ApplicationFixtures.motorInputs;
import static com.horizon.policyforge.support.PolicyApi.issueBody;
import static com.horizon.policyforge.support.PolicyApi.sequenceOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.support.ApplicationApi;
import com.horizon.policyforge.support.ApplicationRows;
import com.horizon.policyforge.support.FixedClockConfig;
import com.horizon.policyforge.support.MutableClock;
import com.horizon.policyforge.support.PolicyApi;
import com.horizon.policyforge.support.PolicyRows;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
 * Policy numbering and the race on one application (AC-05.2, AC-05.3). The sequence is per product and per issue year,
 * so each test issues in a clock year no other test uses and can assert the exact numbers it gets: a gap would show.
 * The years: 2030 concurrent, 2031 one after another, 2032 two products alternating, 2033 the first issue of a year,
 * 2035 the same-application race, 2036 the issue year against the effective date's year, 2040 and 2041 the exhausted
 * sequence.
 */
@Tag("AC-05")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class PolicyNumberingIT {

    private static final int THREADS = 8;

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
        clock.setDate(FixedClockConfig.TODAY);
        api = new ApplicationApi(mvc, json);
        policies = new PolicyApi(mvc, json);
    }

    @AfterEach
    void resetClock() {
        clock.setDate(FixedClockConfig.TODAY);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private String approvedTermLife() throws Exception {
        String applicationId = api.approvedApplication("customer1", today());
        assertThat(ApplicationRows.status(jdbc, applicationId)).isEqualTo("APPROVED");
        return applicationId;
    }

    private String approvedMotor() throws Exception {
        String applicationId = api.applyAs("customer1", "MOTOR", motorInputs(30, 0), 30, today());
        assertThat(ApplicationRows.status(jdbc, applicationId)).isEqualTo("APPROVED");
        return applicationId;
    }

    private String issuedNumber(String applicationId) throws Exception {
        return policies.issueOk("customer1", applicationId, null).get("policyNumber").asText();
    }

    private static void assertContiguousFrom(List<Integer> sequences, int first) {
        assertThat(sequences).containsExactlyElementsOf(IntStream.range(first, first + sequences.size()).boxed().toList());
    }

    @Test
    @DisplayName("AC-05.2: 50 APPROVED applications issued at once on a pool of 8 threads all get 201 and 50 distinct policy numbers, with no gap in the sequence")
    void fiftyConcurrentIssuesGetFiftyDistinctNumbers() throws Exception {
        clock.setDate(LocalDate.parse("2030-03-01"));
        List<String> applicationIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            applicationIds.add(approvedTermLife());
        }

        List<MvcResult> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (String applicationId : applicationIds) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return policies.issue("customer1", applicationId, issueBody(null));
                }));
            }
            start.countDown();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        List<String> numbers = new ArrayList<>();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
            numbers.add(policies.bodyOf(result).get("policyNumber").asText());
        }
        assertThat(new HashSet<>(numbers)).as("50 distinct policy numbers").hasSize(50);
        assertThat(numbers).allMatch(number -> number.matches("^PF-TL-2030-\\d{6}$"));
        assertThat(numbers.stream().map(PolicyApi::sequenceOf).sorted().toList())
                .as("the first issue of the year gets 1 and the 50 numbers are contiguous")
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 50).boxed().toList());
        assertThat(PolicyRows.policies(jdbc)).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("AC-05.2: 5 applications issued one after another get contiguous sequence numbers n to n+4")
    void fiveSequentialIssuesAreContiguous() throws Exception {
        clock.setDate(LocalDate.parse("2031-03-01"));
        List<String> applicationIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            applicationIds.add(approvedTermLife());
        }

        List<Integer> sequences = new ArrayList<>();
        for (String applicationId : applicationIds) {
            String number = issuedNumber(applicationId);
            assertThat(number).matches("^PF-TL-2031-\\d{6}$");
            sequences.add(sequenceOf(number));
        }

        assertContiguousFrom(sequences, sequences.get(0));
        assertThat(sequences.get(sequences.size() - 1)).isEqualTo(sequences.get(0) + 4);
    }

    @Test
    @DisplayName("AC-05.2: TERM_LIFE and MOTOR issued alternately each stay contiguous within their own product")
    void sequencesAreContiguousWithinEachProduct() throws Exception {
        clock.setDate(LocalDate.parse("2032-03-01"));
        List<String> termLife = List.of(approvedTermLife(), approvedTermLife(), approvedTermLife());
        List<String> motor = List.of(approvedMotor(), approvedMotor(), approvedMotor());

        List<Integer> termLifeSequences = new ArrayList<>();
        List<Integer> motorSequences = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String termLifeNumber = issuedNumber(termLife.get(i));
            String motorNumber = issuedNumber(motor.get(i));
            assertThat(termLifeNumber).matches("^PF-TL-2032-\\d{6}$");
            assertThat(motorNumber).matches("^PF-MO-2032-\\d{6}$");
            termLifeSequences.add(sequenceOf(termLifeNumber));
            motorSequences.add(sequenceOf(motorNumber));
        }

        assertContiguousFrom(termLifeSequences, 1);
        assertContiguousFrom(motorSequences, 1);
    }

    @Test
    @DisplayName("AC-05.2: the first issue in a clock year that no other test uses gets sequence 000001, and each product starts its own sequence")
    void firstIssueOfAYearGetsSequenceOne() throws Exception {
        clock.setDate(LocalDate.parse("2033-03-01"));
        String termLife = approvedTermLife();
        String household = api.applyAs("customer1", "HOUSEHOLD", householdInputs("BRICK"), 50, today());

        assertThat(issuedNumber(termLife)).isEqualTo("PF-TL-2033-000001");
        assertThat(issuedNumber(household)).isEqualTo("PF-HH-2033-000001");
        assertThat(issuedNumber(approvedTermLife())).isEqualTo("PF-TL-2033-000002");
    }

    @Test
    @DisplayName("AC-05.2: when the counter of the product and year is at 999999 the issue is a 503 POLICY_NUMBER_SEQUENCE_EXHAUSTED with a fixed detail, the application stays APPROVED, no policy, version, term, transition or audit row is written and the counter is unchanged, while another product and another year still issue")
    void anExhaustedSequenceIsA503AndWritesNothing() throws Exception {
        clock.setDate(LocalDate.parse("2040-03-01"));
        String exhausted = approvedTermLife();
        String motor = approvedMotor();
        // The counter table is not append-only: one statement puts the TERM_LIFE counter of this unused year at its end.
        jdbc.update("insert into policy_number_sequence (product_code, seq_year, last_value) values (?, ?, ?)",
                "TERM_LIFE", 2040, 999_999);
        PolicyRows.Snapshot before = PolicyRows.snapshot(jdbc, exhausted);

        for (int attempt = 1; attempt <= 2; attempt++) {
            MvcResult result = policies.issue("customer1", exhausted, issueBody(null));

            assertThat(result.getResponse().getStatus()).as("attempt " + attempt + ": "
                    + result.getResponse().getContentAsString()).isEqualTo(503);
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
            JsonNode problem = policies.bodyOf(result);
            assertThat(problem.get("code").asText()).isEqualTo("POLICY_NUMBER_SEQUENCE_EXHAUSTED");
            assertThat(problem.get("detail").asText())
                    .isEqualTo("No further policy numbers can be issued for this product at the moment.");
            assertThat(problem.get("instance").asText())
                    .isEqualTo("urn:policyforge:problem:policy-number-sequence-exhausted");
            assertThat(result.getResponse().getContentAsString()).as("no SQL, constraint or counter text")
                    .doesNotContainIgnoringCase("sql").doesNotContainIgnoringCase("constraint")
                    .doesNotContain("999999").doesNotContain("policy_number_sequence")
                    .doesNotContain(exhausted);
            assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull();
            assertThat(ApplicationRows.status(jdbc, exhausted)).as("still APPROVED").isEqualTo("APPROVED");
            assertThat(PolicyRows.policiesForApplication(jdbc, exhausted)).isZero();
            assertThat(PolicyRows.snapshot(jdbc, exhausted)).as("no policy, version, term, transition or audit row")
                    .isEqualTo(before);
            assertThat(jdbc.queryForObject("select last_value from policy_number_sequence"
                    + " where product_code = 'TERM_LIFE' and seq_year = 2040", Integer.class))
                    .as("the counter is unchanged").isEqualTo(999_999);
        }

        assertThat(issuedNumber(motor)).as("another product still issues").isEqualTo("PF-MO-2040-000001");
        clock.setDate(LocalDate.parse("2041-03-01"));
        assertThat(issuedNumber(approvedTermLife())).as("another year still issues").isEqualTo("PF-TL-2041-000001");
    }

    @Test
    @DisplayName("AC-05.1: the year in the policy number is the issue date's year, not the effective date's year")
    void numberYearIsTheIssueYear() throws Exception {
        clock.setDate(LocalDate.parse("2036-12-20"));
        String applicationId = approvedTermLife();

        JsonNode policy = policies.issueOk("customer1", applicationId, "2037-01-05");

        assertThat(policy.get("policyNumber").asText()).isEqualTo("PF-TL-2036-000001");
        assertThat(policy.get("effectiveDate").asText()).isEqualTo("2037-01-05");
        assertThat(policy.get("expiryDate").asText()).isEqualTo("2038-01-04");
    }

    @Test
    @DisplayName("AC-05.3: two concurrent issues of the same application give exactly one 201 and one 409, one policy, and no number is lost to the loser")
    void concurrentIssuesOfOneApplicationGiveOneWinner() throws Exception {
        clock.setDate(LocalDate.parse("2035-03-01"));
        int rounds = 5;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                String applicationId = approvedTermLife();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return policies.issue("customer1", applicationId, issueBody(null));
                    }));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<MvcResult> future : futures) {
                    MvcResult result = future.get(60, TimeUnit.SECONDS);
                    statuses.add(result.getResponse().getStatus());
                    if (result.getResponse().getStatus() == 409) {
                        assertThat(policies.bodyOf(result).get("code").asText()).isEqualTo("INVALID_APPLICATION_STATE");
                    }
                }

                assertThat(statuses).as("round " + round).containsExactlyInAnyOrder(201, 409);
                assertThat(PolicyRows.policiesForApplication(jdbc, applicationId)).isEqualTo(1);
                assertThat(ApplicationRows.status(jdbc, applicationId)).isEqualTo("ISSUED");
            }
        } finally {
            pool.shutdownNow();
        }

        List<Integer> sequences = jdbc.queryForList(
                "select policy_number from policy where policy_number like 'PF-TL-2035-%' order by policy_number",
                String.class).stream().map(PolicyApi::sequenceOf).toList();
        assertContiguousFrom(sequences, 1);
        assertThat(sequences).as("one number per winner, none used up by a loser").hasSize(rounds);
        Set<String> distinct = new HashSet<>(jdbc.queryForList(
                "select policy_number from policy where policy_number like 'PF-TL-2035-%'", String.class));
        assertThat(distinct).hasSize(rounds);
    }
}
