package com.boxcopilot.backend.mcp.security;

import com.boxcopilot.backend.mcp.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Clock;

/**
 * Removes the stored authorizations that can never be used again. Spring Authorization Server deletes no row of
 * its own, so without this the table grows for as long as the feature is switched on, and every expired
 * connection stays readable in the database.
 *
 * <p>The rules live in {@link McpAuthorizationRegistry#deleteExpired}; this class only decides when they run.
 * The schedule is configurable because the right time is an operational question, and the work is one query and
 * one batch delete on a table with a row per connection.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpAuthorizationCleanup {

    private static final Logger log = LoggerFactory.getLogger(McpAuthorizationCleanup.class);

    private final McpAuthorizationRegistry registry;
    private final McpProperties properties;
    private final Clock clock;

    public McpAuthorizationCleanup(McpAuthorizationRegistry registry, McpProperties properties, Clock clock) {
        Assert.notNull(registry, "registry cannot be null");
        Assert.notNull(properties, "properties cannot be null");
        Assert.notNull(clock, "clock cannot be null");
        this.registry = registry;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Runs the cleanup and reports how much it removed. Counts are safe to log: they name no user, no client
     * and no token.
     */
    @Scheduled(cron = "${app.mcp.cleanup-cron:" + McpProperties.DEFAULT_CLEANUP_CRON + "}")
    public void removeExpiredAuthorizations() {
        int removed = registry.deleteExpired(clock.instant(), properties.maxConnectionAge());
        if (removed > 0) {
            log.info("MCP cleanup removed {} expired authorizations", removed);
        }
    }
}
