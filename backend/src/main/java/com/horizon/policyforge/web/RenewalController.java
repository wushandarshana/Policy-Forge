package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.horizon.policyforge.service.PayRenewalCommand;
import com.horizon.policyforge.service.RenewalPayment;
import com.horizon.policyforge.service.RenewalService;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renewal payment (contract C2, AC-07.5): {@code POST /api/policies/{policyNumber}/renewal/pay} pays the pending renewal
 * term of the caller's own policy. CUSTOMER only, the owner only (another customer's policy is a 404), and {@code
 * application/json} only (NFR-04.3, KD-007). The handler declares its role with {@code @PreAuthorize}, takes the actor
 * from the authenticated principal and calls one {@link RenewalService} method; every check and write lives behind it.
 * The body is empty or {@code {}} and is captured, not ignored, so any key it carries (an amount, a date) is rejected by
 * name (KD-008): the amount paid is the pending term's premium, never a client value. There is deliberately no PUT,
 * PATCH or DELETE.
 */
@RestController
@RequestMapping("/api")
public class RenewalController {

    /** The body of the payment request: empty or {@code {}}. Any key it carries is unknown, and is rejected by name. */
    public static final class PayRequest {
        private final Set<String> unknownKeys = new TreeSet<>();

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "PayRequest[keys=" + unknownKeys.size() + "]";
        }
    }

    /** Contract C2: the policy header after the payment (RENEWED, at the renewal term's dates) and the paid term. */
    public record RenewalPaymentResponse(
            PolicyController.PolicyHeaderResponse policy,
            PolicyController.TermResponse term) {

        static RenewalPaymentResponse from(RenewalPayment payment) {
            return new RenewalPaymentResponse(PolicyController.PolicyHeaderResponse.from(payment.policy()),
                    PolicyController.TermResponse.from(payment.term()));
        }
    }

    private final RenewalService renewal;

    public RenewalController(RenewalService renewal) {
        this.renewal = renewal;
    }

    /** CUSTOMER only, and only the policy's owner. 200 with the header and the paid term. */
    @PostMapping(path = "/policies/{policyNumber}/renewal/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public RenewalPaymentResponse pay(@PathVariable("policyNumber") String policyNumber,
            @RequestBody(required = false) PayRequest body, Authentication authentication) {
        List<String> unknownKeys = body == null ? List.of() : List.copyOf(body.unknownKeys);
        return RenewalPaymentResponse.from(
                renewal.pay(new PayRenewalCommand(authentication.getName(), policyNumber, unknownKeys)));
    }
}
