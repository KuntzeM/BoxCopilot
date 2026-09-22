package com.boxcopilot.backend.config;

import com.boxcopilot.backend.service.McpPendingProbe;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the fallback for {@link McpPendingProbe}. It steps aside as soon as the MCP feature contributes its
 * own implementation, so with the feature switched off nothing ever reports a pending authorization.
 */
@Configuration(proxyBeanMethods = false)
class McpPendingProbeConfiguration {

    @Bean
    @ConditionalOnMissingBean(McpPendingProbe.class)
    McpPendingProbe noPendingAuthorizations() {
        return request -> false;
    }
}
