package com.boxcopilot.backend.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * MCP feature configuration.
 *
 * @param enabled      master switch; without it no MCP bean exists
 * @param backendUrl   public base URL of this backend (the OAuth issuer and resource identifier)
 * @param redirectUris redirect URIs the MCP client is allowed to use
 */
@ConfigurationProperties(prefix = "app.mcp")
public record McpProperties(boolean enabled, String backendUrl, List<String> redirectUris) {

    public McpProperties {
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
    }
}
