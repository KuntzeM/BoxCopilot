package com.boxcopilot.backend.service;

/**
 * Tells a caller outside the MCP feature whether a user may currently use MCP at all.
 *
 * <p>It exists so that {@code /api/v1/me} can report {@code mcpAllowed} without the authentication controller or
 * its DTO ever referring to the {@code mcp} package: only {@code mcp} may depend on {@code mcp}. When the feature
 * is switched off, a no-op implementation answers {@code false}, so the SPA never offers the connection dialog.
 */
public interface McpAllowanceProbe {

    /**
     * @param userId the internal id of the user in question; {@code null} for a caller without a known account
     * @return whether that user may use MCP right now
     */
    boolean isAllowed(Long userId);
}
