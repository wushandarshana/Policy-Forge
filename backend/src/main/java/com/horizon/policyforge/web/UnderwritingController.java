package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.horizon.policyforge.domain.application.ManualDecision;
import com.horizon.policyforge.domain.underwriting.ReasonCode;
import com.horizon.policyforge.service.DecideCommand;
import com.horizon.policyforge.service.DecisionResult;
import com.horizon.policyforge.service.OverrideCommand;
import com.horizon.policyforge.service.QueueEntry;
import com.horizon.policyforge.service.UnderwritingService;
import com.horizon.policyforge.service.ValidationFailedException;
import com.horizon.policyforge.service.ValidationFailedException.FieldViolation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Underwriter workbench endpoints (AC-09): the queue, a manual decision and an admin override. Each handler declares its
 * role with {@code @PreAuthorize}, takes the actor ID from the authenticated principal (never from the body, KD-008),
 * calls one {@link UnderwritingService} method and maps the result to a DTO. No business logic lives here.
 *
 * <p>The request bodies capture, rather than ignore, any key the contract does not define, so the service can reject it
 * by name. They keep the key names only, and their string form is redacted, so a framework debug line cannot print a
 * comment (NFR-03). The queue items likewise print their application ID only, because they carry a full name.
 */
@RestController
@RequestMapping("/api/underwriting")
public class UnderwritingController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    /** The body of {@code POST /api/underwriting/{id}/decision}: {@code {decision, reasonCode, comment}}. */
    public static final class DecisionRequest {
        private String decision;
        private String reasonCode;
        private String comment;
        private final Set<String> unknownKeys = new TreeSet<>();

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public void setReasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "DecisionRequest[redacted]";
        }
    }

    /** The body of {@code POST /api/underwriting/{id}/override}: {@code {reasonCode, comment}}. */
    public static final class OverrideRequest {
        private String reasonCode;
        private String comment;
        private final Set<String> unknownKeys = new TreeSet<>();

        public void setReasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "OverrideRequest[redacted]";
        }
    }

    /** The KYC part of a queue item (U-7): the full name and the masked Aadhaar and PAN, nothing else. */
    public record KycView(String fullName, String aadhaar, String pan) {

        /** Nothing printed: the name is personal data (NFR-03). */
        @Override
        public String toString() {
            return "KycView[redacted]";
        }
    }

    /** Contract C4: one case in the queue, built from an allow-list (U-7, KD-008). */
    public record QueueItemResponse(
            String applicationId,
            String quoteId,
            String productCode,
            String status,
            Instant submittedAt,
            int ruleVersion,
            String decision,
            List<String> reasonCodes,
            List<ApplicationController.ReasonItem> reasons,
            KycView kyc,
            Map<String, Object> riskInputs) {

        static QueueItemResponse from(QueueEntry entry) {
            return new QueueItemResponse(
                    entry.applicationId(),
                    entry.quoteId(),
                    entry.productCode(),
                    entry.status().name(),
                    entry.submittedAt(),
                    entry.ruleVersion(),
                    entry.decision().name(),
                    entry.reasons().stream().map(ReasonCode::code).toList(),
                    entry.reasons().stream().map(ApplicationController.ReasonItem::from).toList(),
                    new KycView(entry.fullName(), entry.maskedAadhaar(), entry.maskedPan()),
                    entry.riskInputs());
        }

        /** The application ID only: the name and the risk inputs are personal data (NFR-03). */
        @Override
        public String toString() {
            return "QueueItemResponse[applicationId=" + applicationId + "]";
        }
    }

    /** Contract C5: the application and its status before and after the decision. */
    public record DecisionResponse(String applicationId, String previousStatus, String status) {

        static DecisionResponse from(DecisionResult result) {
            return new DecisionResponse(result.applicationId(), result.previousStatus().name(),
                    result.status().name());
        }
    }

    private final UnderwritingService underwriting;

    public UnderwritingController(UnderwritingService underwriting) {
        this.underwriting = underwriting;
    }

    /**
     * UNDERWRITER (and so ADMIN): the REFERRED applications, oldest first. {@code includeDeclined=true} adds the DECLINED
     * ones and is for ADMIN only: anyone else gets 403, never a silently plain queue (U-11). A value that is neither
     * {@code true} nor {@code false} is a 400, checked before the role.
     */
    @GetMapping("/queue")
    @PreAuthorize("hasRole('UNDERWRITER')")
    public List<QueueItemResponse> queue(
            @RequestParam(name = "includeDeclined", required = false) String includeDeclined,
            Authentication authentication) {
        boolean withDeclined = parseIncludeDeclined(includeDeclined);
        if (withDeclined && !isAdmin(authentication)) {
            throw new AccessDeniedException("includeDeclined needs ADMIN");
        }
        return underwriting.queue(withDeclined).stream().map(QueueItemResponse::from).toList();
    }

    /** UNDERWRITER only. JSON only (NFR-04.3). Bodies over 16 KB are refused before this point with 413 (NFR-04.4). */
    @PostMapping(path = "/{applicationId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('UNDERWRITER')")
    public DecisionResponse decide(@PathVariable("applicationId") String applicationId,
            @RequestBody DecisionRequest request, Authentication authentication) {
        return DecisionResponse.from(underwriting.decide(new DecideCommand(authentication.getName(), applicationId,
                parseDecision(request.decision), request.reasonCode, request.comment,
                List.copyOf(request.unknownKeys))));
    }

    /** ADMIN only. JSON only (NFR-04.3). Bodies over 16 KB are refused before this point with 413 (NFR-04.4). */
    @PostMapping(path = "/{applicationId}/override", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public DecisionResponse override(@PathVariable("applicationId") String applicationId,
            @RequestBody OverrideRequest request, Authentication authentication) {
        return DecisionResponse.from(underwriting.override(new OverrideCommand(authentication.getName(),
                applicationId, request.reasonCode, request.comment, List.copyOf(request.unknownKeys))));
    }

    /** Only {@code true} and {@code false} (or no parameter, meaning false) are accepted; the value is never echoed. */
    private static boolean parseIncludeDeclined(String raw) {
        if (raw == null || "false".equalsIgnoreCase(raw)) {
            return false;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        throw new ValidationFailedException(List.of(new FieldViolation("includeDeclined", "must be true or false")));
    }

    /** An unrecognised or missing decision becomes null, which the domain reports as a 400 naming {@code decision}. */
    private static ManualDecision parseDecision(String raw) {
        for (ManualDecision decision : ManualDecision.values()) {
            if (decision.name().equals(raw)) {
                return decision;
            }
        }
        return null;
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }
}
