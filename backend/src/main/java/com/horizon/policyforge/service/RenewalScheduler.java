package com.horizon.policyforge.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The end-of-day trigger of the renewal cycle (AC-07.1): every day at 23:00 in the configured {@code
 * policyforge.time-zone} it runs the cycle for today's date on the injected clock, as the actor {@code
 * system:renewal-scheduler}. It holds no logic of its own, so the admin job and the schedule run exactly the same code.
 * {@code SchedulingConfig} turns the trigger off in test configuration, and tests call {@link #runScheduled()} directly.
 */
@Component
public class RenewalScheduler {

    private final RenewalService renewal;
    private final Clock clock;

    public RenewalScheduler(RenewalService renewal, Clock clock) {
        this.renewal = renewal;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 23 * * *", zone = "${policyforge.time-zone:Asia/Kolkata}")
    public void runScheduled() {
        renewal.runCycle(LocalDate.now(clock), RenewalService.SYSTEM_ACTOR);
    }
}
