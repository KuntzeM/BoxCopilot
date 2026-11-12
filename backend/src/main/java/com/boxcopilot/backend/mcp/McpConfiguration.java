package com.boxcopilot.backend.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.ai.mcp.customizer.McpStatelessSyncServerCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
     * MCP SDK 2 validates tool arguments against the input schema before the tool runs and answers a miss with
     * its own validator text (in the JVM's default locale), so such a call would never be audited or counted.
     * The tools validate their input themselves; switching the SDK check off keeps every call on that one path.
     */
    @Bean
    McpStatelessSyncServerCustomizer toolsValidateTheirOwnInput() {
        return spec -> spec.validateToolInputs(false);
    }

    /**
     * A registry for the MCP counters: a plain in-process registry, so the counters have somewhere to go even in a
     * context without Actuator's metrics auto-configuration. This configuration is processed before the
     * auto-configurations, so with Actuator on the classpath this is the one registry and Boot's own simple
     * registry steps aside (it is {@code @ConditionalOnMissingBean}); no metrics endpoint is exposed either way.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry mcpMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
