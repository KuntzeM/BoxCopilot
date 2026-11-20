package com.boxcopilot.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * The browser hardening headers for every response the backend sends - SPA, static files, API, OAuth and MCP.
 * They used to be added by the frontend's nginx; since the backend serves the SPA itself they are set here.
 *
 * <p>The headers are written before the request proceeds, so they are present on early answers (401, 403, 429)
 * too, and a handler that needs a different policy - the MCP consent page sets its own, stricter one - simply
 * replaces it with {@code setHeader}.
 *
 * <p>CSP additions for the lazily loaded PDF renderer (label PDFs): {@code 'wasm-unsafe-eval'} compiles its
 * WebAssembly (no JS eval), {@code data:} in {@code connect-src} lets it fetch the inlined wasm, and
 * {@code worker-src blob:} lets it spawn a blob worker.
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String CONTENT_SECURITY_POLICY = "default-src 'self'; "
            + "script-src 'self' 'wasm-unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob:; "
            + "font-src 'self' data:; "
            + "connect-src 'self' data:; "
            + "worker-src 'self' blob:; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    static final String HSTS = "max-age=31536000; includeSubDomains";

    private final boolean enforceCsp;
    private final boolean hstsEnabled;

    /**
     * @param cspMode     {@code enforce} (any case) sends {@code Content-Security-Policy}; any other value sends the
     *                    same policy as {@code Content-Security-Policy-Report-Only}
     * @param hstsEnabled send {@code Strict-Transport-Security}; only behind TLS
     */
    public SecurityHeadersFilter(String cspMode, boolean hstsEnabled) {
        this.enforceCsp = cspMode != null && "enforce".equals(cspMode.trim().toLowerCase(Locale.ROOT));
        this.hstsEnabled = hstsEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader(enforceCsp ? "Content-Security-Policy" : "Content-Security-Policy-Report-Only",
                CONTENT_SECURITY_POLICY);
        if (hstsEnabled) {
            response.setHeader("Strict-Transport-Security", HSTS);
        }
        chain.doFilter(request, response);
    }

    /** The error page (404/500 JSON) is a separate dispatch and gets the same headers. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
