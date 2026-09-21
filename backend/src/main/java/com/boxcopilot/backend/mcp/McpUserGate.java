package com.boxcopilot.backend.mcp;

/**
 * Fail-closed policy port consulted by every MCP tool call. Implementations decide from fresh state whether the
 * user may currently use MCP; there is deliberately no default implementation.
 */
public interface McpUserGate {

    /**
     * @throws McpAccessDeniedException if the user is unknown, disabled, locked or not allowed to use MCP, or if
     *                                  this cannot be determined
     */
    void requireActiveUser(long userId);
}
