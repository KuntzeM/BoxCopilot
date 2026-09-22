package com.boxcopilot.backend.mcp.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the few HTML pages the MCP feature serves to a browser, always with the same hardening: the page may
 * load nothing, may only submit to this origin, may not be framed and may not be cached.
 *
 * <p>{@code default-src 'none'} also blocks inline styles, which is why these pages are deliberately unstyled;
 * a nonce would be the alternative and is not worth the machinery for two pages.
 */
public final class McpSecureHtmlResponse {

    /** Matches the specification exactly: no resources, submissions only to this origin, never framed. */
    public static final String CONTENT_SECURITY_POLICY = "default-src 'none'; form-action 'self'; frame-ancestors 'none'";

    private McpSecureHtmlResponse() {
    }

    public static void write(HttpServletResponse response, int status, String html) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(html);
    }

    /** Escapes every character that could leave an HTML text node or an attribute value. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                case '/' -> escaped.append("&#47;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
