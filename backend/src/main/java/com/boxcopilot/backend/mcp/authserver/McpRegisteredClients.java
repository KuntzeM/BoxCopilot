package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The client and token settings every BoxPilot MCP client is registered with. They are a single source of truth
 * so that client provisioning and the tests cannot drift apart.
 *
 * <p>Every client is a public client bound to exactly one user, requires PKCE and requires consent on every
 * authorization. Tokens are opaque references with a short access-token lifetime and a rotating refresh token;
 * the absolute lifetime of a connection is enforced separately by {@code McpAccessPolicy}.
 */
public final class McpRegisteredClients {

    /** An authorization code is redeemed immediately after the redirect, so a minute is generous. */
    public static final Duration AUTHORIZATION_CODE_TIME_TO_LIVE = Duration.ofSeconds(60);

    /** Access-token lifetime; short, because a refresh costs the client nothing. */
    public static final Duration ACCESS_TOKEN_TIME_TO_LIVE = Duration.ofMinutes(30);

    /** Idle window of a connection: without a refresh within this period the connection dies. */
    public static final Duration REFRESH_TOKEN_TIME_TO_LIVE = Duration.ofDays(7);

    /** What the connection is called wherever a registered client is shown; it holds no user data. */
    public static final String CLIENT_NAME = "Claude connection";

    private McpRegisteredClients() {
    }

    /**
     * Builds the one client a user may hold: a public client with the fixed BoxPilot settings, bound to that user.
     *
     * @param userId       the internal user id the client is bound to
     * @param clientId     the generated public client id
     * @param redirectUris the configured redirect URIs; no other one may be used
     */
    public static RegisteredClient publicClient(long userId, String clientId, List<String> redirectUris) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(CLIENT_NAME)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(McpOAuth2Attributes.SCOPE_READ)
                .clientSettings(clientSettings(userId))
                .tokenSettings(tokenSettings());
        redirectUris.forEach(builder::redirectUri);
        return builder.build();
    }

    /**
     * @param userId the internal user id this client belongs to; it is the only user that may authorize it
     */
    public static ClientSettings clientSettings(long userId) {
        return ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(true)
                .setting(McpOAuth2Attributes.CLIENT_USER_ID, String.valueOf(userId))
                .build();
    }

    public static TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .authorizationCodeTimeToLive(AUTHORIZATION_CODE_TIME_TO_LIVE)
                .accessTokenTimeToLive(ACCESS_TOKEN_TIME_TO_LIVE)
                .refreshTokenTimeToLive(REFRESH_TOKEN_TIME_TO_LIVE)
                .reuseRefreshTokens(false)
                .build();
    }
}
