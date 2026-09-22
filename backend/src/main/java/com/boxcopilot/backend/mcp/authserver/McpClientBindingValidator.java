package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.function.Consumer;

/**
 * Enforces that a client may only ever be authorized by the one user it is bound to.
 *
 * <p>The check runs <em>only</em> for an already authenticated principal. Spring Authorization Server calls the
 * validator before it looks at whether the resource owner is logged in, so rejecting an anonymous request here
 * would mean no one could ever reach the login: an anonymous authorize request has to fall through to the
 * authentication entry point and is validated again on the replay after the login.
 *
 * <p>The principal name is the internal user id, because {@code McpAuthenticationNormalizationFilter} has already
 * replaced the session authentication for this request. That filter is also the single authoritative place where
 * the user is loaded from the database and checked against {@code McpUserGate}: it covers the authorize request
 * and the consent POST alike, whereas this validator only ever sees the authorize request. Repeating the gate
 * here would cost a second database round trip per request and would still leave the consent POST relying on the
 * filter, so the state of the user is deliberately not re-checked in this class.
 */
final class McpClientBindingValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    private static final Logger log = LoggerFactory.getLogger(McpClientBindingValidator.class);

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken authentication = context.getAuthentication();
        if (authentication.getScopes().contains(OidcScopes.OPENID)) {
            // OpenID Connect is switched off; without this the displaced default validator's restriction would
            // be lost together with it.
            throw McpAuthorizeErrors.reject(OAuth2ErrorCodes.INVALID_SCOPE,
                    "OpenID Connect authentication requests are not supported.");
        }
        Authentication principal = (Authentication) authentication.getPrincipal();
        if (!McpSessionUserResolver.isAuthenticated(principal)) {
            return;
        }
        requireClientBoundTo(context.getRegisteredClient(), parseUserId(principal.getName()));
    }

    private static long parseUserId(String principalName) {
        try {
            return Long.parseLong(principalName);
        } catch (NumberFormatException e) {
            log.debug("MCP authorize denied: reason=PRINCIPAL_NOT_A_USER_ID");
            throw accessDenied();
        }
    }

    private static void requireClientBoundTo(RegisteredClient registeredClient, long userId) {
        Object boundUserId = registeredClient.getClientSettings().getSetting(McpOAuth2Attributes.CLIENT_USER_ID);
        if (!(boundUserId instanceof String bound) || !bound.equals(String.valueOf(userId))) {
            log.debug("MCP authorize denied: userId={} reason=CLIENT_BINDING", userId);
            throw accessDenied();
        }
    }

    /** One indistinguishable answer for every reason, so that nothing about the user or the client leaks. */
    private static RuntimeException accessDenied() {
        return McpAuthorizeErrors.reject(OAuth2ErrorCodes.ACCESS_DENIED, "The authorization request was denied.");
    }
}
