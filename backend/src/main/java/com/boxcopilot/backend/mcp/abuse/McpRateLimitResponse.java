package com.boxcopilot.backend.mcp.abuse;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The single answer every exceeded limit gets. It names no limit, no counter and no subject: a caller learns
 * that it was too fast and when to come back, and nothing about how the protection is built.
 */
public final class McpRateLimitResponse {

    /** The whole body; deliberately the same for every limit. */
    public static final String BODY = "{\"error\":\"too_many_requests\"}";

    private McpRateLimitResponse() {
    }

    public static void write(HttpServletResponse response, Duration window) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
