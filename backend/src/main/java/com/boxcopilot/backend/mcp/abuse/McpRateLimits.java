package com.boxcopilot.backend.mcp.abuse;

import com.boxcopilot.backend.security.FixedWindowRateLimiter;
import com.boxcopilot.backend.mcp.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The four limits the specification asks for, in one place so that the filters and the tools share exactly the
 * counters the configuration describes.
 *
 * <p>Two of them are keyed by the token subject (the internal user id), because Anthropic's egress addresses are
 * shared by every Claude user and an IP would therefore punish the wrong people. The other two are keyed by the
 * client address, which is trustworthy only because forwarded headers are resolved from the internal proxies
 * alone (M4).
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpRateLimits {

    /** How often stale counters are swept out; short enough to bound memory, long enough to cost nothing. */
    private static final long SWEEP_MINUTES = 5;

    private final FixedWindowRateLimiter toolCalls;
    private final FixedWindowRateLimiter imageCalls;
    private final FixedWindowRateLimiter authorizationRequests;
    private final FixedWindowRateLimiter failedTokenLookups;
    private final List<FixedWindowRateLimiter> all;

    public McpRateLimits(McpProperties properties, Clock clock) {
        McpProperties.RateLimit configured = properties.rateLimit();
        this.toolCalls = new FixedWindowRateLimiter(configured.callsPerMinute(), Duration.ofMinutes(1), clock);
        this.imageCalls = new FixedWindowRateLimiter(configured.imageCallsPerDay(), Duration.ofDays(1), clock);
        this.authorizationRequests =
                new FixedWindowRateLimiter(configured.authorizationRequestsPerMinute(), Duration.ofMinutes(1), clock);
        this.failedTokenLookups =
                new FixedWindowRateLimiter(configured.failedTokenLookupsPerMinute(), Duration.ofMinutes(1), clock);
        this.all = List.of(toolCalls, imageCalls, authorizationRequests, failedTokenLookups);
    }

    /** Calls to {@code /mcp} per token subject and minute. */
    public FixedWindowRateLimiter toolCalls() {
        return toolCalls;
    }

    /** {@code get_item_image} calls per token subject and day. */
    public FixedWindowRateLimiter imageCalls() {
        return imageCalls;
    }

    /** Requests to the authorize and token endpoints per client address and minute. */
    public FixedWindowRateLimiter authorizationRequests() {
        return authorizationRequests;
    }

    /** Failed bearer-token lookups at {@code /mcp} per client address and minute. */
    public FixedWindowRateLimiter failedTokenLookups() {
        return failedTokenLookups;
    }

    /**
     * Keeps the counter maps bounded. Without it every address and every subject that ever called would stay in
     * the heap; eviction only removes keys whose window has elapsed, so no counter is reset early.
     */
    @Scheduled(fixedDelay = SWEEP_MINUTES, timeUnit = TimeUnit.MINUTES)
    public void evictStaleCounters() {
        all.forEach(FixedWindowRateLimiter::evictStale);
    }
}
