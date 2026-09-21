package com.boxcopilot.backend.mcp.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.Assert;

import java.time.Instant;

/**
 * Decorates the JDBC authorization store so that every read is subject to {@link McpAccessPolicy}: a rejected
 * authorization is reported as absent, which makes the access token, the refresh token and the authorization
 * code alike stop working the moment access is withdrawn.
 *
 * <p>On its first save an authorization is stamped with {@link McpOAuth2Attributes#STARTED_AT}. Spring
 * Authorization Server copies attributes into the rotated authorization, so the stamp survives the sliding
 * refresh chain and gives the connection an absolute lifetime.
 */
public class McpAuthorizationService implements OAuth2AuthorizationService, McpAuthorizationRegistry {

    static final String DELETE_BY_PRINCIPAL = "DELETE FROM oauth2_authorization WHERE principal_name = ?";

    private final OAuth2AuthorizationService delegate;
    private final McpAccessPolicy policy;
    private final JdbcTemplate jdbcTemplate;

    public McpAuthorizationService(OAuth2AuthorizationService delegate, McpAccessPolicy policy,
                                   JdbcTemplate jdbcTemplate) {
        Assert.notNull(delegate, "delegate cannot be null");
        Assert.notNull(policy, "policy cannot be null");
        Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
        this.delegate = delegate;
        this.policy = policy;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(stampConnectionStart(authorization));
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return allowed(delegate.findById(id));
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return allowed(delegate.findByToken(token, tokenType));
    }

    @Override
    public int removeByPrincipal(String principalName) {
        Assert.hasText(principalName, "principalName cannot be empty");
        return jdbcTemplate.update(DELETE_BY_PRINCIPAL, principalName);
    }

    private OAuth2Authorization allowed(OAuth2Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        return policy.checkAuthorization(authorization) ? authorization : null;
    }

    private static OAuth2Authorization stampConnectionStart(OAuth2Authorization authorization) {
        if (authorization.getAttribute(McpOAuth2Attributes.STARTED_AT) != null) {
            return authorization;
        }
        return OAuth2Authorization.from(authorization)
                .attribute(McpOAuth2Attributes.STARTED_AT, String.valueOf(Instant.now().getEpochSecond()))
                .build();
    }
}
