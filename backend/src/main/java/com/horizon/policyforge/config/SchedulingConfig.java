package com.horizon.policyforge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled methods, which today means the end-of-day renewal cycle (AC-07.1). It is on unless
 * {@code policyforge.scheduling.enabled} is {@code false}. Test configuration sets it to false, so a run at 23:00 can
 * never touch an integration-test database, and the tests call {@code RenewalScheduler} directly on a fixed clock.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "policyforge.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
