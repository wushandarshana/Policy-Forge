package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.domain.cancellation.RefundQuote;
import com.horizon.policyforge.service.CancelCommand;
import com.horizon.policyforge.service.CancellationResult;
import com.horizon.policyforge.service.CancellationService;
import com.horizon.policyforge.service.RefundPreview;
import com.horizon.policyforge.service.RefundPreviewCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cancellation (AC-08): preview the refund of cancelling the caller's own policy (contract C3,
 * {@code GET /api/policies/{policyNumber}/cancel/preview}) and cancel it (contract C4,
 * {@code POST /api/policies/{policyNumber}/cancel}). Both are CUSTOMER only and the owner only (another customer's policy
 * is a 404), and the cancel takes {@code application/json} only (NFR-04.3, KD-007). Each handler declares its role with
 * {@code @PreAuthorize}, takes the actor from the authenticated principal and calls one {@link CancellationService}
 * method; every check and rule lives behind it. There is deliberately no PUT, PATCH or DELETE: a refund record is never
 * changed or removed. Responses are built from allow-lists (KD-008): numbers, dates, codes and the formula string.
 *
 * <p>The cancel body is {@code {reason, cancellationDate?}}. It is captured, not bound to a strict type, so the service
 * can reject a missing or unknown reason, a malformed date and every unknown key by name, all together (KD-008). A refund
 * amount can never be supplied: the service calculates it.
 */
@RestController
@RequestMapping("/api")
public class CancellationController {

    /**
     * The body of the cancel request. {@code reason} and {@code cancellationDate} are kept as text: null when absent or
     * null, the text when it was text, and an empty string for anything else (a number, an object), which the service
     * refuses as not a reason or not a date. Any other key is unknown, and is rejected by name.
     */
    public static final class CancelRequest {
        private String reason;
        private String cancellationDate;
        private final Set<String> unknownKeys = new TreeSet<>();

        public void setReason(JsonNode value) {
            this.reason = asText(value);
        }

        public void setCancellationDate(JsonNode value) {
            this.cancellationDate = asText(value);
        }

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        private static String asText(JsonNode value) {
            if (value == null || value.isNull()) {
                return null;
            }
            return value.isTextual() ? value.asText() : "";
        }

        @Override
        public String toString() {
            return "CancelRequest[keys=" + unknownKeys.size() + "]";
        }
    }

    /** Contract C3: what cancelling on the date would refund. Money is a decimal string (NFR-01). */
    public record RefundPreviewResponse(
            LocalDate cancellationDate,
            int termNo,
            LocalDate termStart,
            LocalDate termEnd,
            String premium,
            int termDays,
            int unexpiredDays,
            boolean coolingOff,
            String fee,
            String amount,
            String formula) {

        static RefundPreviewResponse from(RefundPreview preview) {
            RefundQuote quote = preview.quote();
            return new RefundPreviewResponse(
                    preview.cancellationDate(),
                    preview.termNo(),
                    preview.termStart(),
                    preview.termEnd(),
                    preview.premium().toPlainString(),
                    quote.termDays(),
                    quote.unexpiredDays(),
                    quote.coolingOff(),
                    quote.fee().toPlainString(),
                    quote.amount().toPlainString(),
                    quote.formula());
        }
    }

    /** Contract C4: the refund record that was written and the policy header after it (CANCELLED). */
    public record CancellationResponse(
            PolicyController.RefundResponse refund,
            PolicyController.PolicyHeaderResponse policy) {

        static CancellationResponse from(CancellationResult result) {
            return new CancellationResponse(PolicyController.RefundResponse.from(result.refund()),
                    PolicyController.PolicyHeaderResponse.from(result.policy()));
        }
    }

    private final CancellationService cancellation;

    public CancellationController(CancellationService cancellation) {
        this.cancellation = cancellation;
    }

    /** CUSTOMER only, and only the policy's owner. Read-only. 200 with the figures. */
    @GetMapping("/policies/{policyNumber}/cancel/preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RefundPreviewResponse preview(@PathVariable("policyNumber") String policyNumber,
            @RequestParam(name = "date", required = false) String date, Authentication authentication) {
        return RefundPreviewResponse.from(
                cancellation.preview(new RefundPreviewCommand(authentication.getName(), policyNumber, date)));
    }

    /** CUSTOMER only, and only the policy's owner. JSON only (NFR-04.3). 201 with the refund and the policy header. */
    @PostMapping(path = "/policies/{policyNumber}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CancellationResponse cancel(@PathVariable("policyNumber") String policyNumber,
            @RequestBody(required = false) CancelRequest body, Authentication authentication) {
        CancelRequest read = body == null ? new CancelRequest() : body;
        return CancellationResponse.from(cancellation.cancel(new CancelCommand(authentication.getName(), policyNumber,
                read.reason, read.cancellationDate, List.copyOf(read.unknownKeys))));
    }
}
