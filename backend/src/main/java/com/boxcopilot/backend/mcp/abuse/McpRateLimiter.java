package com.boxcopilot.backend.mcp.abuse;

import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A fixed-window counter, the whole of BoxPilot's abuse protection. The specification rules out a third-party
 * limiter: there is exactly one backend process, so a map of counters in its heap is the complete picture.
 *
 * <p>Every key holds the start of its current window and how many permits were taken in it. A permit is handed
 * out with a compare-and-set, so under load exactly {@code limit} callers get through a window and the rest are
 * turned away; a plain read-modify-write would lose increments and let more through.
 *
 * <p>Memory is bounded by {@link #evictStale()}, which {@code McpRateLimits} calls on a schedule. Eviction only
 * ever removes a key whose window has already elapsed. A caller racing with that eviction re-creates the key and
 * starts a new window, which is exactly what it would have got from the evicted entry, so the race cannot hand
 * out more permits than the limit.
 */
public final class McpRateLimiter {

    private final ConcurrentHashMap<String, AtomicReference<Window>> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;
    private final Duration window;
    private final Clock clock;

    public McpRateLimiter(int limit, Duration window, Clock clock) {
        Assert.isTrue(limit > 0, "limit must be positive");
        Assert.notNull(window, "window cannot be null");
        Assert.isTrue(!window.isZero() && !window.isNegative(), "window must be positive");
        Assert.notNull(clock, "clock cannot be null");
        this.limit = limit;
        this.window = window;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /**
     * Takes one permit for {@code key}.
     *
     * @return {@code true} if the key was still below its limit in the current window
     */
    public boolean tryAcquire(String key) {
        Assert.hasText(key, "key cannot be empty");
        long now = clock.millis();
        AtomicReference<Window> reference =
                windows.computeIfAbsent(key, ignored -> new AtomicReference<>(new Window(now, 0)));
        while (true) {
            Window current = reference.get();
            boolean elapsed = now - current.startedAt() >= windowMillis;
            if (!elapsed && current.taken() >= limit) {
                return false;
            }
            Window next = elapsed ? new Window(now, 1) : new Window(current.startedAt(), current.taken() + 1);
            if (reference.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    /**
     * Reads whether {@code key} has already used up its window, without taking a permit. The failure limiter
     * needs this: it counts only failed calls, but has to turn a caller away before the work that could fail.
     */
    public boolean isExhausted(String key) {
        Assert.hasText(key, "key cannot be empty");
        AtomicReference<Window> reference = windows.get(key);
        if (reference == null) {
            return false;
        }
        Window current = reference.get();
        return clock.millis() - current.startedAt() < windowMillis && current.taken() >= limit;
    }

    /** Drops every key whose window has elapsed; their counts would be reset on the next call anyway. */
    public void evictStale() {
        long now = clock.millis();
        windows.values().removeIf(reference -> now - reference.get().startedAt() >= windowMillis);
    }

    /** How long a caller has to wait at worst before the window opens again. */
    public Duration window() {
        return window;
    }

    /** How many keys are currently held; used to prove that eviction keeps the map bounded. */
    public int trackedKeys() {
        return windows.size();
    }

    private record Window(long startedAt, int taken) {
    }
}
