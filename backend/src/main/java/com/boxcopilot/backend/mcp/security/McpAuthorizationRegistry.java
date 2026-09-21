package com.boxcopilot.backend.mcp.security;

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
}
