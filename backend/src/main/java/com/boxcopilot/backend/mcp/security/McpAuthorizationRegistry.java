package com.boxcopilot.backend.mcp.security;

import java.time.Duration;
import java.time.Instant;

/**
 * Bulk removal of stored authorizations, which {@code OAuth2AuthorizationService} does not offer. Revocation
 * (disabling or deleting a user, withdrawing MCP access) uses it to end every connection of that user at once.
 */
public interface McpAuthorizationRegistry {

    /**
     * Deletes every stored authorization of one principal, whatever its state.
     *
     * @param principalName the internal user id as a string, as stored in the authorization
     * @return the number of deleted authorizations
     */
    int removeByPrincipal(String principalName);

    /**
     * Deletes the authorizations that can never be used again. Spring Authorization Server never removes a row
     * itself, so without this the table only ever grows.
     *
     * <p>An authorization goes when its refresh token has expired, or - if it has no refresh token - when its
     * access token has expired, or when the connection started longer ago than the absolute age allows, or when
     * it is nothing but an authorization code that expired without ever being redeemed.
     *
     * @param now              the moment expiry is judged against
     * @param maxConnectionAge the absolute lifetime of a connection
     * @return the number of deleted authorizations
     */
    int deleteExpired(Instant now, Duration maxConnectionAge);
}
