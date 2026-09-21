package com.boxcopilot.backend.mcp.security;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.McpAccessDeniedException;
import com.boxcopilot.backend.mcp.McpProperties;
import com.boxcopilot.backend.mcp.McpUserGate;
import com.boxcopilot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The single place that decides whether MCP access is currently allowed. It is fail-closed: every call reloads
 * the user from the database, there is no cache, and anything unexpected denies.
 *
 * <p>Policy denials are logged at DEBUG with the user id and a reason code. A denial caused by infrastructure
 * ({@link McpDenyReason#ERROR}) is logged at WARN, because it hides a real fault behind a 401; even then only
 * the user id, the reason and the exception class are written. Tokens, token values and client ids never
 * appear in a log line.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpAccessPolicy implements McpUserGate, McpAuthorizationPolicy {

    private static final Logger log = LoggerFactory.getLogger(McpAccessPolicy.class);

    private final UserRepository userRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final Duration maxConnectionAge;
    private final Clock clock;

    public McpAccessPolicy(UserRepository userRepository, RegisteredClientRepository registeredClientRepository,
                           McpProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.registeredClientRepository = registeredClientRepository;
        this.maxConnectionAge = properties.maxConnectionAge();
        this.clock = clock;
    }

    /**
     * Decides whether an authorization may still be used. Besides the state of the user it verifies that the
     * client is bound to that same user and that the connection start lies in a plausible past window.
     */
    @Override
    public boolean checkAuthorization(OAuth2Authorization authorization) {
        if (authorization == null) {
            return deny(null, McpDenyReason.NO_AUTHORIZATION);
        }
        try {
            Optional<Long> userId = parseUserId(authorization.getPrincipalName());
            if (userId.isEmpty()) {
                return deny(null, McpDenyReason.PRINCIPAL_NOT_A_USER_ID);
            }
            Optional<McpDenyReason> reason = checkConnection(authorization, userId.get())
                    .or(() -> checkUser(userId.get()));
            if (reason.isPresent()) {
                return deny(userId.get(), reason.get());
            }
            return true;
        } catch (RuntimeException e) {
            return denyBecauseOfError(null, e);
        }
    }

    @Override
    public void requireActiveUser(long userId) {
        Optional<McpDenyReason> reason;
        try {
            reason = checkUser(userId);
        } catch (RuntimeException e) {
            denyBecauseOfError(userId, e);
            throw new McpAccessDeniedException("MCP access denied: " + McpDenyReason.ERROR);
        }
        if (reason.isPresent()) {
            deny(userId, reason.get());
            throw new McpAccessDeniedException("MCP access denied: " + reason.get());
        }
    }

    /** @return the reason this connection may not be used, or empty if it may */
    private Optional<McpDenyReason> checkConnection(OAuth2Authorization authorization, long userId) {
        if (!isBoundTo(authorization.getRegisteredClientId(), userId)) {
            return Optional.of(McpDenyReason.CLIENT_BINDING);
        }
        Optional<Instant> startedAt = startedAt(authorization);
        if (startedAt.isEmpty()) {
            return Optional.of(McpDenyReason.NO_START_TIMESTAMP);
        }
        Instant now = clock.instant();
        if (startedAt.get().isAfter(now.plus(MAX_CLOCK_SKEW))) {
            return Optional.of(McpDenyReason.START_TIMESTAMP_IN_FUTURE);
        }
        if (startedAt.get().plus(maxConnectionAge).isBefore(now)) {
            return Optional.of(McpDenyReason.CONNECTION_TOO_OLD);
        }
        return Optional.empty();
    }

    /** @return the reason the user may not use MCP, or empty if the user may */
    private Optional<McpDenyReason> checkUser(long userId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return Optional.of(McpDenyReason.UNKNOWN_USER);
        }
        User user = found.get();
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return Optional.of(McpDenyReason.USER_DISABLED);
        }
        if (user.isAccountLocked()) {
            return Optional.of(McpDenyReason.USER_LOCKED);
        }
        if (!user.isMcpAllowed()) {
            return Optional.of(McpDenyReason.MCP_NOT_ALLOWED);
        }
        return Optional.empty();
    }

    private boolean isBoundTo(String registeredClientId, long userId) {
        if (registeredClientId == null) {
            return false;
        }
        RegisteredClient client = registeredClientRepository.findById(registeredClientId);
        if (client == null) {
            return false;
        }
        Object boundUserId = client.getClientSettings().getSetting(McpOAuth2Attributes.CLIENT_USER_ID);
        return boundUserId instanceof String bound && bound.equals(String.valueOf(userId));
    }

    /** Reads the start timestamp, which the authorization store writes as a decimal string of epoch seconds. */
    private static Optional<Instant> startedAt(OAuth2Authorization authorization) {
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

    private static Optional<Long> parseUserId(String principalName) {
        if (principalName == null) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(principalName);
            return userId > 0 ? Optional.of(userId) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean deny(Long userId, McpDenyReason reason) {
        log.debug("MCP access denied: userId={} reason={}", userId, reason);
        return false;
    }

    private static boolean denyBecauseOfError(Long userId, RuntimeException cause) {
        log.warn("MCP access denied: userId={} reason={} cause={}", userId, McpDenyReason.ERROR,
                cause.getClass().getName());
        return false;
    }
}
