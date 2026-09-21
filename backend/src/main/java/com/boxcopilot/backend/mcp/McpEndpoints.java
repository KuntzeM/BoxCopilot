package com.boxcopilot.backend.mcp;

/**
 * The HTTP paths of the MCP feature. They are constants because two independent places have to agree on them:
 * the Spring AI transport, which publishes the router function, and the resource server chain, whose
 * {@code securityMatcher} must cover exactly that router function. {@link McpStartupGuard} refuses to start if
 * the configured transport endpoint drifts away from {@link #MCP}.
 */
public final class McpEndpoints {

    /** The MCP endpoint, and the value {@link #ENDPOINT_PROPERTY} must have. */
    public static final String MCP = "/mcp";

    /** Everything below the MCP endpoint, so that no sub-path escapes the resource server chain. */
    public static final String MCP_PATTERN = "/mcp/**";

    /** Protected-resource metadata of the MCP endpoint (RFC 9728). */
    public static final String PROTECTED_RESOURCE_METADATA = "/.well-known/oauth-protected-resource/mcp";

    /** The whole protected-resource-metadata namespace, which is readable without a token. */
    public static final String PROTECTED_RESOURCE_METADATA_PATTERN = "/.well-known/oauth-protected-resource/**";

    /** Where the Spring AI starter reads the endpoint of the streamable HTTP transport from. */
    public static final String ENDPOINT_PROPERTY = "spring.ai.mcp.server.streamable-http.mcp-endpoint";

    private McpEndpoints() {
    }
}
