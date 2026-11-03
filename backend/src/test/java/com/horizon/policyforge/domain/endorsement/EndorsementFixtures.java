package com.horizon.policyforge.domain.endorsement;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.ruleset.SumInsuredLimits;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Policy snapshots from the endorsement-validator skill: a TERM_LIFE policy, a MOTOR policy and a HOUSEHOLD policy. */
final class EndorsementFixtures {

    static final LocalDate TERM_START = LocalDate.of(2026, 11, 1);
    static final LocalDate TERM_END = LocalDate.of(2027, 10, 31);
    /** Inside the term of every fixture. */
    static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    static final SumInsuredLimits TERM_LIFE_LIMITS = limits("500000.00", "50000000.00");
    static final SumInsuredLimits MOTOR_LIMITS = limits("100000.00", "10000000.00");
    static final SumInsuredLimits HOUSEHOLD_LIMITS = limits("200000.00", "50000000.00");

    private EndorsementFixtures() {
    }

    static SumInsuredLimits limits(String min, String max) {
        return new SumInsuredLimits(new BigDecimal(min), new BigDecimal(max));
    }

    static Nominee nominee(String relationship, int share) {
        return new Nominee("Ravi Nominee", relationship, share);
    }

    /** TERM_LIFE, ACTIVE, 2026-11-01..2027-10-31, SI 5,000,000.00, one nominee at 60%, no endorsement yet. */
    static PolicySnapshot termLife() {
        return termLife(PolicyStatus.ACTIVE, null, List.of(nominee("SPOUSE", 60)));
    }

    static PolicySnapshot termLife(PolicyStatus status, LocalDate lastEndorsement, List<Nominee> nominees) {
        return new PolicySnapshot("TERM_LIFE", status, TERM_START, TERM_END, lastEndorsement, nominees,
                Map.of("sumInsured", new BigDecimal("5000000.00")), TERM_LIFE_LIMITS);
    }

    /** MOTOR, ACTIVE, same term, vehicleValue 800,000.00. */
    static PolicySnapshot motor() {
        return new PolicySnapshot("MOTOR", PolicyStatus.ACTIVE, TERM_START, TERM_END, null, List.of(),
                Map.of("vehicleValue", new BigDecimal("800000.00")), MOTOR_LIMITS);
    }

    /** HOUSEHOLD, ACTIVE, same term, building 6,000,000.00 and contents 1,000,000.00. */
    static PolicySnapshot household() {
        return new PolicySnapshot("HOUSEHOLD", PolicyStatus.ACTIVE, TERM_START, TERM_END, null, List.of(),
                Map.of("buildingSumInsured", new BigDecimal("6000000.00"),
                        "contentsSumInsured", new BigDecimal("1000000.00")),
                HOUSEHOLD_LIMITS);
    }

    static Address address(String postcode) {
        return new Address("12 Test Lane", "Pune", postcode);
    }

    static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
