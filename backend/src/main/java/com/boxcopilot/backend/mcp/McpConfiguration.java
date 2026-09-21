package com.boxcopilot.backend.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Root of the MCP configuration; nothing in it exists unless {@code app.mcp.enabled=true}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpProperties.class)
class McpConfiguration {
}
