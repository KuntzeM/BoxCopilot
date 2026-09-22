package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.authserver.McpClientIdGenerator;
import com.boxcopilot.backend.mcp.authserver.McpRegisteredClients;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * Builds per-user public clients exactly as the connection provisioning does: a random client id, bound to one
 * user through the {@code boxpilot.user_id} client setting, {@code client_authentication_method=none}, only the
 * authorization-code and refresh-token grants, PKCE and consent required, the single {@code mcp:read} scope and
 * the production token settings.
 *
 * <p>Both the client and its id come from the production factory {@link McpRegisteredClients} and the production
 * {@link McpClientIdGenerator}, so a fixture can never drift away from what the application registers.
 */
public final class RegisteredClientFixtures {

    private static final McpClientIdGenerator CLIENT_ID_GENERATOR = new McpClientIdGenerator();

    private RegisteredClientFixtures() {
    }

    public static RegisteredClient publicClient(long boundUserId, List<String> redirectUris) {
        return McpRegisteredClients.publicClient(boundUserId, randomClientId(), redirectUris);
    }

    /**
     * A client id as the specification demands it: 256 bits straight from {@code SecureRandom}, Base64url
     * encoded. Deriving the id from a {@code UUID} would look similar but carry only 122 bits.
     */
    public static String randomClientId() {
        return CLIENT_ID_GENERATOR.newClientId();
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
