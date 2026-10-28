package com.horizon.policyforge.config;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The single injectable Clock (backend CLAUDE.md, TDD fixed-clock rule). Structural rule kept with the Clock. */
@Tag("NFR-08")
class ClockConfigTest {

    @Test
    @DisplayName("NFR-08 clock: the Clock bean uses the configured time zone")
    void clockUsesConfiguredZone() {
        Clock clock = new ClockConfig().clock("Asia/Kolkata");

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("NFR-08 clock: production code never calls now() without a Clock")
    void noProductionCodeBypassesTheClock() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.horizon.policyforge");

        noClasses().should().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(Instant.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .because("NFR-08: all date logic uses the injected java.time.Clock so tests can fix the date")
                .check(production);
    }
}
