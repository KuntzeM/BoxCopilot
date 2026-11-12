package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.mcp.McpProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * The {@code mcp_authz} cookie, which is the entire state of an interrupted authorization.
 *
 * <p>An anonymous authorize request must not create a session — otherwise a flood of such requests would fill the
 * session store — so the query string of the request travels in a short-lived cookie instead and "latest wins":
 * a new authorize request simply overwrites it.
 *
 * <p>The cookie is {@code HttpOnly} and {@code Secure} in every profile, and its {@code SameSite} attribute
 * follows the session cookie, because it has to be sent on exactly the requests the session cookie is sent on.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpPendingCookies {

    /** Name of the cookie; the SPA never reads it, it only learns about it through {@code /api/v1/me}. */
    public static final String NAME = "mcp_authz";

    /** An authorize query is a few hundred bytes; anything beyond this is not a genuine request. */
    public static final int MAX_VALUE_LENGTH = 2048;

    /** Long enough for a password, magic-link or Nextcloud login, short enough to be forgotten quickly. */
    public static final Duration MAX_AGE = Duration.ofSeconds(600);

    private static final String SESSION_SAME_SITE_PROPERTY = "server.servlet.session.cookie.same-site";
    private static final String DEFAULT_SAME_SITE = "Lax";

    private final String sameSite;

    public McpPendingCookies(McpProperties properties, Environment environment) {
        this.sameSite = resolveSameSite(properties.cookieSameSite(),
                environment.getProperty(SESSION_SAME_SITE_PROPERTY));
    }

    private static String resolveSameSite(String configured, String sessionCookieSameSite) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (sessionCookieSameSite != null && !sessionCookieSameSite.isBlank()) {
            return sessionCookieSameSite.trim();
        }
        return DEFAULT_SAME_SITE;
    }

    /** @return the {@code Set-Cookie} value that stores an encoded authorize query */
    public String store(String encodedQuery) {
        return cookie(encodedQuery, MAX_AGE).toString();
    }

    /** @return the {@code Set-Cookie} value that removes the cookie again, with the very same attributes */
    public String clear() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAge)
                .sameSite(this.sameSite)
                .build();
    }

    public static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public static String encode(String query) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(query.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the decoded authorize query, or empty when the value is not base64url or does not decode to a
     * printable ASCII query. A {@code #} is refused as well: it would turn the rest of the query into a fragment
     * of the redirect target.
     */
    public static Optional<String> decode(String value) {
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String query = new String(decoded, StandardCharsets.UTF_8);
        return isPrintableAsciiQuery(query) ? Optional.of(query) : Optional.empty();
    }

    private static boolean isPrintableAsciiQuery(String query) {
        if (query.isEmpty()) {
            return false;
        }
        for (int i = 0; i < query.length(); i++) {
            char character = query.charAt(i);
            if (character < 0x20 || character > 0x7E || character == '#') {
                return false;
            }
        }
        return true;
    }
}
