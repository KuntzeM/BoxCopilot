package com.boxcopilot.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Failed-login throttling limits.
 *
 * @param ipLimit     failed logins per client IP and window
 * @param ipUserLimit failed logins per client IP and username and window
 * @param window      length of the fixed window
 */
@ConfigurationProperties("app.security.login-throttle")
public record LoginThrottleProperties(
        @DefaultValue("10") int ipLimit,
        @DefaultValue("5") int ipUserLimit,
        @DefaultValue("15m") Duration window) {
}
