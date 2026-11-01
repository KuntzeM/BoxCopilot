package com.boxcopilot.backend.mcp.security;

import com.boxcopilot.backend.mcp.McpEndpoints;
import com.boxcopilot.backend.mcp.McpProperties;
import com.boxcopilot.backend.mcp.abuse.McpFailedTokenRateLimitFilter;
import com.boxcopilot.backend.mcp.abuse.McpRateLimitAudit;
import com.boxcopilot.backend.mcp.abuse.McpRateLimits;
import com.boxcopilot.backend.mcp.abuse.McpSubjectRateLimitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import jakarta.servlet.Filter;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

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
                                               McpProperties properties, McpRateLimits rateLimits,
                                               McpRateLimitAudit rateLimitAudit) throws Exception {
        RequestMatcher mcpEndpoint = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(McpEndpoints.MCP),
                PathPatternRequestMatcher.withDefaults().matcher(McpEndpoints.MCP_PATTERN));
        McpBearerChallenge challenge =
                new McpBearerChallenge(properties.backendUrl() + McpEndpoints.PROTECTED_RESOURCE_METADATA);
        McpAuthenticationEntryPoint entryPoint = new McpAuthenticationEntryPoint(challenge);
        McpAccessDeniedHandler accessDeniedHandler = new McpAccessDeniedHandler(challenge);

        DefaultSecurityFilterChain chain = http
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
                // Around the bearer filter, so the failure limiter sees the outcome of every token lookup this
                // chain makes - including one aimed at the metadata document, which the bearer filter
                // authenticates just the same; behind the authorization filter, so the per-subject limit counts
                // an established identity rather than a claimed one. Neither replaces a security check.
                .addFilterBefore(new McpFailedTokenRateLimitFilter(rateLimits.failedTokenLookups(),
                        rateLimitAudit), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new McpSubjectRateLimitFilter(mcpEndpoint, rateLimits.toolCalls(), rateLimitAudit),
                        AuthorizationFilter.class)
                .build();
        return withoutFrameworkMetadataEndpoint(chain);
    }

    /**
     * Removes the protected-resource metadata endpoint that Spring Security 7 adds to every resource server.
     *
     * <p>{@code OAuth2ResourceServerConfigurer} registers an {@link OAuth2ProtectedResourceMetadataFilter} for
     * {@code GET /.well-known/oauth-protected-resource/**} unconditionally, with no switch to turn it off, and
     * places it ahead of the bearer filter. Left in, it would answer the request before
     * {@link McpProtectedResourceMetadataController}: with a {@code resource} derived from the request URL (and
     * thereby from {@code X-Forwarded-*}) instead of the configured backend URL, without
     * {@code authorization_servers} and {@code scopes_supported}, advertising certificate-bound tokens BoxPilot
     * never issues, and without the bearer filter ever seeing a token presented to it, which takes the metadata
     * lane out of the failed-token budget. Removing it keeps the document and the chain exactly as they were.
     */
    private static SecurityFilterChain withoutFrameworkMetadataEndpoint(DefaultSecurityFilterChain chain) {
        List<Filter> filters = chain.getFilters().stream()
                .filter(filter -> !(filter instanceof OAuth2ProtectedResourceMetadataFilter))
                .toList();
        return new DefaultSecurityFilterChain(chain.getRequestMatcher(), filters);
    }
}
