package com.boxcopilot.backend.mcp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock a test moves by hand, so a rate-limit window can elapse without anything sleeping. */
public final class MutableTestClock extends Clock {

    private Instant now;

    public MutableTestClock(Instant start) {
        this.now = start;
    }

    public MutableTestClock() {
        this(Instant.parse("2026-09-22T10:00:00Z"));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void advance(Duration amount) {
        now = now.plus(amount);
    }
}
