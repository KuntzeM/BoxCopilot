package com.boxcopilot.backend.mcp;

/** Item inside a box listing. */
public record McpItemRef(long itemId, String name, boolean hasImage) {
}
