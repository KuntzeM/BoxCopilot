package com.boxcopilot.backend.mcp.authserver;

import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.function.Consumer;

/**
 * Validates {@code redirect_uri} by exact string match against the registered URIs — for every host, including
 * loopback addresses.
 *
 * <p>This replaces Spring Authorization Server's default redirect validator, which implements the RFC 8252 port
 * flexibility and accepts <em>any</em> port for {@code 127.0.0.0/8} and {@code ::1}. BoxPilot registers a fixed
 * loopback port for Claude Code, so that flexibility would widen the set of accepted callback URLs without any
 * need. An unexpected port is a rejection here.
 */
final class McpRedirectUriValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        RegisteredClient registeredClient = context.getRegisteredClient();
        OAuth2AuthorizationCodeRequestAuthenticationToken authentication = context.getAuthentication();
        String requestedRedirectUri = authentication.getRedirectUri();

        if (!StringUtils.hasText(requestedRedirectUri)) {
            // Omitting redirect_uri is only unambiguous when the client has exactly one registered URI, and
            // OpenID Connect requires it outright. The second half is unreachable today, because openid is
            // never a registered scope, but it is kept so that the rule survives a future change.
            if (registeredClient.getRedirectUris().size() != 1
                    || authentication.getScopes().contains(OidcScopes.OPENID)) {
                throw invalidRedirectUri();
            }
            return;
        }
        if (hasFragment(requestedRedirectUri) || !registeredClient.getRedirectUris().contains(requestedRedirectUri)) {
            throw invalidRedirectUri();
        }
    }

    private static boolean hasFragment(String redirectUri) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(redirectUri).build();
            return components.getFragment() != null;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static RuntimeException invalidRedirectUri() {
        return McpAuthorizeErrors.reject(OAuth2ErrorCodes.INVALID_REQUEST,
                "OAuth 2.0 Parameter: " + OAuth2ParameterNames.REDIRECT_URI);
    }
}
