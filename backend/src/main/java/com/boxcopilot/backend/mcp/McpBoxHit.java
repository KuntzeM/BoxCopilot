package com.boxcopilot.backend.mcp;

import java.util.List;

/** Summary of one box, addressed by its number. */
public record McpBoxHit(int boxNumber, List<String> rooms, List<String> flags, long itemCount) {

    public McpBoxHit {
        rooms = List.copyOf(rooms);
        flags = List.copyOf(flags);
    }
}
