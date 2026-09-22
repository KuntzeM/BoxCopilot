package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.authserver.McpRegisteredClients;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Builds per-user public clients exactly as the connection provisioning of story S9 will: a random client id,
 * bound to one user through the {@code boxpilot.user_id} client setting, {@code client_authentication_method=none},
 * only the authorization-code and refresh-token grants, PKCE and consent required, the single {@code mcp:read}
 * scope and the production token settings.
 *
 * <p>Client and token settings come from {@link McpRegisteredClients}, so a fixture can never drift away from
 * what the application registers.
 */
public final class RegisteredClientFixtures {

    /** Well above the 128 bits the specification requires for a client id. */
    private static final int CLIENT_ID_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RegisteredClientFixtures() {
    }

    public static RegisteredClient.Builder publicClient(long boundUserId, List<String> redirectUris) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(randomClientId())
                .clientName("Claude connection")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(McpOAuth2Attributes.SCOPE_READ)
                .clientSettings(McpRegisteredClients.clientSettings(boundUserId))
                .tokenSettings(McpRegisteredClients.tokenSettings());
        redirectUris.forEach(builder::redirectUri);
        return builder;
    }

    /**
     * A client id as the specification demands it: 256 bits straight from {@code SecureRandom}, Base64url
     * encoded. Story S9 registers clients the same way; deriving the id from a {@code UUID} would look similar
     * but carry only 122 bits.
     */
    public static String randomClientId() {
        byte[] randomBytes = new byte[CLIENT_ID_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /** @return the S256 code challenge belonging to a PKCE code verifier */
    public static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM specification", e);
        }
    }
}
