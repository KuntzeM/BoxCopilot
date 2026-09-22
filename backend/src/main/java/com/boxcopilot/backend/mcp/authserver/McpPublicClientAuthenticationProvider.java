package com.boxcopilot.backend.mcp.authserver;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Map;

/**
 * Authenticates a public client for a refresh-token or revocation request by its client id.
 *
 * <p>It runs before Spring Authorization Server's {@code PublicClientAuthenticationProvider} and repeats the
 * decision of {@link McpPublicClientRequests} on the authentication token rather than trusting that only the
 * matching converter produced it: anything that carries a {@code code_verifier}, or any other grant, is passed on
 * so that the PKCE proof is still demanded where it belongs.
 *
 * @see McpPublicClientRequests
 */
final class McpPublicClientAuthenticationProvider implements AuthenticationProvider {

    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-3.2.1";

    private final RegisteredClientRepository registeredClientRepository;

    McpPublicClientAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuthentication = (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(clientAuthentication.getClientAuthenticationMethod())
                || clientAuthentication.getCredentials() != null
                || !isClientIdOnlyRequest(clientAuthentication.getAdditionalParameters())) {
            return null;
        }
        RegisteredClient registeredClient = this.registeredClientRepository
                .findByClientId(String.valueOf(clientAuthentication.getPrincipal()));
        if (registeredClient == null
                || !registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT,
                    "Client authentication failed: " + OAuth2ParameterNames.CLIENT_ID, ERROR_URI));
        }
        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static boolean isClientIdOnlyRequest(Map<String, Object> parameters) {
        // A revocation request is the one that brings a token without a grant type; the converter has already
        // established that it really went to the revocation endpoint.
        return new McpPublicClientRequests(asString(parameters.get(OAuth2ParameterNames.GRANT_TYPE)),
                parameters.containsKey(PkceParameterNames.CODE_VERIFIER),
                asString(parameters.get(OAuth2ParameterNames.REFRESH_TOKEN)),
                asString(parameters.get(OAuth2ParameterNames.TOKEN)), true).isAuthenticatedByClientId();
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
