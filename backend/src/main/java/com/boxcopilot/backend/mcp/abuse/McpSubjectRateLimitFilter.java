package com.boxcopilot.backend.mcp.abuse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limits how often one token subject may call {@code /mcp}. It runs behind authentication and authorization, so
 * the subject it counts is an established one and an unauthenticated flood is stopped by the chain rather than
 * by filling this counter with keys.
 *
 * <p>The key is the principal name, which for an MCP access token is the internal user id - the same identity
 * the audit log records.
 */
public class McpSubjectRateLimitFilter extends OncePerRequestFilter {

    private final RequestMatcher matcher;
    private final McpRateLimiter limiter;

    public McpSubjectRateLimitFilter(RequestMatcher matcher, McpRateLimiter limiter) {
        Assert.notNull(matcher, "matcher cannot be null");
        Assert.notNull(limiter, "limiter cannot be null");
        this.matcher = matcher;
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = authenticatedSubject();
        if (subject != null && matcher.matches(request) && !limiter.tryAcquire(subject)) {
            McpRateLimitResponse.write(response, limiter.window());
            return;
        }
        chain.doFilter(request, response);
    }

    private static String authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? null : name;
    }
}
