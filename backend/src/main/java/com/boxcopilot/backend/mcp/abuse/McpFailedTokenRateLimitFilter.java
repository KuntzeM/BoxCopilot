package com.boxcopilot.backend.mcp.abuse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Counts, per client address, how often a bearer token presented at {@code /mcp} fails to resolve. An opaque
 * token costs a database query even when it is worthless, so guessing at tokens is cheap for the caller and
 * expensive for the backend.
 *
 * <p>Only failures are counted: a call that authenticates is handled by the per-subject limit instead. The check
 * runs before the resource server's authentication filter, so an address that has already used up its budget is
 * turned away without another query.
 */
public class McpFailedTokenRateLimitFilter extends OncePerRequestFilter {

    private static final String BEARER = "bearer ";

    private final RequestMatcher matcher;
    private final McpRateLimiter limiter;

    public McpFailedTokenRateLimitFilter(RequestMatcher matcher, McpRateLimiter limiter) {
        Assert.notNull(matcher, "matcher cannot be null");
        Assert.notNull(limiter, "limiter cannot be null");
        this.matcher = matcher;
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!matcher.matches(request) || !bearerTokenPresented(request)) {
            chain.doFilter(request, response);
            return;
        }
        String address = McpClientAddress.of(request);
        if (limiter.isExhausted(address)) {
            McpRateLimitResponse.write(response, limiter.window());
            return;
        }
        chain.doFilter(request, response);
        if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            limiter.tryAcquire(address);
        }
    }

    private static boolean bearerTokenPresented(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.toLowerCase(Locale.ROOT).startsWith(BEARER);
    }
}
