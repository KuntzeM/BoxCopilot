package com.boxcopilot.backend.mcp.security;

import com.boxcopilot.backend.mcp.McpEndpoints;
import com.boxcopilot.backend.mcp.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

/**
 * The MCP resource server chain. It sits in front of the application chain and covers only the MCP endpoint
 * and the protected-resource metadata, so nothing else in the application changes. The paths come from
 * {@link McpEndpoints}, which {@code McpStartupGuard} also pins the transport to.
 *
 * <p>The chain is bearer-only: no session is read or created, no request is cached, CSRF and CORS are off
 * (there is no browser and no cookie involved), and authentication comes exclusively from the opaque access
 * token. A session cookie therefore cannot authenticate at {@code /mcp}, and because the application chain has
 * no resource server, a bearer token cannot authenticate at {@code /api/**}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpSecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, McpOpaqueTokenIntrospector introspector,
                                               McpProperties properties) throws Exception {
        McpBearerChallenge challenge =
                new McpBearerChallenge(properties.backendUrl() + McpEndpoints.PROTECTED_RESOURCE_METADATA);
        McpAuthenticationEntryPoint entryPoint = new McpAuthenticationEntryPoint(challenge);
        McpAccessDeniedHandler accessDeniedHandler = new McpAccessDeniedHandler(challenge);

        return http
                .securityMatcher(McpEndpoints.MCP, McpEndpoints.MCP_PATTERN,
                        McpEndpoints.PROTECTED_RESOURCE_METADATA_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(RequestCacheConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(McpEndpoints.PROTECTED_RESOURCE_METADATA_PATTERN).permitAll()
                        .requestMatchers(McpEndpoints.MCP, McpEndpoints.MCP_PATTERN)
                        .hasAuthority(McpOAuth2Attributes.READ_AUTHORITY)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .opaqueToken(opaque -> opaque.introspector(introspector))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
