package com.boxcopilot.backend.mcp;

/** Generic failure of the MCP read layer; messages never carry file system paths or internal details. */
public class McpReadException extends RuntimeException {

    public McpReadException(String message) {
        super(message);
    }
}
