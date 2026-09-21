package com.boxcopilot.backend.mcp.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Decorates the JDBC authorization store so that every read is subject to {@link McpAuthorizationPolicy}: a
 * rejected authorization is reported as absent, which makes the access token, the refresh token and the
 * authorization code alike stop working the moment access is withdrawn.
 *
 * <p>On its first save an authorization is stamped with {@link McpOAuth2Attributes#STARTED_AT}. Spring
 * Authorization Server copies attributes into the rotated authorization, so the stamp survives the sliding
 * refresh chain and gives the connection an absolute lifetime. An existing stamp is never moved forward, with
 * one exception: a stamp further in the future than {@link McpAuthorizationPolicy#MAX_CLOCK_SKEW} would extend
 * the absolute lifetime indefinitely, so it is replaced by the current time.
 */
public class McpAuthorizationService implements OAuth2AuthorizationService, McpAuthorizationRegistry {

    static final String DELETE_BY_PRINCIPAL = "DELETE FROM oauth2_authorization WHERE principal_name = ?";

    private final OAuth2AuthorizationService delegate;
    private final McpAuthorizationPolicy policy;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public McpAuthorizationService(OAuth2AuthorizationService delegate, McpAuthorizationPolicy policy,
                                   JdbcTemplate jdbcTemplate, Clock clock) {
        Assert.notNull(delegate, "delegate cannot be null");
        Assert.notNull(policy, "policy cannot be null");
        Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
        Assert.notNull(clock, "clock cannot be null");
        this.delegate = delegate;
        this.policy = policy;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
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

    private OAuth2Authorization stampConnectionStart(OAuth2Authorization authorization) {
        Instant now = clock.instant();
        Optional<Instant> existing = readStartedAt(authorization);
        if (existing.isPresent() && !existing.get().isAfter(now.plus(McpAuthorizationPolicy.MAX_CLOCK_SKEW))) {
            return authorization;
        }
        if (existing.isEmpty() && authorization.getAttribute(McpOAuth2Attributes.STARTED_AT) != null) {
            // An unreadable stamp stays as it is; the policy rejects it instead of it being silently repaired.
            return authorization;
        }
        return OAuth2Authorization.from(authorization)
                .attribute(McpOAuth2Attributes.STARTED_AT, String.valueOf(now.getEpochSecond()))
                .build();
    }

    private static Optional<Instant> readStartedAt(OAuth2Authorization authorization) {
        Object value = authorization.getAttribute(McpOAuth2Attributes.STARTED_AT);
        if (!(value instanceof String epochSecond)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(epochSecond)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
