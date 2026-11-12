package com.boxcopilot.backend.mcp;

import java.util.List;

/** One item found by name, addressed by its box number. */
public record McpItemHit(long itemId, String name, int boxNumber, List<String> rooms, boolean hasImage) {

    public McpItemHit {
        rooms = List.copyOf(rooms);
    }
}
