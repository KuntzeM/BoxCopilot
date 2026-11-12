package com.boxcopilot.backend.mcp.authserver;

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.Base64;

/**
 * Issues refresh tokens to public clients as well.
 *
 * <p>Spring Authorization Server's {@code OAuth2RefreshTokenGenerator} refuses a refresh token whenever the
 * authorization-code grant was authenticated as a public client, following the older OAuth 2.0 security best
 * practice. OAuth 2.1 (section 4.3.1) allows it for a public client when the refresh token is either
 * sender-constrained or rotated, and BoxPilot does more than rotate: {@code reuseRefreshTokens(false)} invalidates
 * the previous token on every refresh, the whole connection dies after {@code app.mcp.max-connection-age}, and
 * {@code McpAccessPolicy} re-checks the user and the client binding on every single use.
 *
 * <p>Without this, a Claude connection would have to send the user through login and consent every thirty
 * minutes, which the specification rules out.
 *
 * <p>Apart from dropping that one rule, the token is generated exactly as the stock implementation does it: 96
 * random bytes, Base64url encoded, with the client's configured refresh-token lifetime.
 */
final class McpRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final StringKeyGenerator refreshTokenGenerator =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings()
                .getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(this.refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
    }
}
