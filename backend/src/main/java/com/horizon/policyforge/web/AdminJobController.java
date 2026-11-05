package com.horizon.policyforge.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.horizon.policyforge.service.RenewalCycleResult;
import com.horizon.policyforge.service.RenewalService;
import com.horizon.policyforge.service.RunCycleCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin jobs: {@code POST /api/admin/jobs/renewal-cycle?date=YYYY-MM-DD} runs the renewal cycle on demand for testing
 * (contract C1, AC-07). ADMIN only, and {@code application/json} only (NFR-04.3, KD-007). The handler declares its role
 * with {@code @PreAuthorize}, takes the actor from the authenticated principal and calls one {@link RenewalService}
 * method; the date check, the cycle and the audit entry live behind it. The date is taken as text so a malformed one is a
 * 400 naming {@code date}, and the body is captured, not ignored, so any key it carries is rejected by name (KD-008).
 */
@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobController {

    /** The body of the job request: empty or {@code {}}. Any key it carries is unknown, and is rejected by name. */
    public static final class JobRequest {
        private final Set<String> unknownKeys = new TreeSet<>();

        @JsonAnySetter
        public void unknownKey(String key, Object ignoredValue) {
            unknownKeys.add(key);
        }

        @Override
        public String toString() {
            return "JobRequest[keys=" + unknownKeys.size() + "]";
        }
    }

    /** Contract C1: the date the cycle ran for, the policies offered a renewal and the policies lapsed, ascending. */
    public record RenewalCycleResponse(LocalDate runDate, List<String> offersCreated, List<String> lapsed) {

        static RenewalCycleResponse from(RenewalCycleResult result) {
            return new RenewalCycleResponse(result.runDate(), result.offersCreated(), result.lapsed());
        }
    }

    private final RenewalService renewal;

    public AdminJobController(RenewalService renewal) {
        this.renewal = renewal;
    }

    /** ADMIN only. The date defaults to today. A second run for the same date changes nothing (AC-07.2). */
    @PostMapping(path = "/renewal-cycle", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public RenewalCycleResponse renewalCycle(@RequestParam(name = "date", required = false) String date,
            @RequestBody(required = false) JobRequest body, Authentication authentication) {
        List<String> unknownKeys = body == null ? List.of() : List.copyOf(body.unknownKeys);
        return RenewalCycleResponse.from(
                renewal.runCycle(new RunCycleCommand(authentication.getName(), date, unknownKeys)));
    }
}
