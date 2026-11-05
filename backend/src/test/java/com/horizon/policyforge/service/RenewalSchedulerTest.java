package com.horizon.policyforge.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** AC-07.1: the renewal scheduler runs at 23:00 every day, in the configured time zone. */
@Tag("AC-07")
class RenewalSchedulerTest {

    @Test
    @DisplayName("AC-07.1: the scheduler's trigger is the cron 0 0 23 * * * in the configured policyforge.time-zone")
    void theCronIsEndOfDayInTheConfiguredZone() throws Exception {
        Method run = RenewalScheduler.class.getMethod("runScheduled");

        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertThat(scheduled).as("runScheduled carries @Scheduled").isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 23 * * *");
        assertThat(scheduled.zone()).contains("policyforge.time-zone");
    }
}
