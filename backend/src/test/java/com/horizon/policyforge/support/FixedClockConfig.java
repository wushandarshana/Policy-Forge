package com.horizon.policyforge.support;

import java.time.LocalDate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A movable test clock for integration tests that share one application context ({@code @Import} this class). The
 * date starts at {@link #TODAY}. A test that moves the clock puts it back in {@code @AfterEach}.
 */
@TestConfiguration
public class FixedClockConfig {

    public static final LocalDate TODAY = LocalDate.parse("2026-10-28");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(TODAY);
    }
}
