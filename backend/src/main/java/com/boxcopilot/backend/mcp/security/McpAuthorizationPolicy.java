package com.boxcopilot.backend.mcp.security;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.time.Duration;

/**
 * Decides whether a stored authorization may still be used. The authorization store depends on this narrow
 * view rather than on the whole policy implementation.
 */
public interface McpAuthorizationPolicy {

    /**
     * How far a connection start stamp may lie in the future before it is treated as wrong rather than as a
     * clock difference. Without an upper bound, a stamp in the future would push the absolute age limit out
     * indefinitely.
     */
    Duration MAX_CLOCK_SKEW = Duration.ofSeconds(60);

    /**
     * @return {@code true} only if every rule passes; implementations are fail-closed, so anything unexpected
     *         yields {@code false}
     */
    boolean checkAuthorization(OAuth2Authorization authorization);
}
