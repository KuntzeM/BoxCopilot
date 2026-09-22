package com.boxcopilot.backend.mcp.authserver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.Set;

/**
 * Supplies the single BoxPilot scope when an authorization request carries no {@code scope} parameter.
 *
 * <p>A client that asks for nothing would otherwise be authorized for nothing and receive an access token without
 * {@code SCOPE_mcp:read}, which the resource server then rejects — a silent dead end. Since there is exactly one
 * scope, defaulting to it is unambiguous.
 *
 * <p>A {@code resource} parameter is neither required nor evaluated: it simply stays in the additional parameters
 * and is ignored, because there is only one resource server and an opaque token is valid nowhere else.
 */
final class McpDefaultScopeConverter implements AuthenticationConverter {

    private final AuthenticationConverter delegate = new OAuth2AuthorizationCodeRequestAuthenticationConverter();
    private final String defaultScope;

    McpDefaultScopeConverter(String defaultScope) {
        this.defaultScope = defaultScope;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        Authentication authentication = this.delegate.convert(request);
        if (!(authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken codeRequest)
                || !codeRequest.getScopes().isEmpty()) {
            return authentication;
        }
        return new OAuth2AuthorizationCodeRequestAuthenticationToken(codeRequest.getAuthorizationUri(),
                codeRequest.getClientId(), (Authentication) codeRequest.getPrincipal(), codeRequest.getRedirectUri(),
                codeRequest.getState(), Set.of(this.defaultScope), codeRequest.getAdditionalParameters());
    }
}
