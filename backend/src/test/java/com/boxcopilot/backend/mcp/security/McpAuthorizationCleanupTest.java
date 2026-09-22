package com.boxcopilot.backend.mcp.security;

import com.boxcopilot.backend.mcp.McpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the cleanup asks the store for, and when it is scheduled to ask. */
class McpAuthorizationCleanupTest {

    private static final Instant NOW = Instant.parse("2026-09-22T03:00:00Z");
    private static final Duration MAX_AGE = Duration.ofDays(14);

    private final McpAuthorizationRegistry registry = mock(McpAuthorizationRegistry.class);
    private final McpAuthorizationCleanup cleanup = new McpAuthorizationCleanup(registry,
            new McpProperties(true, "https://boxpilot.example", List.of(), MAX_AGE, null, null, null),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void itDeletesAgainstTheClockAndTheConfiguredAbsoluteAge() {
        when(registry.deleteExpired(NOW, MAX_AGE)).thenReturn(3);

        cleanup.removeExpiredAuthorizations();

        verify(registry).deleteExpired(NOW, MAX_AGE);
    }

    @Test
    void itRunsOnAConfigurableCronThatDefaultsToNightlyAtThree() throws Exception {
        Scheduled scheduled = McpAuthorizationCleanup.class
                .getMethod("removeExpiredAuthorizations")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${app.mcp.cleanup-cron:" + McpProperties.DEFAULT_CLEANUP_CRON + "}");
        assertThat(CronExpression.isValidExpression(McpProperties.DEFAULT_CLEANUP_CRON)).isTrue();
        assertThat(CronExpression.parse(McpProperties.DEFAULT_CLEANUP_CRON)
                .next(NOW.atZone(ZoneOffset.UTC).withHour(0)).toLocalTime())
                .isEqualTo(LocalTime.of(3, 0));
    }
}
