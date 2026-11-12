package com.boxcopilot.backend.event;

/**
 * Published when a user permanently loses the ability to hold MCP connections: MCP access was
 * withdrawn, the account was disabled or the account was deleted. A temporary lock does not
 * publish this event.
 */
public record McpAccessRevokedEvent(long userId) {
}
