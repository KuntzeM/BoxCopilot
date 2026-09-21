package com.boxcopilot.backend.mcp.security;

/**
 * The {@code WWW-Authenticate} challenges of the MCP resource server (RFC 6750 / RFC 9728). They point the
 * client at the protected-resource metadata so it can discover the authorization server on its own.
 *
 * @param resourceMetadataUrl absolute URL of this resource's metadata document
 */
record McpBearerChallenge(String resourceMetadataUrl) {

    private static final String BASE = "Bearer resource_metadata=\"%s\", scope=\"%s\"";

    /** Sent with 401: no token, an unknown token, or a token that is no longer valid. */
    String unauthorized() {
        return BASE.formatted(resourceMetadataUrl, McpOAuth2Attributes.SCOPE_READ);
    }

    /** Sent with 403: the token is valid but does not carry the required scope. */
    String insufficientScope() {
        return unauthorized() + ", error=\"insufficient_scope\"";
    }
}
