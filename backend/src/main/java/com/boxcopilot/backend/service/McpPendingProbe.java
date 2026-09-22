package com.boxcopilot.backend.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tells a caller outside the MCP feature whether the current request carries an interrupted MCP authorization.
 *
 * <p>It exists so that {@code /api/v1/me} can report {@code mcpPending} without the authentication controller or
 * its DTO ever referring to the {@code mcp} package: only {@code mcp} may depend on {@code mcp}. When the feature
 * is switched off, a no-op implementation answers {@code false}.
 */
public interface McpPendingProbe {

    /** @return whether an MCP authorization is waiting to be resumed for this request */
    boolean isPending(HttpServletRequest request);
}
