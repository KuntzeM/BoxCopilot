package com.boxcopilot.backend.mcp;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of boxes and items for MCP tools. Results never contain image tokens, image URLs or box UUIDs;
 * boxes are addressed by their number and all free text is sanitised.
 */
public interface McpReadService {

    /**
     * @param query non-blank, at most 100 characters; matched literally and case-insensitively against item names
     * @param limit 1 to 50
     * @throws IllegalArgumentException if an argument is out of range
     */
    List<McpItemHit> searchItems(String query, int limit);

    /**
     * @param query optional text matched against box number (exact), rooms and description
     * @param room  optional room name (exact, case-insensitive) matched against current and target room
     * @param limit 1 to 50
     * @throws IllegalArgumentException if neither query nor room is given or an argument is out of range
     */
    List<McpBoxHit> searchBoxes(String query, String room, int limit);

    Optional<McpBoxContents> boxContents(int boxNumber);

    /**
     * @return the image bytes, or empty if the item is unknown, has no image or its file is missing
     * @throws McpReadException if the image file exists but cannot be read
     */
    Optional<McpItemImage> itemImage(long itemId, ImageSize size);
}
