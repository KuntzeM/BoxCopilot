package com.boxcopilot.backend.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Root of the MCP configuration; nothing in it exists unless {@code app.mcp.enabled=true}.
 *
 * <p>Scheduling is switched on here rather than on the application class, so that a BoxPilot without MCP runs
 * with no scheduler at all: the only scheduled work in this application is the MCP authorization cleanup and
 * the sweep of the rate-limit counters.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpProperties.class)
@EnableScheduling
class McpConfiguration {

    /**
     * Token lifetimes and the absolute connection age are decided against this clock, so that tests can move
     * time instead of waiting. The application has no clock of its own today; if one is ever added, it wins.
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * A registry for the MCP counters. Micrometer is on the classpath, but Actuator is not, so nothing
     * auto-configures a {@code MeterRegistry} today and the counters would otherwise have nowhere to go. The
     * specification asks for no new endpoint and no new dependency, so this is a plain in-process registry; as
     * soon as Actuator is added it configures its own and this one steps aside.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    @ConditionalOnMissingClass("org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration")
    MeterRegistry mcpMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
