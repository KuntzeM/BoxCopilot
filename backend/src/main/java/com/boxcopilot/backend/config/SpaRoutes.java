package com.boxcopilot.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

/**
 * Which paths belong to the single-page application the backend serves, and which never do.
 *
 * <p>The reserved prefixes are server endpoints: their unknown paths must keep answering 401/404 (JSON or a
 * redirect), never {@code index.html}. Everything else that a browser GETs is either a static file of the SPA
 * build or a client-side route.
 */
public final class SpaRoutes {

    /** First path segments that are owned by the server, never by the SPA. */
    static final List<String> RESERVED_SEGMENTS =
            List.of("api", "mcp", "oauth2", "login", "actuator", ".well-known", "error");

    private SpaRoutes() {
    }

    /**
     * GET/HEAD requests outside the reserved prefixes: static files and client-side routes, which are public.
     * The reserved prefixes are matched with path patterns, i.e. on the decoded path exactly as Spring MVC routes
     * it, so an encoded alias such as {@code /%61pi/...} cannot slip past as a "static" request.
     */
    public static RequestMatcher staticAndSpaRequests() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher reserved = new OrRequestMatcher(RESERVED_SEGMENTS.stream()
                .map(segment -> (RequestMatcher) paths.matcher("/" + segment + "/**"))
                .toList());
        RequestMatcher readOnly = new OrRequestMatcher(
                paths.matcher(HttpMethod.GET, "/**"), paths.matcher(HttpMethod.HEAD, "/**"));
        return new AndRequestMatcher(readOnly, new NegatedRequestMatcher(reserved));
    }

    /**
     * Whether a request for a path that matched no file should be answered with {@code index.html}.
     *
     * @param path the path relative to the web root, without leading slash, as the resource handler sees it
     */
    static boolean isClientSideRoute(String path, HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
            return false;
        }
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        int slash = trimmed.indexOf('/');
        String first = slash < 0 ? trimmed : trimmed.substring(0, slash);
        if (RESERVED_SEGMENTS.contains(first)) {
            return false;
        }
        String last = trimmed.endsWith("/") ? "" : trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return !last.contains(".") && acceptsHtml(request);
    }

    /** A navigation sends text/html; a missing header means anything. Only an explicit non-HTML Accept says no. */
    private static boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(type -> type.isCompatibleWith(MediaType.TEXT_HTML));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
