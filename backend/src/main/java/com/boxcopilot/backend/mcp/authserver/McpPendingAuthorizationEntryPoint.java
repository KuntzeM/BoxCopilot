package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.web.McpPendingCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Sends an anonymous authorize request to the SPA login and remembers the request in the {@code mcp_authz}
 * cookie so that {@code McpPendingAuthorizationController} can replay it afterwards.
 *
 * <p>Nothing is written to the server: no session, no saved request, no map. That is what keeps a flood of
 * anonymous authorize requests from filling the session store, and it is why the chain uses a null request
 * cache.
 */
class McpPendingAuthorizationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(McpPendingAuthorizationEntryPoint.class);

    private final McpPendingCookies cookies;
    private final String loginUrl;

    McpPendingAuthorizationEntryPoint(McpPendingCookies cookies, String frontendUrl) {
        this.cookies = cookies;
        this.loginUrl = frontendUrl + "/";
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) {
        String query = request.getQueryString();
        String encoded = McpPendingCookies.encode(query == null ? "" : query);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (encoded.length() > McpPendingCookies.MAX_VALUE_LENGTH) {
            // Never log the query itself: it carries the client id, which is confidential.
            log.debug("MCP authorize rejected: reason=QUERY_TOO_LONG length={}", encoded.length());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, this.cookies.store(encoded));
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION, this.loginUrl);
    }
}
