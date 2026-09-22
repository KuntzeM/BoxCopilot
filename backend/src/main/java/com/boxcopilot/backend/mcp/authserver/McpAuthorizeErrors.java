package com.boxcopilot.backend.mcp.authserver;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;

/**
 * Builds the failures of the authorize endpoint. Every rejection this class produces carries no authentication
 * token, which makes Spring Authorization Server answer with a plain {@code 400} instead of redirecting the
 * error to the client: a request that failed validation must not be able to steer the browser to a URL, and the
 * client must not learn anything about the logged-in user.
 */
final class McpAuthorizeErrors {

    private McpAuthorizeErrors() {
    }

    static OAuth2AuthorizationCodeRequestAuthenticationException reject(String errorCode, String description) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error(errorCode, description, null),
                null);
    }
}
