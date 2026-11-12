package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.McpAccessDeniedException;
import com.boxcopilot.backend.mcp.McpUserGate;
import com.boxcopilot.backend.mcp.web.McpSecureHtmlResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Replaces the application's session authentication with a minimal one for the duration of an authorize or
 * consent request.
 *
 * <p>The session carries either a {@code CustomUserPrincipal} with the whole JPA user entity, password hash
 * included, or an {@code OAuth2AuthenticationToken} with the Nextcloud claims. Spring Authorization Server
 * serialises whatever it finds into {@code oauth2_authorization.principal}, so neither may end up there. The
 * replacement is a {@code UsernamePasswordAuthenticationToken} over
 * {@code org.springframework.security.core.userdetails.User} whose name is the internal user id and which has no
 * password and no authorities — a shape Spring Security ships Jackson mixins for.
 *
 * <p>The swap lives in the {@code SecurityContextHolder} for this request only and is undone afterwards. Nothing
 * writes it back: since Spring Security 6 the context is saved explicitly, and no filter on this chain saves
 * one, so the SPA session keeps its original authentication.
 *
 * <p>The user is reloaded from the database and checked here as well, so that an authorization can never be
 * started by a user who has meanwhile been disabled, locked or had MCP taken away.
 */
class McpAuthenticationNormalizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpAuthenticationNormalizationFilter.class);

    private static final String DENIED_PAGE = """
            <!DOCTYPE html><html lang="de"><head><meta charset="utf-8"><title>Zugriff verweigert</title></head>
            <body><h1>Zugriff verweigert</h1>
            <p>Dieses Konto darf derzeit keine Claude-Verbindung herstellen.</p></body></html>""";

    private final RequestMatcher requestMatcher;
    private final McpSessionUserResolver userResolver;
    private final McpUserGate userGate;

    McpAuthenticationNormalizationFilter(RequestMatcher requestMatcher, McpSessionUserResolver userResolver,
                                         McpUserGate userGate) {
        this.requestMatcher = requestMatcher;
        this.userResolver = userResolver;
        this.userGate = userGate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!this.requestMatcher.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        SecurityContext sessionContext = SecurityContextHolder.getContext();
        Authentication sessionAuthentication = sessionContext.getAuthentication();
        if (!McpSessionUserResolver.isAuthenticated(sessionAuthentication)) {
            // Anonymous requests must reach the authentication entry point, which starts the login.
            chain.doFilter(request, response);
            return;
        }
        Optional<Long> userId = activeUserId(sessionAuthentication);
        if (userId.isEmpty()) {
            McpSecureHtmlResponse.write(response, HttpStatus.FORBIDDEN.value(), DENIED_PAGE);
            return;
        }
        SecurityContext normalized = SecurityContextHolder.createEmptyContext();
        normalized.setAuthentication(minimalAuthentication(userId.get()));
        SecurityContextHolder.setContext(normalized);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(sessionContext);
        }
    }

    private Optional<Long> activeUserId(Authentication sessionAuthentication) {
        Optional<User> user = this.userResolver.resolve(sessionAuthentication);
        if (user.isEmpty() || user.get().getId() == null) {
            log.debug("MCP authorize denied: reason=UNKNOWN_SESSION_USER");
            return Optional.empty();
        }
        long userId = user.get().getId();
        try {
            this.userGate.requireActiveUser(userId);
        } catch (McpAccessDeniedException e) {
            log.debug("MCP authorize denied: userId={} reason=USER_NOT_ACTIVE", userId);
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    private static Authentication minimalAuthentication(long userId) {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(String.valueOf(userId), "", List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
