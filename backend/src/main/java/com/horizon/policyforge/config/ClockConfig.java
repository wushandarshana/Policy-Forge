package com.horizon.policyforge.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for the application. Every date calculation injects this {@link Clock}; tests replace it
 * with {@code Clock.fixed(...)}. Calling {@code LocalDate.now()} without a clock is banned.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${policyforge.time-zone:Asia/Kolkata}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
