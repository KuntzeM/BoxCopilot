package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.web.McpSecureHtmlResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * Demands a valid CSRF token on the consent POST.
 *
 * <p>Spring's {@code CsrfFilter} cannot do it here: {@code OAuth2AuthorizationServerConfigurer} adds every
 * authorization server endpoint to the CSRF ignore list, and that list can only be extended, never reduced. The
 * ignore list is right for the token and revocation endpoints, which are called by a machine with no cookies,
 * but the consent POST is a form submitted by a logged-in browser and is exactly the request a cross-site
 * attacker would try to forge. This filter therefore repeats what {@code CsrfFilter} would have done, using the
 * same {@link CsrfTokenRequestHandler} the chain is configured with, for that one request.
 */
class McpConsentCsrfFilter extends OncePerRequestFilter {

    private static final String REJECTED_PAGE = """
            <!DOCTYPE html><html lang="de"><head><meta charset="utf-8"><title>Abgelaufen</title></head>
            <body><h1>Abgelaufen</h1>
            <p>Die Bestätigung ist nicht mehr gültig. Bitte starten Sie die Verbindung in Claude erneut.</p>
            </body></html>""";

    private final RequestMatcher requestMatcher;
    private final CsrfTokenRequestHandler requestHandler;

    McpConsentCsrfFilter(RequestMatcher requestMatcher, CsrfTokenRequestHandler requestHandler) {
        this.requestMatcher = requestMatcher;
        this.requestHandler = requestHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!this.requestMatcher.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (!isTokenValid(request)) {
            McpSecureHtmlResponse.write(response, HttpStatus.FORBIDDEN.value(), REJECTED_PAGE);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isTokenValid(HttpServletRequest request) {
        Object deferred = request.getAttribute(DeferredCsrfToken.class.getName());
        if (!(deferred instanceof DeferredCsrfToken deferredCsrfToken)) {
            return false;
        }
        CsrfToken expected = deferredCsrfToken.get();
        String actual = this.requestHandler.resolveCsrfTokenValue(request, expected);
        return actual != null && MessageDigest.isEqual(Utf8.encode(expected.getToken()), Utf8.encode(actual));
    }
}
