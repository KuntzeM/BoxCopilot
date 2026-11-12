package com.boxcopilot.backend.mcp;

import java.util.List;

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

    /** Where the Spring AI starter reads the transport protocol from; only {@link #STATELESS_PROTOCOL} is allowed. */
    public static final String PROTOCOL_PROPERTY = "spring.ai.mcp.server.protocol";

    /** The only transport protocol the MCP feature supports: no server-side session, one request per call. */
    public static final String STATELESS_PROTOCOL = "STATELESS";

    /**
     * Properties that only take effect for the session-bound SSE transport. Configuring any of them means a
     * session transport was intended, which would publish endpoints outside the resource server chain.
     */
    public static final List<String> SESSION_TRANSPORT_PROPERTIES =
            List.of("spring.ai.mcp.server.sse-endpoint", "spring.ai.mcp.server.sse-message-endpoint",
                    "spring.ai.mcp.server.base-url");

    /** Resumes an authorization that was interrupted by the login; lives in the application chain. */
    public static final String CONTINUE_PENDING_AUTHORIZATION = "/api/v1/mcp/continue";

    private McpEndpoints() {
    }
}
