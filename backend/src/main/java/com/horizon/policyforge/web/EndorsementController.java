package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.horizon.policyforge.domain.endorsement.EndorsementType;
import com.horizon.policyforge.service.EndorseCommand;
import com.horizon.policyforge.service.EndorsementPreview;
import com.horizon.policyforge.service.EndorsementResult;
import com.horizon.policyforge.service.EndorsementService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endorsements (AC-06): apply a mid-term change to the caller's own in-force policy (C4) and preview it (C5, plan G-1).
 * Both are CUSTOMER only and take {@code application/json} only (NFR-04.3, KD-007). Each handler declares its role with
 * {@code @PreAuthorize}, takes the actor from the authenticated principal and calls one {@link EndorsementService}
 * method; every check and rule lives behind it. There is deliberately no PUT, PATCH or DELETE: an endorsement is never
 * changed or removed (AC-06.7). Responses are built from allow-lists (KD-008).
 *
 * <p>The body is captured as a raw object, so the service can reject every unknown key by name (KD-008). Its string form
 * and those of the responses carry no address and no nominee, so Spring's DEBUG body logging cannot print them (NFR-03).
 */
@RestController
@RequestMapping("/api")
public class EndorsementController {

    /** The body of an endorsement or preview request: every key it carries, unread. It holds personal data. */
    public static final class EndorsementRequestBody {
        private final Map<String, Object> fields = new LinkedHashMap<>();

        @JsonAnySetter
        public void field(String key, Object value) {
            fields.put(key, value);
        }

        @Override
        public String toString() {
            return "EndorsementRequestBody[keys=" + fields.size() + "]";
        }
    }

    /** Contract C4: the new endorsement and the policy header after it. Neither string form shows personal data. */
    public record EndorsementCreatedResponse(
            PolicyController.EndorsementResponse endorsement,
            PolicyController.PolicyHeaderResponse policy) {

        static EndorsementCreatedResponse from(EndorsementResult result) {
            return new EndorsementCreatedResponse(PolicyController.EndorsementResponse.from(result.endorsement()),
                    PolicyController.PolicyHeaderResponse.from(result.policy()));
        }

        @Override
        public String toString() {
            return "EndorsementCreatedResponse[policyNumber=" + policy.policyNumber() + ", sequenceNo="
                    + endorsement.sequenceNo() + "]";
        }
    }

    /** Contract C5: what the endorsement would do. Money is a decimal string (NFR-01). */
    public record EndorsementPreviewResponse(
            EndorsementType type,
            LocalDate effectiveDate,
            String currentAnnualPremium,
            String newAnnualPremium,
            String adjustment,
            long unexpiredDays,
            long termDays) {

        static EndorsementPreviewResponse from(EndorsementPreview preview) {
            return new EndorsementPreviewResponse(
                    preview.type(),
                    preview.effectiveDate(),
                    preview.currentAnnualPremium().toPlainString(),
                    preview.newAnnualPremium().toPlainString(),
                    preview.adjustment().toPlainString(),
                    preview.unexpiredDays(),
                    preview.termDays());
        }
    }

    private final EndorsementService service;

    public EndorsementController(EndorsementService service) {
        this.service = service;
    }

    /** CUSTOMER only, and only the policy's owner (another customer's policy is a 404). 201 with the endorsement. */
    @PostMapping(path = "/policies/{policyNumber}/endorsements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public EndorsementCreatedResponse endorse(@PathVariable("policyNumber") String policyNumber,
            @RequestBody EndorsementRequestBody body, Authentication authentication) {
        return EndorsementCreatedResponse.from(service.endorse(command(policyNumber, body, authentication)));
    }

    /** The same role, body and checks as the endorsement; writes nothing. 200 with the figures. */
    @PostMapping(path = "/policies/{policyNumber}/endorsements/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public EndorsementPreviewResponse preview(@PathVariable("policyNumber") String policyNumber,
            @RequestBody EndorsementRequestBody body, Authentication authentication) {
        return EndorsementPreviewResponse.from(service.preview(command(policyNumber, body, authentication)));
    }

    private static EndorseCommand command(String policyNumber, EndorsementRequestBody body,
            Authentication authentication) {
        return new EndorseCommand(authentication.getName(), policyNumber, body.fields);
    }
}
