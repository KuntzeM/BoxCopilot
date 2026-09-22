package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.mcp.McpEndpoints;
import com.boxcopilot.backend.mcp.McpProperties;
import com.boxcopilot.backend.mcp.McpUrls;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import com.boxcopilot.backend.mcp.authserver.McpSessionUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

/**
 * Resumes an authorization that was interrupted by the login.
 *
 * <p>This handler lives in the application chain, because only that chain has the session. It consumes the
 * {@code mcp_authz} cookie and sends the browser back to the authorize endpoint. The target URL is assembled from
 * the configured backend URL and the decoded query only — no URL supplied by a client is ever used as a redirect
 * target, and a query that is not printable ASCII, or contains a {@code #}, is discarded instead of replayed.
 *
 * <p>Consuming the cookie in a {@code GET} is a side effect, but a harmless one: the replayed request still has
 * to pass the consent page, which is a {@code POST} with a CSRF token.
 */
@Controller
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpPendingAuthorizationController {

    private static final Logger log = LoggerFactory.getLogger(McpPendingAuthorizationController.class);

    private final McpPendingCookies cookies;
    private final McpSessionUserResolver userResolver;
    private final String authorizeUrl;
    private final String frontendUrl;

    McpPendingAuthorizationController(McpPendingCookies cookies, McpSessionUserResolver userResolver,
                                      McpProperties properties, @Value("${frontend.url}") String frontendUrl) {
        this.cookies = cookies;
        this.userResolver = userResolver;
        this.authorizeUrl = properties.backendUrl() + McpAuthorizationServerEndpoints.AUTHORIZE;
        this.frontendUrl = McpUrls.withoutTrailingSlash(frontendUrl) + "/";
    }

    @GetMapping(McpEndpoints.CONTINUE_PENDING_AUTHORIZATION)
    void resume(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (this.userResolver.resolve(authentication).isEmpty()) {
            // The session expired while the user was logging in, or the account no longer exists. The cookie
            // stays: the SPA sends the user through the login again and comes back here.
            redirect(response, this.frontendUrl);
            return;
        }
        Optional<String> cookie = McpPendingCookies.read(request);
        if (cookie.isEmpty()) {
            redirect(response, this.frontendUrl);
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, this.cookies.clear());
        Optional<String> query = McpPendingCookies.decode(cookie.get());
        if (query.isEmpty()) {
            log.debug("MCP authorization not resumed: reason=UNUSABLE_PENDING_QUERY");
            redirect(response, this.frontendUrl);
            return;
        }
        redirect(response, this.authorizeUrl + "?" + query.get());
    }

    private static void redirect(HttpServletResponse response, String location) {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION, location);
    }
}
