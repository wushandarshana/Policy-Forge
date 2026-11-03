package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.horizon.policyforge.domain.application.KycAddress;
import com.horizon.policyforge.domain.application.KycInput;
import com.horizon.policyforge.domain.underwriting.ReasonCode;
import com.horizon.policyforge.service.ApplicationResult;
import com.horizon.policyforge.service.ApplicationService;
import com.horizon.policyforge.service.SubmitApplicationCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer application endpoints (AC-03): submit an application for a quote and list one's own applications. Both are
 * CUSTOMER only. Maps requests to {@link ApplicationService} and results to DTOs. No business logic lives here.
 *
 * <p>The request types capture, rather than ignore, any key the contract does not define, so the service can reject it
 * naming its dotted path (U-1, KD-008). They keep the key names only, never the values, and their string form is
 * redacted so a framework debug line cannot print the KYC details (NFR-03).
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    /** The request body of {@code POST /api/applications}: exactly {@code {quoteId, kyc}} (U-1). */
    public static final class SubmitRequest {
        private String quoteId;
        private KycRequest kyc;
        private final Set<String> unknownKeys = new TreeSet<>();

        public void setQuoteId(String quoteId) {
            this.quoteId = quoteId;
        }

        public void setKyc(KycRequest kyc) {
            this.kyc = kyc;
        }

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "SubmitRequest[redacted]";
        }
    }

    /** The KYC part of the request. */
    public static final class KycRequest {
        private String fullName;
        private String dateOfBirth;
        private String aadhaar;
        private String pan;
        private AddressRequest address;
        private final Set<String> unknownKeys = new TreeSet<>();

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public void setDateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public void setAadhaar(String aadhaar) {
            this.aadhaar = aadhaar;
        }

        public void setPan(String pan) {
            this.pan = pan;
        }

        public void setAddress(AddressRequest address) {
            this.address = address;
        }

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "KycRequest[redacted]";
        }
    }

    /** The address part of the KYC request. */
    public static final class AddressRequest {
        private String line1;
        private String city;
        private String postcode;
        private final Set<String> unknownKeys = new TreeSet<>();

        public void setLine1(String line1) {
            this.line1 = line1;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public void setPostcode(String postcode) {
            this.postcode = postcode;
        }

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "AddressRequest[redacted]";
        }
    }

    /** One reason of an automated decision (U-8). */
    public record ReasonItem(String code, String description) {
        static ReasonItem from(ReasonCode reason) {
            return new ReasonItem(reason.code(), reason.description());
        }
    }

    /**
     * Contract C1 and C2, built from an allow-list (U-7, KD-008): the owner sees no KYC field. {@code decision},
     * {@code reasonCodes}, {@code reasons} and {@code ruleVersion} are the automated evaluation's result.
     */
    public record ApplicationResponse(
            String applicationId,
            String quoteId,
            String productCode,
            String status,
            String decision,
            List<String> reasonCodes,
            List<ReasonItem> reasons,
            int ruleVersion,
            Instant submittedAt) {

        static ApplicationResponse from(ApplicationResult result) {
            return new ApplicationResponse(
                    result.applicationId(),
                    result.quoteId(),
                    result.productCode(),
                    result.status().name(),
                    result.decision().name(),
                    result.reasons().stream().map(ReasonCode::code).toList(),
                    result.reasons().stream().map(ReasonItem::from).toList(),
                    result.ruleVersion(),
                    result.submittedAt());
        }
    }

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    /** CUSTOMER only. JSON only (NFR-04.3). Bodies over 16 KB are refused before this point with 413 (NFR-04.4). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse submit(@RequestBody SubmitRequest request, Authentication authentication) {
        return ApplicationResponse.from(applications.submit(toCommand(authentication.getName(), request)));
    }

    /** CUSTOMER only: the caller's own applications, newest first (U-13). */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ApplicationResponse> mine(Authentication authentication) {
        return applications.mine(authentication.getName()).stream().map(ApplicationResponse::from).toList();
    }

    /** Maps the wire shape to the service command, turning each unknown key into its dotted path. */
    private static SubmitApplicationCommand toCommand(String customerId, SubmitRequest request) {
        List<String> unknown = new ArrayList<>(request.unknownKeys);
        KycInput kyc = null;
        KycRequest body = request.kyc;
        if (body != null) {
            body.unknownKeys.forEach(key -> unknown.add("kyc." + key));
            KycAddress address = null;
            AddressRequest place = body.address;
            if (place != null) {
                place.unknownKeys.forEach(key -> unknown.add("kyc.address." + key));
                address = new KycAddress(place.line1, place.city, place.postcode);
            }
            kyc = new KycInput(body.fullName, body.dateOfBirth, body.aadhaar, body.pan, address);
        }
        return new SubmitApplicationCommand(customerId, request.quoteId, kyc, unknown);
    }
}
