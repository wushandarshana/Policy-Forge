package com.horizon.policyforge.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** AC-07.1: scheduled triggers are on by default and off when test configuration says so. */
@Tag("AC-07")
class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    @DisplayName("AC-07.1: scheduled methods are enabled by default, so the end-of-day renewal cycle runs in production")
    void schedulingIsOnByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    @DisplayName("AC-07.1: policyforge.scheduling.enabled=false turns every scheduled trigger off, as test configuration does")
    void schedulingCanBeTurnedOff() {
        runner.withPropertyValues("policyforge.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
