package com.horizon.policyforge.web;

import com.horizon.policyforge.support.DashboardApi;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Shared set-up of the dashboard integration tests (AC-11). They share one application context of their own (its own
 * marker property), with the renewal tests' movable clock at 2026-11-01 and the same helpers, because the dashboard is
 * proved against policies that the real issue, endorse, renew and cancel endpoints created. The portfolio seed runs in
 * every context (P-15), so the tests never assume a count or a position: they read the dashboard before and after and
 * compare, or find their own policies by number.
 */
@SpringBootTest(properties = "policyforge.test.context=dashboard")
abstract class DashboardTestSupport extends RenewalTestSupport {

    protected DashboardApi dashboards;

    @BeforeEach
    void setUpDashboardSupport() {
        dashboards = new DashboardApi(mvc, json);
    }
}
