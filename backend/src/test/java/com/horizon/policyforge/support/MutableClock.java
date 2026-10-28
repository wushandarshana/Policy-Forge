package com.horizon.policyforge.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that stands still until a test moves it. Always UTC so that a date maps to one instant. */
public final class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(LocalDate today) {
        this.instant = today.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public void setDate(LocalDate today) {
        this.instant = today.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
