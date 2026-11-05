package com.horizon.policyforge.web;

import com.horizon.policyforge.service.DashboardService;
import com.horizon.policyforge.service.PortfolioDashboard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin portfolio dashboard (contract C6, AC-11): {@code GET /api/admin/dashboard?asOf=YYYY-MM-DD}. ADMIN only,
 * declared with {@code @PreAuthorize}, and read only: there is deliberately no other verb. The {@code asOf} date is
 * taken as text so a malformed one is a 400 naming {@code asOf}. The handler calls one {@link DashboardService} method
 * and maps the answer, with money as decimal strings (NFR-01), from an allow-list of fields (NFR-04.2).
 */
@RestController
@RequestMapping("/api/admin")
public class DashboardController {

    /** Contract C6: exactly the spec's keys. */
    public record DashboardResponse(
            LocalDate asOf,
            Map<String, Integer> activePoliciesByProduct,
            Map<String, String> premiumCollected,
            List<PipelineItem> renewalPipeline,
            LapseForecastResponse lapseForecast) {

        static DashboardResponse from(PortfolioDashboard dashboard) {
            Map<String, String> money = new LinkedHashMap<>();
            dashboard.premiumCollected().forEach((key, amount) -> money.put(key, amount.toPlainString()));
            return new DashboardResponse(dashboard.asOf(), dashboard.activePoliciesByProduct(), money,
                    dashboard.renewalPipeline().stream().map(PipelineItem::from).toList(),
                    new LapseForecastResponse(dashboard.lapseForecast().count(),
                            dashboard.lapseForecast().policies()));
        }
    }

    /** One renewal pipeline entry: the renewal premium is a decimal string, or null when no offer exists. */
    public record PipelineItem(String policyNumber, LocalDate termEnd, String renewalPremium, boolean paid) {

        static PipelineItem from(PortfolioDashboard.PipelineEntry entry) {
            BigDecimal premium = entry.renewalPremium();
            return new PipelineItem(entry.policyNumber(), entry.termEnd(),
                    premium == null ? null : premium.toPlainString(), entry.paid());
        }
    }

    public record LapseForecastResponse(int count, List<String> policies) {
    }

    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    /** ADMIN only. {@code asOf} defaults to today. */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardResponse dashboard(@RequestParam(name = "asOf", required = false) String asOf) {
        return DashboardResponse.from(dashboards.dashboard(asOf));
    }
}
