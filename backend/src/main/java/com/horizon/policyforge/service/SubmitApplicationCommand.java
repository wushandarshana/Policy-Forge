package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.application.KycInput;
import java.util.List;

/**
 * A customer's application request, already mapped from the wire shape.
 *
 * @param customerId  the authenticated username of the submitting customer
 * @param quoteId     the quote the application is for
 * @param kyc         the submitted KYC details, or null when the body had none (its string form is redacted)
 * @param unknownKeys the dotted paths of keys the body carried that the contract does not define (U-1, AC-03.5)
 */
public record SubmitApplicationCommand(String customerId, String quoteId, KycInput kyc, List<String> unknownKeys) {

    public SubmitApplicationCommand {
        unknownKeys = unknownKeys == null ? List.of() : List.copyOf(unknownKeys);
    }
}
