package com.horizon.policyforge.domain.application;

import com.horizon.policyforge.domain.underwriting.ReasonCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The manual and override reason codes with their descriptions, and which decision each is offered for (U-10). */
public final class ManualReasonCatalogue {

    private static final ReasonCode MAN_001 = new ReasonCode("UW-MAN-001", "Acceptable after review");
    private static final ReasonCode MAN_002 = new ReasonCode("UW-MAN-002", "Additional evidence satisfactory");
    private static final ReasonCode MAN_010 = new ReasonCode("UW-MAN-010", "Risk outside appetite");
    private static final ReasonCode OVR_001 = new ReasonCode("UW-OVR-001", "Commercial exception approved");
    private static final ReasonCode OVR_002 = new ReasonCode("UW-OVR-002", "Evidence rebuts decline reason");

    private static final List<ReasonCode> APPROVE_CODES = List.of(MAN_001, MAN_002);
    private static final List<ReasonCode> DECLINE_CODES = List.of(MAN_010);
    private static final List<ReasonCode> OVERRIDE_CODES = List.of(OVR_001, OVR_002);

    private ManualReasonCatalogue() {
    }

    /** The codes a decision may carry: APPROVE offers two, DECLINE one. */
    public static List<ReasonCode> forDecision(ManualDecision decision) {
        return switch (decision) {
            case APPROVE -> APPROVE_CODES;
            case DECLINE -> DECLINE_CODES;
        };
    }

    /** The codes valid only on the override endpoint. */
    public static List<ReasonCode> overrideCodes() {
        return OVERRIDE_CODES;
    }

    /** Every code in the catalogue. */
    public static List<ReasonCode> all() {
        List<ReasonCode> all = new ArrayList<>(APPROVE_CODES);
        all.addAll(DECLINE_CODES);
        all.addAll(OVERRIDE_CODES);
        return List.copyOf(all);
    }

    public static Optional<ReasonCode> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return all().stream().filter(reason -> reason.code().equals(code)).findFirst();
    }

    /** Whether the code is one of the manual (UW-MAN) codes, whichever decision it pairs with. */
    static boolean isManualCode(String code) {
        return find(code).filter(reason -> !OVERRIDE_CODES.contains(reason)).isPresent();
    }
}
