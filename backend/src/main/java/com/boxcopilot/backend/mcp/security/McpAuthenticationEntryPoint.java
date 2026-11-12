package com.boxcopilot.backend.mcp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers every unauthenticated MCP request with a bare 401 and the bearer challenge. The challenge is identical
 * whether no token, an unknown token or an expired token was presented, so it reveals nothing about a token.
 */
class McpAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final String challenge;

    McpAuthenticationEntryPoint(McpBearerChallenge challenge) {
        this.challenge = challenge.unauthorized();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
