package com.boxcopilot.backend.mcp.abuse;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Records every rejection the rate-limit filters make. Without it the only visible trace of a blocked caller
 * would be a 429 in the reverse proxy's access log, and the very event the protection exists for - somebody
 * hitting a limit - would be the one event the backend says nothing about.
 *
 * <p>The tag is the limit's fixed name, never the key that was blocked: a per-address or per-subject tag
 * would let a caller create an unbounded number of time series, which is the same discipline
 * {@code McpToolMetrics} follows. The key does go into the audit line, where the address and the user id are
 * already what the tool audit records.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpRateLimitAudit {

    /** Name of the counter; the tag {@link #LIMIT_TAG} separates the series. */
    public static final String COUNTER = "boxpilot.mcp.rate.limited";

    public static final String LIMIT_TAG = "limit";

    /** Stands in for a field that does not apply to this limit, so no field is ever empty. */
    static final String NOT_APPLICABLE = "-";

    private static final Logger log = LoggerFactory.getLogger(McpRateLimitAudit.class);

    private final MeterRegistry registry;

    public McpRateLimitAudit(MeterRegistry registry) {
        Assert.notNull(registry, "registry cannot be null");
        this.registry = registry;
    }

    /**
     * @param limit         which limit was reached
     * @param subject       the token subject the limit is keyed by, or {@code null} for an address limit
     * @param clientAddress the resolved client address
     */
    public void rejected(McpRateLimit limit, String subject, String clientAddress) {
        log.info("MCP rate limit reached limit={} sub={} ip={}", limit.tag(),
                subject == null ? NOT_APPLICABLE : subject,
                clientAddress == null ? McpClientAddress.UNKNOWN : clientAddress);
        registry.counter(COUNTER, LIMIT_TAG, limit.tag()).increment();
    }
}
