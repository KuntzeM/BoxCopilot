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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The single place that decides whether MCP access is currently allowed. It is fail-closed: every call reloads
 * the user from the database, there is no cache, and anything unexpected denies.
 *
 * <p>Deny reasons are logged at DEBUG with the user id and a reason code only. Tokens, token values and client
 * ids never appear in a log line.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpAccessPolicy implements McpUserGate {

    private static final Logger log = LoggerFactory.getLogger(McpAccessPolicy.class);

    private final UserRepository userRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final Duration maxConnectionAge;

    public McpAccessPolicy(UserRepository userRepository, RegisteredClientRepository registeredClientRepository,
                           McpProperties properties) {
        this.userRepository = userRepository;
        this.registeredClientRepository = registeredClientRepository;
        this.maxConnectionAge = properties.maxConnectionAge();
    }

    /**
     * Decides whether an authorization may still be used. Besides the state of the user it verifies that the
     * client is bound to that same user and that the absolute connection age is not exceeded.
     *
     * @return {@code true} only if every rule passes
     */
    public boolean checkAuthorization(OAuth2Authorization authorization) {
        if (authorization == null) {
            return deny(null, McpDenyReason.NO_AUTHORIZATION);
        }
        try {
            Long userId = parseUserId(authorization.getPrincipalName());
            if (userId == null) {
                return deny(null, McpDenyReason.PRINCIPAL_NOT_A_USER_ID);
            }
            if (!isBoundTo(authorization.getRegisteredClientId(), userId)) {
                return deny(userId, McpDenyReason.CLIENT_BINDING);
            }
            Instant startedAt = startedAt(authorization);
            if (startedAt == null) {
                return deny(userId, McpDenyReason.NO_START_TIMESTAMP);
            }
            if (startedAt.plus(maxConnectionAge).isBefore(Instant.now())) {
                return deny(userId, McpDenyReason.CONNECTION_TOO_OLD);
            }
            McpDenyReason userReason = checkUser(userId);
            return userReason == null || deny(userId, userReason);
        } catch (RuntimeException e) {
            log.debug("MCP access denied: reason={}", McpDenyReason.ERROR, e);
            return false;
        }
    }

    @Override
    public void requireActiveUser(long userId) {
        McpDenyReason reason;
        try {
            reason = checkUser(userId);
        } catch (RuntimeException e) {
            log.debug("MCP access denied: userId={} reason={}", userId, McpDenyReason.ERROR, e);
            throw new McpAccessDeniedException("MCP access denied: " + McpDenyReason.ERROR);
        }
        if (reason != null) {
            deny(userId, reason);
            throw new McpAccessDeniedException("MCP access denied: " + reason);
        }
    }

    /** @return the reason the user may not use MCP, or {@code null} if the user may */
    private McpDenyReason checkUser(long userId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return McpDenyReason.UNKNOWN_USER;
        }
        User user = found.get();
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return McpDenyReason.USER_DISABLED;
        }
        if (user.isAccountLocked()) {
            return McpDenyReason.USER_LOCKED;
        }
        if (!user.isMcpAllowed()) {
            return McpDenyReason.MCP_NOT_ALLOWED;
        }
        return null;
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

    /** Reads the start timestamp, which the decorator writes as a decimal string of epoch seconds. */
    private static Instant startedAt(OAuth2Authorization authorization) {
        Object value = authorization.getAttribute(McpOAuth2Attributes.STARTED_AT);
        if (!(value instanceof String epochSecond)) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(epochSecond));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseUserId(String principalName) {
        if (principalName == null) {
            return null;
        }
        try {
            long userId = Long.parseLong(principalName);
            return userId > 0 ? userId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean deny(Long userId, McpDenyReason reason) {
        log.debug("MCP access denied: userId={} reason={}", userId, reason);
        return false;
    }
}
