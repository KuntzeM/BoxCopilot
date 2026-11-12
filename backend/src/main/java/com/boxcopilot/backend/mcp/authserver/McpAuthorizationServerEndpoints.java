package com.boxcopilot.backend.mcp.authserver;

/**
 * The only OAuth2 endpoints BoxPilot publishes. The authorization server chain matches exactly this set; every
 * other endpoint Spring Authorization Server knows (introspection, device authorization, pushed authorization
 * requests, JWKS, OpenID Connect discovery and client registration) stays outside the matcher and is therefore
 * served by no authorization server filter at all.
 */
public final class McpAuthorizationServerEndpoints {

    /** Authorization endpoint (RFC 6749 4.1.1); also receives the consent POST. */
    public static final String AUTHORIZE = "/oauth2/authorize";

    /** BoxPilot's own consent page, rendered by {@code McpConsentController}. */
    public static final String CONSENT = "/oauth2/consent";

    /** Token endpoint (RFC 6749 4.1.3, 6). */
    public static final String TOKEN = "/oauth2/token";

    /** Token revocation endpoint (RFC 7009). */
    public static final String REVOKE = "/oauth2/revoke";

    /** Authorization server metadata (RFC 8414); the issuer has no path component, so there is no suffix form. */
    public static final String METADATA = "/.well-known/oauth-authorization-server";

    private McpAuthorizationServerEndpoints() {
    }
}
