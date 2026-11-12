package com.boxcopilot.backend.mcp.authserver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Creates the public identifier of an MCP client.
 *
 * <p>A public client has no secret, so its id is the only value an attacker would have to guess to start an
 * authorization in someone else's name. It is therefore 256 bits straight from {@link SecureRandom}, Base64url
 * encoded without padding; deriving it from a {@code UUID} would look similar but carry only 122 bits.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpClientIdGenerator {

    /** Well above the 128 bits the specification requires. */
    private static final int CLIENT_ID_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** @return a fresh client id; it is a credential and must never be logged */
    public String newClientId() {
        byte[] randomBytes = new byte[CLIENT_ID_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
