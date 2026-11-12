package com.boxcopilot.backend.config;

import com.boxcopilot.backend.service.McpAllowanceProbe;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the fallback for {@link McpAllowanceProbe}. It steps aside as soon as the MCP feature contributes its
 * own implementation, so with the feature switched off nobody is reported as allowed to use MCP.
 */
@Configuration(proxyBeanMethods = false)
class McpAllowanceProbeConfiguration {

    @Bean
    @ConditionalOnMissingBean(McpAllowanceProbe.class)
    McpAllowanceProbe mcpIsNotAvailable() {
        return userId -> false;
    }
}
