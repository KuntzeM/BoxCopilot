package com.boxcopilot.backend.mcp;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Makes {@code app.mcp.enabled} the only switch. Unless it is {@code true}, the Spring AI starter's own switch is
 * forced off with the highest precedence, whatever a profile or the environment sets it to; otherwise the starter
 * would register a server, a transport without our extractor and host check, and {@code /mcp} with no MCP
 * security chain in front of it. With the flag on, the starter's switch is left as configured. It reads
 * {@code app.mcp.enabled} after config-data loading; a value set later ({@code @DynamicPropertySource}, a context
 * initializer) is not seen, so the starter stays off (fails closed).
 */
public final class McpStarterSwitch implements EnvironmentPostProcessor {

    static final String STARTER_SWITCH = "spring.ai.mcp.server.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // The same comparison as @ConditionalOnProperty(havingValue = "true") on the MCP configuration
        if (!"true".equalsIgnoreCase(environment.getProperty("app.mcp.enabled"))) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("mcpStarterSwitch", Map.of(STARTER_SWITCH, "false")));
        }
    }
}
