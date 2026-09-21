package com.boxcopilot.backend.mcp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Clock;

/**
 * The persistent OAuth2 stores (Flyway tables {@code oauth2_registered_client} and {@code oauth2_authorization}).
 *
 * <p>The plain {@link JdbcOAuth2AuthorizationService} is created inside the decorator's bean method rather than
 * being a bean of its own: Spring Authorization Server resolves the single {@code OAuth2AuthorizationService}
 * bean from the context, and only {@link McpAuthorizationService} may be found there.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpOAuth2StoreConfiguration {

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    McpAuthorizationService mcpAuthorizationService(JdbcTemplate jdbcTemplate,
                                                    RegisteredClientRepository registeredClientRepository,
                                                    McpAuthorizationPolicy accessPolicy, Clock clock) {
        JdbcOAuth2AuthorizationService store =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        return new McpAuthorizationService(store, accessPolicy, jdbcTemplate, clock);
    }

    @Bean
    McpOpaqueTokenIntrospector mcpOpaqueTokenIntrospector(McpAuthorizationService authorizationService) {
        return new McpOpaqueTokenIntrospector(authorizationService);
    }
}
