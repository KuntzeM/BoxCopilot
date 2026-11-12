package com.boxcopilot.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Turns a caller whose failed-login quota is used up away with 429 before any DB or bcrypt work.
 * Registered before the form-login filter, i.e. after the CSRF filter: a request without a valid CSRF token never
 * reaches the authentication code and costs no bcrypt, so it needs no throttling, while every request that could
 * burn bcrypt time is counted. Failures are fed in by the failure handler and the magic-login controller through
 * {@link #recordFailure}; successful logins neither count nor reset anything.
 *
 * <p>The address is {@code getRemoteAddr()}; that is the real client only if {@code
 * server.tomcat.remoteip.internal-proxies} ({@code TRUSTED_PROXY_PATTERN}) matches the actual proxies, otherwise all
 * clients share one bucket.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleFilter.class);

    private static final String FORM_LOGIN = "/api/auth/login";

    private final LoginThrottle throttle;
    private final RequestMatcher formLogin = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, FORM_LOGIN);
    private final RequestMatcher guarded = new OrRequestMatcher(formLogin,
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/magic-login"));

    public LoginThrottleFilter(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    /** Counts one failed login; logs a single WARN when this failure is the one that starts the block. */
    public static void recordFailure(LoginThrottle throttle, HttpServletRequest request, String username) {
        String ip = ClientAddress.of(request);
        boolean wasBlocked = throttle.isBlocked(ip, username);
        throttle.recordFailure(ip, username);
        if (!wasBlocked && throttle.isBlocked(ip, username)) {
            log.warn("Login throttle engaged for client {} for {}s", ip, throttle.retryAfterSeconds());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !guarded.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = formLogin.matches(request) ? request.getParameter("username") : null;
        if (!throttle.isBlocked(ClientAddress.of(request), username)) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = throttle.retryAfterSeconds();
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"Too many failed login attempts\",\"retryAfterSeconds\":" + retryAfter + "}");
    }
}
