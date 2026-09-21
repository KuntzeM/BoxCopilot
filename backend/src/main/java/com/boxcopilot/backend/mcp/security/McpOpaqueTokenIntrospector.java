package com.boxcopilot.backend.mcp.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves an opaque bearer token against the local authorization store. The lookup is typed to
 * {@link OAuth2TokenType#ACCESS_TOKEN}, so a refresh token or an authorization code presented as a bearer token
 * is simply not found. The store is the policy-aware decorator, so a withdrawn user is rejected here as well.
 *
 * <p>The principal carries the internal user id as its name and no authority but {@code SCOPE_<scope>} for the
 * authorized scopes; application roles deliberately do not travel in a token.
 */
public class McpOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private static final String INVALID_TOKEN = "The presented token is not a valid MCP access token";

    private final OAuth2AuthorizationService authorizationService;

    public McpOpaqueTokenIntrospector(OAuth2AuthorizationService authorizationService) {
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        this.authorizationService = authorizationService;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            throw new BadOpaqueTokenException(INVALID_TOKEN);
        }
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken == null || !accessToken.isActive()) {
            throw new BadOpaqueTokenException(INVALID_TOKEN);
        }
        Set<String> scopes = authorization.getAuthorizedScopes();
        return new DefaultOAuth2AuthenticatedPrincipal(authorization.getPrincipalName(),
                attributes(authorization.getPrincipalName(), accessToken.getToken(), scopes), authorities(scopes));
    }

    /** The minimum a resource server needs: who, what for, and how long. No client id, no token value. */
    private static Map<String, Object> attributes(String subject, OAuth2AccessToken accessToken,
                                                  Set<String> scopes) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sub", subject);
        attributes.put("scope", String.join(" ", scopes));
        putIfPresent(attributes, "iat", accessToken.getIssuedAt());
        putIfPresent(attributes, "exp", accessToken.getExpiresAt());
        return Collections.unmodifiableMap(attributes);
    }

    private static void putIfPresent(Map<String, Object> attributes, String name, Instant value) {
        if (value != null) {
            attributes.put(name, value);
        }
    }

    private static List<GrantedAuthority> authorities(Set<String> scopes) {
        return scopes.stream()
                .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();
    }
}
