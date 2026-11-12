package com.boxcopilot.backend.mcp.authserver;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Decides which requests of a public client BoxPilot authenticates by client id alone.
 *
 * <p>Spring Authorization Server authenticates a public client only for the authorization-code token request,
 * where the {@code code_verifier} is the proof. The refresh-token grant and token revocation carry no such proof
 * and are therefore left unauthenticated, which makes both fail with {@code invalid_client} for a client
 * registered with {@code client_authentication_method=none}. BoxPilot accepts the client id for exactly those two
 * requests; both are bound to a stored authorization of that very client, so the client id is an identifier here
 * and never a credential.
 *
 * <p>The same decision is taken by the converter and again by the provider, so a request that carries a
 * {@code code_verifier} can never be answered by anything but Spring Authorization Server's own PKCE
 * authentication.
 *
 * @param grantType       the {@code grant_type} parameter, or {@code null} when absent
 * @param codeVerifier    whether a {@code code_verifier} is present, whatever its value
 * @param refreshToken    the {@code refresh_token} parameter, or {@code null} when absent
 * @param token           the {@code token} parameter of a revocation request, or {@code null} when absent
 * @param revocation      whether this decision is taken about a revocation request
 */
record McpPublicClientRequests(String grantType, boolean codeVerifier, String refreshToken, String token,
                               boolean revocation) {

    /**
     * Name under which the converter records whether the request reached the revocation endpoint, so that the
     * provider reads that fact instead of assuming it. The converter strips any incoming parameter of this name
     * before setting it, so a client cannot supply it.
     */
    static final String REVOCATION_MARKER = "boxpilot.revocation_request";

    boolean isAuthenticatedByClientId() {
        if (this.codeVerifier) {
            return false;
        }
        if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(this.grantType)) {
            return hasText(this.refreshToken);
        }
        return this.revocation && this.grantType == null && hasText(this.token);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
