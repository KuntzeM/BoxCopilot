package com.boxcopilot.backend.security;

import java.time.Clock;
import java.util.Locale;

/**
 * Throttles failed logins per client IP and per IP+username, so nobody can lock out someone else's account:
 * the counters are keyed by the caller, never by the username alone. Only failures are counted;
 * {@link #isBlocked} takes no permit, so the filter can turn a caller away before any DB or bcrypt work.
 * In-process (one replica); counters reset on restart.
 */
public class LoginThrottle {

    private static final int MAX_USERNAME_LENGTH = 64;

    private final FixedWindowRateLimiter byIp;
    private final FixedWindowRateLimiter byIpAndUser;

    public LoginThrottle(Clock clock, LoginThrottleProperties properties) {
        this.byIp = new FixedWindowRateLimiter(properties.ipLimit(), properties.window(), clock);
        this.byIpAndUser = new FixedWindowRateLimiter(properties.ipUserLimit(), properties.window(), clock);
    }

    /** @param username may be null (e.g. magic-login), then only the IP quota applies */
    public boolean isBlocked(String ip, String username) {
        return byIp.isExhausted(ip) || (username != null && byIpAndUser.isExhausted(userKey(ip, username)));
    }

    public void recordFailure(String ip, String username) {
        byIp.tryAcquire(ip);
        if (username != null) {
            byIpAndUser.tryAcquire(userKey(ip, username));
        }
    }

    /** Conservative: a full window, whatever part of it has already elapsed. */
    public long retryAfterSeconds() {
        return byIp.window().toSeconds();
    }

    private static String userKey(String ip, String username) {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_USERNAME_LENGTH) {
            normalized = normalized.substring(0, MAX_USERNAME_LENGTH);
        }
        return ip + "|" + normalized;
    }
}
