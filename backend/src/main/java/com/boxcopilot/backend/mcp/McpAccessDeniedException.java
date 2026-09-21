package com.boxcopilot.backend.mcp;

/** The caller is not (or no longer) allowed to use MCP. */
public class McpAccessDeniedException extends RuntimeException {

    public McpAccessDeniedException(String message) {
        super(message);
    }
}
