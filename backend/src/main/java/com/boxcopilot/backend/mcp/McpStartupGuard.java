package com.boxcopilot.backend.mcp;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Aborts startup when MCP is enabled with an unsafe or incomplete configuration. It only exists when
 * {@code app.mcp.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpStartupGuard implements InitializingBean {

    private static final String TEST_PROFILE = "test";
    private static final String PROD_PROFILE = "prod";

    private final McpProperties properties;
    private final Environment environment;

    McpStartupGuard(McpProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of(TEST_PROFILE))) {
            throw new IllegalStateException(
                    "app.mcp.enabled=true is not allowed with the '" + TEST_PROFILE + "' profile");
        }
        String backendUrl = properties.backendUrl();
        if (backendUrl == null || backendUrl.isBlank()) {
            throw new IllegalStateException("app.mcp.enabled=true requires BACKEND_URL (app.mcp.backend-url)");
        }
        if (environment.acceptsProfiles(Profiles.of(PROD_PROFILE)) && !backendUrl.startsWith("https://")) {
            throw new IllegalStateException(
                    "app.mcp.backend-url must start with https:// in the '" + PROD_PROFILE + "' profile");
        }
        requireUsableBaseUrl(backendUrl);
        requireSameOriginAsSpa(backendUrl, properties.spaApiBaseUrl());
        if (properties.redirectUris().isEmpty() || properties.redirectUris().stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException(
                    "app.mcp.enabled=true requires at least one non-blank redirect URI (app.mcp.redirect-uris)");
        }
        if (properties.maxConnectionAge().isZero() || properties.maxConnectionAge().isNegative()) {
            throw new IllegalStateException("app.mcp.max-connection-age must be a positive duration");
        }
        requirePinnedTransportEndpoint();
        requireStatelessTransport();
    }

    /**
     * Only the stateless transport keeps every MCP request a self-contained, bearer-authenticated call. The SSE and
     * streamable protocols keep a server-side session and publish further endpoints, which the resource server
     * chain does not cover, so anything but {@code STATELESS} is a startup error.
     */
    private void requireStatelessTransport() {
        String protocol = environment.getProperty(McpEndpoints.PROTOCOL_PROPERTY, McpEndpoints.STATELESS_PROTOCOL);
        if (!McpEndpoints.STATELESS_PROTOCOL.equals(protocol)) {
            throw new IllegalStateException(McpEndpoints.PROTOCOL_PROPERTY + " must be '"
                    + McpEndpoints.STATELESS_PROTOCOL + "'; a session transport is not protected by the MCP chain");
        }
        for (String property : McpEndpoints.SESSION_TRANSPORT_PROPERTIES) {
            if (environment.containsProperty(property)) {
                throw new IllegalStateException(
                        property + " must not be configured; it only applies to the session transport");
            }
        }
    }

    /**
     * The resource server chain matches {@link McpEndpoints#MCP} literally. If the transport were published
     * anywhere else, its router function would fall through to the application chain, where a session cookie
     * would reach the tools. Moving the endpoint is therefore a startup error rather than a silent downgrade.
     */
    private void requirePinnedTransportEndpoint() {
        String configured = environment.getProperty(McpEndpoints.ENDPOINT_PROPERTY, McpEndpoints.MCP);
        if (!McpEndpoints.MCP.equals(configured)) {
            throw new IllegalStateException(McpEndpoints.ENDPOINT_PROPERTY + " must be '" + McpEndpoints.MCP
                    + "'; the MCP resource server chain only protects that path");
        }
    }

    /** A SPA that calls another origin than the MCP issuer loops forever in the connect flow. */
    private static void requireSameOriginAsSpa(String backendUrl, String spaApiBaseUrl) {
        if (spaApiBaseUrl == null || spaApiBaseUrl.isBlank()) {
            return;
        }
        String spaOrigin;
        try {
            spaOrigin = McpUrls.origin(spaApiBaseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("VITE_API_BASE_URL (app.mcp.spa-api-base-url) is not a valid URL", e);
        }
        if (!spaOrigin.equals(McpUrls.origin(backendUrl))) {
            throw new IllegalStateException("VITE_API_BASE_URL (" + spaApiBaseUrl + ") and BACKEND_URL ("
                    + backendUrl + ") must have the same origin (scheme, host, port) when MCP is enabled");
        }
    }

    /**
     * The backend URL is the issuer and the base of every advertised metadata URL, so it has to be an absolute
     * http(s) URL with a host and without query or fragment. Anything else would silently produce broken
     * metadata instead of failing here.
     */
    private static void requireUsableBaseUrl(String backendUrl) {
        URI uri;
        try {
            uri = new URI(backendUrl);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("app.mcp.backend-url is not a valid URL", e);
        }
        boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        if (!uri.isAbsolute() || !httpScheme || uri.getHost() == null || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException(
                    "app.mcp.backend-url must be an absolute http(s) URL with a host and no query or fragment");
        }
    }
}
