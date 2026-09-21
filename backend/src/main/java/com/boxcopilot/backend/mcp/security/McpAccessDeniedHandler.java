package com.boxcopilot.backend.mcp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Answers an authenticated request that lacks the required scope with a bare 403 and the bearer challenge. */
class McpAccessDeniedHandler implements AccessDeniedHandler {

    private final String challenge;

    McpAccessDeniedHandler(McpBearerChallenge challenge) {
        this.challenge = challenge.insufficientScope();
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
