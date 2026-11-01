package com.boxcopilot.backend.mcp.security;

import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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

    static final String DELETE_BY_ID = "DELETE FROM oauth2_authorization WHERE id = ?";

    /**
     * Everything the cleanup has to judge a row by. The decision is made in Java rather than in SQL because the
     * connection start lives inside the serialised {@code attributes} column, which H2 and Postgres would have
     * to be asked about in two different dialects; the table holds one row per connection, so reading it is
     * cheap once a day.
     *
     * <p>The three token columns hold live secrets in clear text, and every rule only asks whether they are
     * present. They are therefore reduced to a flag in the query and never enter the process: nothing can put
     * them into a log line, an exception message or a generated {@code toString}.
     */
    static final String SELECT_FOR_CLEANUP = """
            SELECT id, attributes,
                   CASE WHEN access_token_value IS NULL THEN 0 ELSE 1 END AS has_access_token,
                   access_token_expires_at,
                   CASE WHEN refresh_token_value IS NULL THEN 0 ELSE 1 END AS has_refresh_token,
                   refresh_token_expires_at,
                   CASE WHEN authorization_code_value IS NULL THEN 0 ELSE 1 END AS has_authorization_code,
                   authorization_code_expires_at
            FROM oauth2_authorization""";

    /** Reads one plain scalar field out of the stored attributes; no typed value is deserialised here. */
    private static final ObjectMapper ATTRIBUTE_READER = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(McpAuthorizationService.class);

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

    @Override
    public int deleteExpired(Instant now, Duration maxConnectionAge) {
        Assert.notNull(now, "now cannot be null");
        Assert.notNull(maxConnectionAge, "maxConnectionAge cannot be null");
        Instant earliestAllowedStart = now.minus(maxConnectionAge);
        List<Object[]> doomed = jdbcTemplate.query(SELECT_FOR_CLEANUP, McpAuthorizationService::readCleanupRow)
                .stream()
                .filter(row -> row.isSpent(now, earliestAllowedStart))
                .map(row -> new Object[] {row.id()})
                .toList();
        if (doomed.isEmpty()) {
            return 0;
        }
        return Arrays.stream(jdbcTemplate.batchUpdate(DELETE_BY_ID, doomed)).sum();
    }

    private static CleanupRow readCleanupRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CleanupRow(resultSet.getString("id"),
                readStartedAt(resultSet.getString("attributes")),
                resultSet.getBoolean("has_access_token"),
                instant(resultSet, "access_token_expires_at"),
                resultSet.getBoolean("has_refresh_token"),
                instant(resultSet, "refresh_token_expires_at"),
                resultSet.getBoolean("has_authorization_code"),
                instant(resultSet, "authorization_code_expires_at"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * Reads the connection start out of the stored attributes. An attribute set that cannot be read yields no
     * start: the policy already refuses such an authorization, and the cleanup deletes only what it can judge.
     *
     * <p>The stamp is written as a decimal string today, but the specification describes it as a {@code Long}
     * and the storage type has changed once already. A number is therefore accepted just as readily, and a
     * stamp of any other shape is reported at warning level rather than quietly turning the absolute-age rule
     * off for every connection.
     */
    private static Instant readStartedAt(String attributes) {
        if (attributes == null || attributes.isBlank()) {
            return null;
        }
        try {
            JsonNode startedAt = ATTRIBUTE_READER.readTree(attributes).get(McpOAuth2Attributes.STARTED_AT);
            if (startedAt == null || startedAt.isNull()) {
                return null;
            }
            if (!startedAt.isTextual() && !startedAt.isNumber()) {
                log.warn("MCP cleanup cannot read {}: unexpected JSON node type {}",
                        McpOAuth2Attributes.STARTED_AT, startedAt.getNodeType());
                return null;
            }
            return Instant.ofEpochSecond(Long.parseLong(startedAt.asText()));
        } catch (JsonProcessingException | NumberFormatException | ArithmeticException e) {
            log.warn("MCP cleanup cannot read {}: {}", McpOAuth2Attributes.STARTED_AT,
                    e.getClass().getSimpleName());
            return null;
        }
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

    /**
     * One stored authorization, reduced to what decides whether it can ever be used again.
     *
     * @param startedAt when the connection was first authorized, or {@code null} if that is unreadable
     */
    private record CleanupRow(String id, Instant startedAt, boolean hasAccessToken, Instant accessTokenExpiresAt,
                              boolean hasRefreshToken, Instant refreshTokenExpiresAt, boolean hasAuthorizationCode,
                              Instant codeExpiresAt) {

        boolean isSpent(Instant now, Instant earliestAllowedStart) {
            return refreshTokenExpired(now) || accessTokenExpiredWithoutRefreshToken(now)
                    || startedTooLongAgo(earliestAllowedStart) || codeExpiredUnused(now);
        }

        private boolean refreshTokenExpired(Instant now) {
            return hasRefreshToken && refreshTokenExpiresAt != null && refreshTokenExpiresAt.isBefore(now);
        }

        private boolean accessTokenExpiredWithoutRefreshToken(Instant now) {
            return !hasRefreshToken && accessTokenExpiresAt != null && accessTokenExpiresAt.isBefore(now);
        }

        private boolean startedTooLongAgo(Instant earliestAllowedStart) {
            return startedAt != null && startedAt.isBefore(earliestAllowedStart);
        }

        /** A code that expired before it was exchanged; the authorization never became a connection. */
        private boolean codeExpiredUnused(Instant now) {
            return hasAuthorizationCode && codeExpiresAt != null && codeExpiresAt.isBefore(now)
                    && !hasAccessToken;
        }
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
