package com.boxcopilot.backend.mcp.abuse;

import com.boxcopilot.backend.security.FixedWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limits how often one client address may hit the matched endpoints, whatever its authentication state. It sits
 * at the front of the authorization server chain, where the flood it protects against - authorize and token
 * requests - would otherwise reach the database and the session lookup.
 *
 * <p>It is an additional check only: a request that passes the limit is handed on unchanged and still has to
 * satisfy every security rule behind it.
 */
public class McpIpRateLimitFilter extends OncePerRequestFilter {

    private final RequestMatcher matcher;
    private final FixedWindowRateLimiter limiter;
    private final McpRateLimitAudit audit;

    public McpIpRateLimitFilter(RequestMatcher matcher, FixedWindowRateLimiter limiter, McpRateLimitAudit audit) {
        Assert.notNull(matcher, "matcher cannot be null");
        Assert.notNull(limiter, "limiter cannot be null");
        Assert.notNull(audit, "audit cannot be null");
        this.matcher = matcher;
        this.limiter = limiter;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientAddress = McpClientAddress.of(request);
        if (matcher.matches(request) && !limiter.tryAcquire(clientAddress)) {
            McpRateLimitResponse.write(response, limiter.window());
            audit.rejected(McpRateLimit.AUTHORIZATION_REQUESTS, null, clientAddress);
            return;
        }
        chain.doFilter(request, response);
    }
}
