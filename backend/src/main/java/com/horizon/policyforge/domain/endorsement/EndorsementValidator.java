package com.horizon.policyforge.domain.endorsement;

import com.horizon.policyforge.domain.Money;
import com.horizon.policyforge.domain.policy.InvalidPolicyStateException;
import com.horizon.policyforge.domain.policy.PolicyStateMachine;
import com.horizon.policyforge.domain.policy.PolicyStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The endorsement rule matrix E1 to E11 as a pure function over a {@link PolicySnapshot} (specs/endorsement_spec.md).
 *
 * <ul>
 *   <li>E1: a policy that is not in force raises {@link InvalidPolicyStateException} (through
 *       {@link PolicyStateMachine}, to ENDORSED) and short-circuits.</li>
 *   <li>E2 to E10: every violation is collected, in E-order, each naming its dotted request field. Messages are fixed
 *       text and never contain the submitted value.</li>
 *   <li>E11: a valid request returns a normalised {@link EndorsementCommand}.</li>
 * </ul>
 *
 * <p>The request's shape (required keys, types, sizes) is checked by the web layer first (P-5). A request that
 * lacks the payload of its type, or a sum-insured value the product needs, is a programming error and raises
 * {@link IllegalArgumentException}.
 */
public final class EndorsementValidator {

    public static final String EFFECTIVE_DATE_OUT_OF_TERM = "EFFECTIVE_DATE_OUT_OF_TERM";
    public static final String EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT = "EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT";
    public static final String NOMINEE_NOT_SUPPORTED = "NOMINEE_NOT_SUPPORTED";
    public static final String NOMINEE_LIMIT_REACHED = "NOMINEE_LIMIT_REACHED";
    public static final String NOMINEE_SHARE_EXCEEDED = "NOMINEE_SHARE_EXCEEDED";
    public static final String NOMINEE_RELATIONSHIP_INVALID = "NOMINEE_RELATIONSHIP_INVALID";
    public static final String SUM_INSURED_OUT_OF_RANGE = "SUM_INSURED_OUT_OF_RANGE";
    public static final String SUM_INSURED_UNCHANGED = "SUM_INSURED_UNCHANGED";
    public static final String POSTCODE_INVALID = "POSTCODE_INVALID";

    /** The most nominees a policy can have. */
    public static final int MAX_NOMINEES = 3;

    private static final int MAX_TOTAL_SHARE = 100;
    private static final int POSTCODE_LENGTH = 6;
    private static final Set<String> RELATIONSHIPS = Set.of("SPOUSE", "CHILD", "PARENT", "SIBLING", "OTHER");

    private static final String SUM_INSURED = "sumInsured";
    private static final String VEHICLE_VALUE = "vehicleValue";
    private static final String BUILDING = "buildingSumInsured";
    private static final String CONTENTS = "contentsSumInsured";
    private static final String FIELD_NEW_SUM_INSURED = "newSumInsured";
    private static final String FIELD_NEW_BUILDING = "newBuildingSumInsured";
    private static final String FIELD_NEW_CONTENTS = "newContentsSumInsured";

    private final PolicyStateMachine stateMachine;

    public EndorsementValidator() {
        this(new PolicyStateMachine());
    }

    public EndorsementValidator(PolicyStateMachine stateMachine) {
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
    }

    /**
     * Validates {@code request} against {@code policy}. A missing effective date defaults to {@code today}.
     *
     * @throws InvalidPolicyStateException when the policy is not in force (E1)
     */
    public EndorsementValidation validate(PolicySnapshot policy, EndorsementRequest request, LocalDate today) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(today, "today");
        stateMachine.transition(policy.status(), PolicyStatus.ENDORSED);

        LocalDate effective = request.effectiveDate() == null ? today : request.effectiveDate();
        List<EndorsementViolation> violations = new ArrayList<>();
        checkEffectiveDate(policy, effective, violations);

        EndorsementCommand command = switch (request.type()) {
            case CHANGE_ADDRESS -> changeAddress(request, effective, violations);
            case ADD_NOMINEE -> addNominee(policy, request, effective, violations);
            case CHANGE_SUM_INSURED -> changeSumInsured(policy, request, effective, violations);
        };
        return violations.isEmpty()
                ? new EndorsementValidation(command, List.of())
                : new EndorsementValidation(null, violations);
    }

    private static void checkEffectiveDate(PolicySnapshot policy, LocalDate effective, List<EndorsementViolation> out) {
        if (effective.isBefore(policy.termStart()) || effective.isAfter(policy.termEnd())) {
            out.add(new EndorsementViolation("effectiveDate", EFFECTIVE_DATE_OUT_OF_TERM,
                    "Effective date must fall within the current policy term"));
        }
        LocalDate last = policy.lastEndorsementEffectiveDate();
        if (last != null && effective.isBefore(last)) {
            out.add(new EndorsementViolation("effectiveDate", EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT,
                    "Effective date must not be before the latest endorsement's effective date"));
        }
    }

    // ---- CHANGE_ADDRESS (E10) ----

    private static EndorsementCommand changeAddress(
            EndorsementRequest request, LocalDate effective, List<EndorsementViolation> out) {
        Address address = require(request.address(), "address");
        if (!isSixDigits(address.postcode())) {
            out.add(new EndorsementViolation("address.postcode", POSTCODE_INVALID, "Postcode must be exactly 6 digits"));
        }
        Address normalised = new Address(trim(address.line1()), trim(address.city()), trim(address.postcode()));
        return new EndorsementCommand(request.type(), effective, normalised, null, Map.of());
    }

    private static boolean isSixDigits(String postcode) {
        if (postcode == null) {
            return false;
        }
        String value = postcode.trim();
        if (value.length() != POSTCODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    // ---- ADD_NOMINEE (E4 to E7) ----

    private static EndorsementCommand addNominee(
            PolicySnapshot policy, EndorsementRequest request, LocalDate effective, List<EndorsementViolation> out) {
        Nominee nominee = require(request.nominee(), "nominee");
        if (!"TERM_LIFE".equals(policy.productCode())) {
            out.add(new EndorsementViolation("nominee", NOMINEE_NOT_SUPPORTED,
                    "Nominees are supported on term life policies only"));
        }
        if (policy.nominees().size() >= MAX_NOMINEES) {
            out.add(new EndorsementViolation("nominee", NOMINEE_LIMIT_REACHED,
                    "A policy can have at most 3 nominees"));
        }
        int total = policy.nominees().stream().mapToInt(Nominee::sharePercent).sum();
        if (total + nominee.sharePercent() > MAX_TOTAL_SHARE) {
            out.add(new EndorsementViolation("nominee.sharePercent", NOMINEE_SHARE_EXCEEDED,
                    "Nominee shares must not total more than 100 percent"));
        }
        if (nominee.relationship() == null || !RELATIONSHIPS.contains(nominee.relationship())) {
            out.add(new EndorsementViolation("nominee.relationship", NOMINEE_RELATIONSHIP_INVALID,
                    "Relationship must be SPOUSE, CHILD, PARENT, SIBLING or OTHER"));
        }
        Nominee normalised = new Nominee(trim(nominee.name()), nominee.relationship(), nominee.sharePercent());
        return new EndorsementCommand(request.type(), effective, null, normalised, Map.of());
    }

    // ---- CHANGE_SUM_INSURED (E8, E9) ----

    private static EndorsementCommand changeSumInsured(
            PolicySnapshot policy, EndorsementRequest request, LocalDate effective, List<EndorsementViolation> out) {
        Map<String, BigDecimal> supplied = new LinkedHashMap<>();
        Map<String, String> fields = new LinkedHashMap<>();
        switch (policy.productCode()) {
            case "TERM_LIFE" -> supply(supplied, fields, SUM_INSURED, FIELD_NEW_SUM_INSURED,
                    require(request.newSumInsured(), FIELD_NEW_SUM_INSURED));
            case "MOTOR" -> supply(supplied, fields, VEHICLE_VALUE, FIELD_NEW_SUM_INSURED,
                    require(request.newSumInsured(), FIELD_NEW_SUM_INSURED));
            case "HOUSEHOLD" -> {
                if (request.newBuildingSumInsured() == null && request.newContentsSumInsured() == null) {
                    throw new IllegalArgumentException("A household sum-insured change needs at least one component");
                }
                if (request.newBuildingSumInsured() != null) {
                    supply(supplied, fields, BUILDING, FIELD_NEW_BUILDING, request.newBuildingSumInsured());
                }
                if (request.newContentsSumInsured() != null) {
                    supply(supplied, fields, CONTENTS, FIELD_NEW_CONTENTS, request.newContentsSumInsured());
                }
            }
            default -> throw new IllegalArgumentException("Unknown product for a sum-insured change");
        }

        String firstField = fields.values().iterator().next();
        BigDecimal newTotal = BigDecimal.ZERO;
        boolean unchanged = true;
        for (String key : policy.sumInsuredComponents().keySet()) {
            BigDecimal current = policy.sumInsuredComponents().get(key);
            BigDecimal updated = supplied.getOrDefault(key, current);
            newTotal = newTotal.add(updated);
            if (supplied.containsKey(key) && updated.compareTo(current) != 0) {
                unchanged = false;
            }
        }
        for (String key : supplied.keySet()) {
            if (!policy.sumInsuredComponents().containsKey(key)) {
                throw new IllegalArgumentException("The policy has no current value for " + key);
            }
        }

        if (outOfRange(newTotal, policy)) {
            out.add(new EndorsementViolation(firstField, SUM_INSURED_OUT_OF_RANGE,
                    "New sum insured must be within the limits of the policy's rule version"));
        }
        if (unchanged) {
            out.add(new EndorsementViolation(firstField, SUM_INSURED_UNCHANGED,
                    "New sum insured must differ from the current sum insured"));
        }
        return new EndorsementCommand(request.type(), effective, null, null, supplied);
    }

    private static void supply(Map<String, BigDecimal> supplied, Map<String, String> fields,
            String key, String field, BigDecimal value) {
        supplied.put(key, Money.finalAmount(value));
        fields.put(key, field);
    }

    private static boolean outOfRange(BigDecimal total, PolicySnapshot policy) {
        return total.compareTo(policy.sumInsuredLimits().min()) < 0
                || total.compareTo(policy.sumInsuredLimits().max()) > 0;
    }

    // ---- helpers ----

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("The request has no " + name);
        }
        return value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
