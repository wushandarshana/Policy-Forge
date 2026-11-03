package com.horizon.policyforge.domain.endorsement;

import com.horizon.policyforge.domain.policy.PolicyStatus;
import com.horizon.policyforge.domain.ruleset.SumInsuredLimits;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything {@link EndorsementValidator} needs to know about a policy, loaded by the service so the validator
 * stays pure.
 *
 * @param productCode                   TERM_LIFE, MOTOR or HOUSEHOLD
 * @param status                        the policy's current status
 * @param termStart                     start of the current term (inclusive)
 * @param termEnd                       end of the current term (inclusive)
 * @param lastEndorsementEffectiveDate  effective date of the latest endorsement, or null when there is none
 * @param nominees                      the current version's nominees
 * @param sumInsuredComponents          the current sum insured, keyed by risk-input name: {@code sumInsured}
 *                                      (TERM_LIFE), {@code vehicleValue} (MOTOR), or {@code buildingSumInsured} and
 *                                      {@code contentsSumInsured} (HOUSEHOLD)
 * @param sumInsuredLimits              the policy's own rule version's sum-insured limits
 */
public record PolicySnapshot(
        String productCode,
        PolicyStatus status,
        LocalDate termStart,
        LocalDate termEnd,
        LocalDate lastEndorsementEffectiveDate,
        List<Nominee> nominees,
        Map<String, BigDecimal> sumInsuredComponents,
        SumInsuredLimits sumInsuredLimits) {

    public PolicySnapshot {
        Objects.requireNonNull(productCode, "productCode");
        Objects.requireNonNull(status, "status");
        nominees = List.copyOf(nominees);
        sumInsuredComponents = Map.copyOf(sumInsuredComponents);
    }
}
