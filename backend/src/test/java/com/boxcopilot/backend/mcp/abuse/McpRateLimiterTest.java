package com.boxcopilot.backend.mcp.abuse;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The counter behind every MCP rate limit: fixed window, one key at a time, decided against an injected clock. */
class McpRateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    /** A clock the test moves by hand, so a window can elapse without anything sleeping. */
    private static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-09-22T10:00:00Z");

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

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    private final TestClock clock = new TestClock();

    @Test
    void allowsExactlyTheConfiguredNumberOfCallsWithinOneWindow() {
        McpRateLimiter limiter = new McpRateLimiter(3, MINUTE, clock);

        assertThat(IntStream.range(0, 3).allMatch(i -> limiter.tryAcquire("7"))).isTrue();
        assertThat(limiter.tryAcquire("7")).isFalse();
        assertThat(limiter.tryAcquire("7")).isFalse();
    }

    @Test
    void countsEveryKeyOnItsOwn() {
        McpRateLimiter limiter = new McpRateLimiter(1, MINUTE, clock);

        assertThat(limiter.tryAcquire("7")).isTrue();
        assertThat(limiter.tryAcquire("8")).isTrue();
        assertThat(limiter.tryAcquire("7")).isFalse();
    }

    @Test
    void opensAFreshWindowOnceTheOldOneHasElapsed() {
        McpRateLimiter limiter = new McpRateLimiter(2, MINUTE, clock);
        limiter.tryAcquire("7");
        limiter.tryAcquire("7");

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("7")).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("7")).isTrue();
    }

    /** The image quota: one window per day, so the count has to survive a whole day of calls. */
    @Test
    void aDailyWindowHoldsTheCountAcrossTheDayAndResetsOnTheNext() {
        McpRateLimiter limiter = new McpRateLimiter(200, Duration.ofDays(1), clock);

        for (int i = 0; i < 200; i++) {
            clock.advance(Duration.ofMinutes(5));
            assertThat(limiter.tryAcquire("7")).as("call %d", i).isTrue();
        }
        assertThat(limiter.tryAcquire("7")).isFalse();

        clock.advance(Duration.ofDays(1));
        assertThat(limiter.tryAcquire("7")).isTrue();
    }

    /** Without a compare-and-set the increment would be lost under load and let more calls than the limit pass. */
    @Test
    void concurrentCallersNeverGetMorePermitsThanTheLimit() throws Exception {
        int limit = 50;
        int threads = 16;
        int callsPerThread = 20;
        McpRateLimiter limiter = new McpRateLimiter(limit, MINUTE, clock);
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < callsPerThread; i++) {
                            if (limiter.tryAcquire("7")) {
                                granted.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(granted.get()).isEqualTo(limit);
    }

    @Test
    void evictionDropsOnlyKeysWhoseWindowHasElapsed() {
        McpRateLimiter limiter = new McpRateLimiter(1, MINUTE, clock);
        limiter.tryAcquire("old");

        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire("recent");
        clock.advance(Duration.ofSeconds(31));
        limiter.evictStale();

        assertThat(limiter.trackedKeys()).isEqualTo(1);
        assertThat(limiter.tryAcquire("recent")).isFalse();
        assertThat(limiter.tryAcquire("old")).isTrue();
    }

    @Test
    void exhaustionIsReadableWithoutTakingAPermit() {
        McpRateLimiter limiter = new McpRateLimiter(2, MINUTE, clock);

        assertThat(limiter.isExhausted("7")).isFalse();
        limiter.tryAcquire("7");
        assertThat(limiter.isExhausted("7")).isFalse();
        limiter.tryAcquire("7");
        assertThat(limiter.isExhausted("7")).isTrue();

        clock.advance(MINUTE);
        assertThat(limiter.isExhausted("7")).isFalse();
    }

    @Test
    void rejectsAMeaninglessConfiguration() {
        assertThatThrownBy(() -> new McpRateLimiter(0, MINUTE, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpRateLimiter(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
