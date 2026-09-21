package com.boxcopilot.backend.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * MCP feature configuration.
 *
 * @param enabled          master switch; without it no MCP bean exists
 * @param backendUrl       public base URL of this backend (the OAuth issuer and resource identifier); a single
 *                         trailing slash is removed so that paths can simply be appended
 * @param redirectUris     redirect URIs the MCP client is allowed to use
 * @param maxConnectionAge absolute lifetime of an MCP connection, counted from its first authorization; a
 *                         sliding refresh chain cannot outlive it
 */
@ConfigurationProperties(prefix = "app.mcp")
public record McpProperties(boolean enabled, String backendUrl, List<String> redirectUris,
                            Duration maxConnectionAge) {

    /** 14 days, as specified; kept in sync with the value in {@code application.yml}. */
    public static final Duration DEFAULT_MAX_CONNECTION_AGE = Duration.ofDays(14);

    public McpProperties {
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
        backendUrl = stripTrailingSlash(backendUrl);
        maxConnectionAge = maxConnectionAge == null ? DEFAULT_MAX_CONNECTION_AGE : maxConnectionAge;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || !url.endsWith("/")) {
            return url;
        }
        return url.substring(0, url.length() - 1);
    }
}
