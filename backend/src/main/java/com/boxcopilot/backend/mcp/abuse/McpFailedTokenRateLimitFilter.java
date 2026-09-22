package com.boxcopilot.backend.mcp.abuse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Counts, per client address, how often a bearer token presented to the resource server fails to resolve. An
 * opaque token costs a database query even when it is worthless, so guessing at tokens is cheap for the caller
 * and expensive for the backend.
 *
 * <p>It deliberately matches <em>every</em> request of the chain it is installed in rather than only the MCP
 * endpoint. {@code BearerTokenAuthenticationFilter} authenticates any request in the chain that carries a
 * bearer token, including one aimed at the publicly readable protected-resource metadata, because
 * {@code permitAll} is decided later; a matcher on {@code /mcp} alone would therefore have left a path on
 * which tokens could be guessed for free.
 *
 * <p>The order is attempt, then classify. Whether a token resolves cannot be known without the lookup, so the
 * filter never refuses a request up front: a caller whose token is good is served even from an address that
 * has used up its budget. Only a lookup that actually failed is counted, and only once the address is already
 * over budget is such a failure answered with 429 instead of the resource server's 401. Claude's egress
 * addresses are shared by every Claude user, so refusing up front would have let one caller guessing at
 * tokens lock out everybody else behind the same address.
 */
public class McpFailedTokenRateLimitFilter extends OncePerRequestFilter {

    private static final String BEARER = "bearer ";

    private final McpRateLimiter limiter;
    private final McpRateLimitAudit audit;

    public McpFailedTokenRateLimitFilter(McpRateLimiter limiter, McpRateLimitAudit audit) {
        Assert.notNull(limiter, "limiter cannot be null");
        Assert.notNull(audit, "audit cannot be null");
        this.limiter = limiter;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!bearerTokenPresented(request)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(request, response);
        if (response.getStatus() != HttpStatus.UNAUTHORIZED.value()) {
            return;
        }
        // A false result means the address had already spent its budget before this failure.
        if (limiter.tryAcquire(McpClientAddress.of(request)) || response.isCommitted()) {
            return;
        }
        // The entry point writes status and challenge but no body, so nothing has been sent yet and the
        // rejection can still replace the 401 it had prepared.
        response.reset();
        McpRateLimitResponse.write(response, limiter.window());
        audit.rejected(McpRateLimit.FAILED_TOKEN_LOOKUPS, null, McpClientAddress.of(request));
    }

    private static boolean bearerTokenPresented(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.toLowerCase(Locale.ROOT).startsWith(BEARER);
    }
}
