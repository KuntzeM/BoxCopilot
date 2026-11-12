package com.boxcopilot.backend.mcp;

import java.util.List;

/** A box with its (shortened) description and all items it contains. */
public record McpBoxContents(McpBoxHit box, String description, List<McpItemRef> items) {

    public McpBoxContents {
        items = List.copyOf(items);
    }
}
