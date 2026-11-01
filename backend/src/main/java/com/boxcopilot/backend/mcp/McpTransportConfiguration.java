package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Locale;

/**
 * Declares the stateless WebMvc transport with the {@link McpTransportContextExtractor}. The starter offers no hook
 * for an extractor, but backs off when a transport bean exists ({@code @ConditionalOnMissingBean} on
 * {@code McpServerStatelessWebMvcAutoConfiguration#webMvcStatelessServerTransport}); its router function, server
 * and tool registration then build on this transport.
 *
 * <p>The transport also carries the DNS-rebinding protection of the MCP SDK. The builder defaults to a validator
 * that accepts everything and the starter has no property for it, so it is set here: the {@code Host} header has
 * to name the configured backend, and an {@code Origin}, when a browser sends one, has to be the backend's own.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpTransportConfiguration {

    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties streamableHttp, McpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(streamableHttp.getMcpEndpoint())
                .contextExtractor(new McpTransportContextExtractor())
                .securityValidator(hostAndOriginOf(properties.backendUrl()))
                .build();
    }

    /**
     * Allows exactly the host (with the port, unless it is absent or the scheme's default, which clients omit) and the origin of {@code app.mcp.backend-url}.
     * The proxies in front pass the public {@code Host} through unchanged, so this is what a legitimate client
     * sends; a rebound name or a foreign page is answered with 421 or 403 before any tool runs.
     */
    static DefaultServerTransportSecurityValidator hostAndOriginOf(String backendUrl) {
        URI uri = backendUrl == null || backendUrl.isBlank() ? null : URI.create(backendUrl);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("app.mcp.enabled=true requires BACKEND_URL (app.mcp.backend-url)"
                    + " to be an absolute URL");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
        String host = uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        return DefaultServerTransportSecurityValidator.builder()
                .allowedHost(host)
                .allowedOrigin(scheme + "://" + host)
                .build();
    }
}
